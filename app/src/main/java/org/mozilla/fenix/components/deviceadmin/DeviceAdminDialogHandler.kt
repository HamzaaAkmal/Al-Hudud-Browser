/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.deviceadmin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import org.mozilla.fenix.R
import org.mozilla.fenix.components.security.SecurityManager
import org.mozilla.fenix.ext.settings

/**
 * Handler for device admin protection dialog and settings navigation.
 * Manages the user interaction flow for enabling/disabling device admin protection.
 */
class DeviceAdminDialogHandler(private val fragment: Fragment) {

    private val context: Context get() = fragment.requireContext()
    private val deviceAdminManager = DeviceAdminManager(context)
    private val securityManager = SecurityManager(context)

    /**
     * Handle device admin protection menu click.
     * Shows confirmation dialog if protection is enabled,
     * or directly opens settings if protection is disabled.
     */
    fun handleDeviceAdminProtectionClick() {
        if (deviceAdminManager.isDeviceAdminEnabled()) {
            showDisableConfirmationDialog()
        } else {
            openDeviceAdminSettings()
        }
    }

    /**
     * Show confirmation dialog when user wants to disable protection.
     */
    private fun showDisableConfirmationDialog() {
        // Check if App Locker is enabled - if so, redirect to browser settings
        if (context.settings().isAppLockerEnabled) {
            // Check if we're within bypass window from recent App Locker challenge
            if (securityManager.isWithinBypassWindow()) {
                val remainingMinutes = securityManager.getRemainingBypassTime() / (60 * 1000)
                
                AlertDialog.Builder(context)
                    .setTitle("Bypass Window Active")
                    .setMessage("You have ${remainingMinutes} minutes remaining from your recent App Locker challenge.\n\nYou can now disable Device Admin protection directly.")
                    .setPositiveButton("Disable Device Admin") { _, _ ->
                        openDeviceAdminSettingsForDisable()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } else {
                AlertDialog.Builder(context)
                    .setTitle("Security Protection Active")
                    .setMessage("Device Admin protection cannot be disabled while App Locker is active.\n\nTo disable Device Admin protection:\n1. Open the browser\n2. Go to Privacy & Security\n3. Complete the App Locker challenge to disable it\n4. Then return here within 5 minutes to disable Device Admin")
                    .setPositiveButton("Open Browser Settings") { _, _ ->
                        openBrowserSecuritySettings()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        } else {
            // App Locker is disabled, allow direct device admin disable
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.device_admin_protection_dialog_title))
                .setMessage("Device Admin protection will be disabled. This will allow the browser to be uninstalled without extra confirmation.")
                .setPositiveButton("Disable Protection") { _, _ ->
                    openDeviceAdminSettingsForDisable()
                }
                .setNegativeButton(context.getString(R.string.device_admin_protection_dialog_cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    /**
     * Open browser security settings.
     */
    private fun openBrowserSecuritySettings() {
        try {
            // Launch browser with security settings
            val packageName = context.packageName
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Add extra to navigate directly to security settings if supported
                launchIntent.putExtra("navigate_to", "security_settings")
                context.startActivity(launchIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Please open the browser manually and go to Privacy & Security settings", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Open device admin settings using the appropriate intent.
     */
    private fun openDeviceAdminSettings() {
        try {
            val intent = if (deviceAdminManager.isDeviceAdminEnabled()) {
                deviceAdminManager.getDisableDeviceAdminIntent()
            } else {
                deviceAdminManager.getEnableDeviceAdminIntent()
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            showErrorDialog()
        }
    }

    /**
     * Open device admin settings for disable.
     */
    private fun openDeviceAdminSettingsForDisable() {
        try {
            val intent = deviceAdminManager.getDisableDeviceAdminIntent()
            context.startActivity(intent)
            Toast.makeText(context, "Opening Device Admin settings for disable.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showErrorDialog()
        }
    }

    /**
     * Get status summary for settings preference.
     */
    fun getStatusSummary(context: Context): String {
        return deviceAdminManager.getStatusText()
    }

    /**
     * Show error dialog when settings cannot be opened.
     */
    private fun showErrorDialog() {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.device_admin_protection_error_title))
            .setMessage(context.getString(R.string.device_admin_protection_error_message))
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Handle the result from device admin settings activity.
     */
    fun handleActivityResult(result: ActivityResult) {
        // The result code doesn't matter much since we just need to update the UI
        // Device admin status will be checked when menu is shown again
        val status = if (deviceAdminManager.isDeviceAdminEnabled()) {
            "Device admin protection enabled"
        } else {
            "Device admin protection disabled"
        }
        
        Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
    }
}
