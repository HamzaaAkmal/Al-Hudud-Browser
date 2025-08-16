/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import org.mozilla.fenix.components.deviceadmin.DeviceAdminReceiver
import org.mozilla.fenix.ext.settings

/**
 * Manages security bypass windows and device admin state validation.
 */
class SecurityBypassManager(private val context: Context) {

    companion object {
        private const val TAG = "SecurityBypassManager"
        private const val BYPASS_WINDOW_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val devicePolicyManager: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val deviceAdminComponent: ComponentName by lazy {
        ComponentName(context, DeviceAdminReceiver::class.java)
    }

    /**
     * Record a successful challenge completion and start bypass window.
     */
    fun recordChallengeCompletion() {
        val currentTime = System.currentTimeMillis()
        context.settings().lastChallengeCompletionTime = currentTime
        Log.d(TAG, "Challenge completion recorded, bypass window started until ${currentTime + BYPASS_WINDOW_DURATION_MS}")
    }

    /**
     * Check if currently within the bypass window.
     */
    fun isWithinBypassWindow(): Boolean {
        val lastCompletionTime = context.settings().lastChallengeCompletionTime
        if (lastCompletionTime == 0L) return false
        
        val currentTime = System.currentTimeMillis()
        val timeSinceCompletion = currentTime - lastCompletionTime
        val isWithinWindow = timeSinceCompletion <= BYPASS_WINDOW_DURATION_MS
        
        if (!isWithinWindow && lastCompletionTime > 0) {
            // Clear expired bypass window
            context.settings().lastChallengeCompletionTime = 0L
            Log.d(TAG, "Bypass window expired, clearing timestamp")
        }
        
        return isWithinWindow
    }

    /**
     * Clear the bypass window manually.
     */
    fun clearBypassWindow() {
        context.settings().lastChallengeCompletionTime = 0L
        Log.d(TAG, "Bypass window manually cleared")
    }

    /**
     * Check if device admin is actually enabled (not just requested).
     */
    fun isDeviceAdminActuallyEnabled(): Boolean {
        return try {
            val isActiveAdmin = devicePolicyManager.isAdminActive(deviceAdminComponent)
            val settingsValue = context.settings().isDeviceAdminActuallyEnabled
            
            // Update settings if there's a mismatch
            if (isActiveAdmin != settingsValue) {
                context.settings().isDeviceAdminActuallyEnabled = isActiveAdmin
                Log.d(TAG, "Device admin state updated: $isActiveAdmin")
            }
            
            isActiveAdmin
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device admin state", e)
            false
        }
    }

    /**
     * Update device admin state when activation is confirmed.
     */
    fun updateDeviceAdminState(enabled: Boolean) {
        context.settings().isDeviceAdminActuallyEnabled = enabled
        Log.i(TAG, "Device admin state manually updated: $enabled")
    }

    /**
     * Check if protection should be triggered based on current state.
     */
    fun shouldTriggerProtection(): Boolean {
        // Don't trigger if within bypass window
        if (isWithinBypassWindow()) {
            Log.d(TAG, "Within bypass window, protection not triggered")
            return false
        }
        
        // Don't trigger if app locker is not enabled
        if (!context.settings().isAppLockerEnabled) {
            Log.d(TAG, "App locker not enabled, protection not triggered")
            return false
        }
        
        return true
    }

    /**
     * Check if device admin protection should be triggered.
     * Only triggers if device admin is actually enabled (not just during activation).
     */
    fun shouldTriggerDeviceAdminProtection(): Boolean {
        // Don't trigger normal protection checks if device admin isn't actually enabled yet
        if (!isDeviceAdminActuallyEnabled()) {
            Log.d(TAG, "Device admin not actually enabled yet, protection not triggered")
            return false
        }
        
        return shouldTriggerProtection()
    }

    /**
     * Get remaining bypass window time in milliseconds.
     */
    fun getRemainingBypassTime(): Long {
        if (!isWithinBypassWindow()) return 0L
        
        val lastCompletionTime = context.settings().lastChallengeCompletionTime
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastCompletionTime
        
        return maxOf(0L, BYPASS_WINDOW_DURATION_MS - elapsed)
    }
}
