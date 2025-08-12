/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import org.mozilla.fenix.ext.settings

/**
 * Accessibility service for monitoring app launches and enforcing App Locker protection.
 * This service monitors when protected apps are launched and shows the lock screen.
 */
class AppLockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppLockAccessibilityService"
        const val ACTION_UNLOCK_SUCCESS = "org.mozilla.fenix.applocker.UNLOCK_SUCCESS"
    }

    private val unlockSessionManager = UnlockSessionManager()
    private var lastUnlockedApp: String? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                unlockSessionManager.clearAllSessions()
                lastUnlockedApp = null
            }
        }
    }

    private val unlockBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_UNLOCK_SUCCESS) {
                val packageName = intent.getStringExtra("package_name") ?: return
                unlockSessionManager.unlockApp(packageName)
                lastUnlockedApp = packageName
            }
        }
    }

    override fun onServiceConnected() {
        Log.d(TAG, "App Lock Accessibility service connected")

        // Register receivers
        val screenOffFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, screenOffFilter)

        val unlockSuccessFilter = IntentFilter(ACTION_UNLOCK_SUCCESS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockBroadcastReceiver, unlockSuccessFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unlockBroadcastReceiver, unlockSuccessFilter)
        }

        // Configure service info
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageNames = null // Listen to all packages
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        this.serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleAppLaunch(packageName)
            }
        }
    }

    private fun handleAppLaunch(packageName: String) {
        // Skip if app is temporarily unlocked
        if (unlockSessionManager.isTemporarilyUnlocked(packageName)) {
            unlockSessionManager.markAppInForeground(packageName)
            return
        }

        // Skip if this is our own app or system apps we shouldn't lock
        if (shouldSkipApp(packageName)) {
            return
        }

        // Check if app is protected
        val protectedApps = applicationContext.settings().appLockerProtectedApps
        if (protectedApps.contains(packageName) && applicationContext.settings().isAppLockerEnabled) {
            // Check if we switched from another app
            if (packageName != lastUnlockedApp) {
                showLockScreen(packageName)
            }
        }
    }

    private fun shouldSkipApp(packageName: String): Boolean {
        return packageName == applicationContext.packageName || // Our own app
               packageName.startsWith("com.android.") || // System apps
               packageName.startsWith("android") ||
               packageName == "com.android.systemui"
    }

    private fun showLockScreen(packageName: String) {
        val intent = Intent(this, AppLockScreenActivity::class.java).apply {
            putExtra("target_package", packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.d(TAG, "App Lock Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
            unregisterReceiver(unlockBroadcastReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receivers", e)
        }
    }
}
