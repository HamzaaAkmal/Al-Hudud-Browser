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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import org.mozilla.fenix.R

/**
 * Handler for device admin protection dialog and settings navigation.
 * Manages the user interaction flow for enabling/disabling device admin protection.
 */
class DeviceAdminDialogHandler(private val fragment: Fragment) {

    private val context: Context get() = fragment.requireContext()
    private val deviceAdminManager = DeviceAdminManager(context)

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
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.device_admin_protection_dialog_title))
            .setMessage(context.getString(R.string.device_admin_protection_dialog_message))
            .setPositiveButton(context.getString(R.string.device_admin_protection_dialog_disable)) { _, _ ->
                openDeviceAdminSettings()
            }
            .setNegativeButton(context.getString(R.string.device_admin_protection_dialog_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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
