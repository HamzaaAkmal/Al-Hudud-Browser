/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.deviceadmin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.mozilla.fenix.ext.settings

/**
 * Helper class to manage device admin operations for uninstall protection.
 * This class only handles the minimal device admin functionality required
 * to prevent easy uninstall of the browser.
 */
class DeviceAdminManager(private val context: Context) {

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val deviceAdminComponent = ComponentName(context, DeviceAdminReceiver::class.java)

    /**
     * Check if device admin protection is currently enabled.
     */
    fun isDeviceAdminEnabled(): Boolean {
        return devicePolicyManager.isAdminActive(deviceAdminComponent)
    }

    /**
     * Get an intent to enable device admin protection.
     * This will open Android's device admin settings page.
     */
    fun getEnableDeviceAdminIntent(): Intent {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent)
        intent.putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Enable device admin protection to prevent accidental uninstall of the browser. " +
                "This will require an extra confirmation step to uninstall the app."
        )
        return intent
    }

    /**
     * Get an intent to disable device admin protection.
     * This will open Android's device admin settings page.
     */
    fun getDisableDeviceAdminIntent(): Intent {
        val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
        return intent
    }

    /**
     * Get the current status text for the menu item.
     */
    fun getStatusText(): String {
        val isEnabled = isDeviceAdminEnabled()
        val isProtected = context.settings().isAppLockerEnabled
        
        return when {
            isEnabled && isProtected -> "Protection ON (Secured)"
            isEnabled && !isProtected -> "Protection ON"
            else -> "Protection OFF"
        }
    }

    /**
     * Get the menu item label with status.
     */
    fun getMenuItemLabel(): String {
        val status = getStatusText()
        return "Device Admin Protection - $status"
    }

    /**
     * Check if device admin settings can be modified.
     * Returns false if App Locker protection is active.
     */
    fun canModifyDeviceAdminSettings(): Boolean {
        return !context.settings().isAppLockerEnabled
    }

    /**
     * Get explanation for why device admin cannot be modified.
     */
    fun getProtectionExplanation(): String {
        return "Device Admin protection is currently secured by App Locker. " +
               "To modify Device Admin settings, first disable App Locker protection " +
               "from the browser's Privacy & Security settings."
    }
}
