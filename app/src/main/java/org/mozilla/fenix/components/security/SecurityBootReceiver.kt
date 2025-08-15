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
 * Broadcast receiver that automatically restarts security services after device boot.
 * Ensures continuous protection even after device restart.
 */
class SecurityBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SecurityBootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(TAG, "Boot/replacement detected: ${intent.action}")
                handleBootOrReplacement(context)
            }
        }
    }

    /**
     * Handle device boot or app replacement.
     */
    private fun handleBootOrReplacement(context: Context) {
        try {
            // Check if App Locker is enabled
            if (!context.settings().isAppLockerEnabled) {
                Log.d(TAG, "App Locker disabled, not starting security services")
                return
            }
            
            Log.i(TAG, "Starting comprehensive security services after boot/replacement")
            
            // Initialize security manager and start all services
            val securityManager = SecurityManager(context)
            securityManager.startSecurityServices()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting security services after boot", e)
        }
    }
}
