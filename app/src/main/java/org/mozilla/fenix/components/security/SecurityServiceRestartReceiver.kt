/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.mozilla.fenix.ext.settings

/**
 * Broadcast receiver that ensures security services are restarted after
 * system boot, app updates, or service kills to maintain protection.
 */
class SecurityServiceRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SecurityServiceRestart"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "Device boot completed, checking if security services need restart")
                handleBootCompleted(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.i(TAG, "Package replaced, restarting security services")
                handlePackageReplaced(context)
            }
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.i(TAG, "Quick boot detected, checking security services")
                handleBootCompleted(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present, verifying security services")
                handleUserPresent(context)
            }
        }
    }

    /**
     * Handle device boot completion.
     */
    private fun handleBootCompleted(context: Context) {
        if (context.settings().isAppLockerEnabled) {
            Log.i(TAG, "App Locker enabled, starting security services after boot")
            
            // Small delay to ensure system is ready
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startSecurityServices(context)
            }, 3000) // 3 second delay
        } else {
            Log.d(TAG, "App Locker disabled, not starting security services")
        }
    }

    /**
     * Handle package replacement (app update).
     */
    private fun handlePackageReplaced(context: Context) {
        if (context.settings().isAppLockerEnabled) {
            Log.i(TAG, "App updated and App Locker enabled, restarting security services")
            
            // Immediate restart after package replacement
            startSecurityServices(context)
        }
    }

    /**
     * Handle user presence (screen unlock).
     */
    private fun handleUserPresent(context: Context) {
        if (context.settings().isAppLockerEnabled) {
            // Check if services are still running, restart if needed
            val securityManager = SecurityManager(context)
            if (securityManager.hasAllRequiredPermissions()) {
                Log.d(TAG, "User present, ensuring security services are running")
                startSecurityServices(context)
            }
        }
    }

    /**
     * Start all security services.
     */
    private fun startSecurityServices(context: Context) {
        try {
            val securityManager = SecurityManager(context)
            
            // Only start if we have required permissions
            if (!securityManager.hasAllRequiredPermissions()) {
                Log.w(TAG, "Cannot start security services - missing permissions")
                return
            }
            
            securityManager.startSecurityServices()
            Log.i(TAG, "Security services restarted successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting security services", e)
        }
    }
}
