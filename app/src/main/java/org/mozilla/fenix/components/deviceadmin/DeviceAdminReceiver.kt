/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.deviceadmin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.mozilla.fenix.components.security.SecurityManager
import org.mozilla.fenix.ext.settings

/**
 * Device admin receiver for preventing uninstall of the browser.
 * This receiver only handles basic device admin events and does not
 * request any additional permissions beyond uninstall prevention.
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeviceAdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin protection enabled")
        
        // Start security services if App Locker is enabled
        if (context.settings().isAppLockerEnabled) {
            try {
                val securityManager = SecurityManager(context)
                securityManager.startSecurityServices()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting security services", e)
            }
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device admin protection disabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        Log.w(TAG, "Device admin disable requested")
        
        // If App Locker is enabled, show enhanced warning
        return if (context.settings().isAppLockerEnabled) {
            "This browser is protected by App Locker security system. " +
            "Disabling device admin protection will reduce security. " +
            "Consider disabling App Locker first from the browser settings."
        } else {
            "Disabling device admin protection will allow the browser to be uninstalled without extra confirmation."
        }
    }
}
