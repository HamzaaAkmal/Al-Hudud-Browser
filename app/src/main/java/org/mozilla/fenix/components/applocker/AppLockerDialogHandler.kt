/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.settings

/**
 * Handler for App Locker dialog interactions and configuration.
 * Manages the user interaction flow for setting up and configuring App Locker.
 */
class AppLockerDialogHandler(private val fragment: Fragment) {

    private val context: Context get() = fragment.requireContext()
    private val appLockerManager = AppLockerManager(context)
    
    // Activity result launcher for Islamic Text Challenge
    private val islamicChallengeResultLauncher: ActivityResultLauncher<Intent> = 
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleIslamicChallengeResult(result)
        }

    /**
     * Handle App Locker preference click.
     * Shows appropriate dialog based on current configuration state.
     */
    fun handleAppLockerClick() {
        when {
            !appLockerManager.isAccessibilityServiceEnabled() -> showPermissionDialog()
            !context.settings().isAppLockerEnabled -> showSetupDialog()
            else -> showConfigurationDialog()
        }
    }

    /**
     * Show permission dialog to enable accessibility service.
     */
    private fun showPermissionDialog() {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.app_locker_permission_required))
            .setMessage(context.getString(R.string.app_locker_permission_message))
            .setPositiveButton(context.getString(R.string.app_locker_permission_grant)) { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton(context.getString(R.string.app_locker_permission_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Show setup dialog for first-time configuration.
     */
    private fun showSetupDialog() {
        if (context.settings().appLockerMasterPin.isEmpty()) {
            showPinSetupDialog()
        } else {
            showAppSelectionDialog()
        }
    }

    /**
     * Show PIN setup dialog.
     */
    private fun showPinSetupDialog() {
        val pinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter 4-digit PIN"
        }

        AlertDialog.Builder(context)
            .setTitle("Set up App Locker PIN")
            .setMessage("Choose a 4-digit PIN to protect your apps")
            .setView(pinInput)
            .setPositiveButton("Set PIN") { _, _ ->
                val pin = pinInput.text.toString()
                if (isValidPin(pin)) {
                    saveMasterPin(pin)
                    // After setting PIN, go to app selection
                    val intent = Intent(context, AppSelectionActivity::class.java)
                    fragment.startActivity(intent)
                } else {
                    showInvalidPinError()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Show configuration dialog for existing setup.
     */
    private fun showConfigurationDialog() {
        val options = arrayOf(
            "Manage Protected Apps",
            "Change PIN",
            "Test Islamic Text Challenge",
            "Disable App Locker"
        )

        AlertDialog.Builder(context)
            .setTitle("App Locker Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAppSelectionDialog()
                    1 -> showChangePinDialog()
                    2 -> showTestChallengeDialog()
                    3 -> showDisableDialog()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Show app selection dialog.
     */
    private fun showAppSelectionDialog() {
        // Check if PIN is set up first
        if (context.settings().appLockerMasterPin.isEmpty()) {
            showPinSetupDialog()
            return
        }
        
        // Launch the new modern app selection activity
        val intent = Intent(context, AppSelectionActivity::class.java)
        fragment.startActivity(intent)
    }

    /**
     * Show change PIN dialog.
     */
    private fun showChangePinDialog() {
        val pinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter new 4-digit PIN"
        }

        AlertDialog.Builder(context)
            .setTitle("Change App Locker PIN")
            .setView(pinInput)
            .setPositiveButton("Change PIN") { _, _ ->
                val pin = pinInput.text.toString()
                if (isValidPin(pin)) {
                    saveMasterPin(pin)
                    Toast.makeText(context, "PIN changed successfully", Toast.LENGTH_SHORT).show()
                } else {
                    showInvalidPinError()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Show disable confirmation dialog.
     */
    private fun showDisableDialog() {
        AlertDialog.Builder(context)
            .setTitle("Disable App Locker")
            .setMessage("To disable App Locker protection, you must complete an Islamic text typing challenge. This ensures authorized access only.")
            .setPositiveButton("Continue") { _, _ ->
                startIslamicTextChallenge()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Start Islamic Text Challenge for App Locker disable
     */
    private fun startIslamicTextChallenge() {
        val intent = IslamicTextChallengeActivity.createIntent(
            context, 
            IslamicTextChallengeActivity.TYPE_APP_LOCKER_DISABLE
        )
        islamicChallengeResultLauncher.launch(intent)
    }

    /**
     * Handle result from Islamic Text Challenge
     */
    private fun handleIslamicChallengeResult(result: ActivityResult) {
        when (result.resultCode) {
            IslamicTextChallengeActivity.RESULT_CHALLENGE_SUCCESS -> {
                // Challenge passed - proceed with disabling App Locker
                disableAppLocker()
                Toast.makeText(context, "Challenge completed successfully. App Locker disabled.", Toast.LENGTH_LONG).show()
            }
            IslamicTextChallengeActivity.RESULT_CHALLENGE_FAILED -> {
                // Challenge failed - show message and do nothing
                Toast.makeText(context, "Challenge failed. App Locker remains enabled.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Show test challenge dialog
     */
    private fun showTestChallengeDialog() {
        try {
            val intent = appLockerManager.getIslamicTextChallengeTestIntent()
            context.startActivity(intent)
        } catch (e: Exception) {
            showErrorDialog("Cannot open test activity")
        }
    }

    /**
     * Open accessibility settings.
     */
    private fun openAccessibilitySettings() {
        try {
            val intent = appLockerManager.getAccessibilitySettingsIntent()
            context.startActivity(intent)
        } catch (e: Exception) {
            showErrorDialog("Cannot open accessibility settings")
        }
    }

    /**
     * Show error dialog.
     */
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(context)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Show invalid PIN error.
     */
    private fun showInvalidPinError() {
        Toast.makeText(context, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show()
    }

    /**
     * Validate PIN format.
     */
    private fun isValidPin(pin: String): Boolean {
        return pin.length == 4 && pin.all { it.isDigit() }
    }

    /**
     * Save master PIN securely.
     */
    private fun saveMasterPin(pin: String) {
        val hashedPin = PasswordHasher.hashPin(pin)
        context.settings().appLockerMasterPin = hashedPin
    }

    /**
     * Save protected apps list.
     */
    private fun saveProtectedApps(apps: Set<String>) {
        context.settings().appLockerProtectedApps = apps
    }

    /**
     * Enable App Locker.
     */
    private fun enableAppLocker() {
        context.settings().isAppLockerEnabled = true
        Toast.makeText(context, "App Locker enabled", Toast.LENGTH_SHORT).show()
    }

    /**
     * Disable App Locker.
     */
    private fun disableAppLocker() {
        context.settings().isAppLockerEnabled = false
        context.settings().appLockerProtectedApps = emptySet()
        Toast.makeText(context, "App Locker disabled", Toast.LENGTH_SHORT).show()
    }

    /**
     * Get status summary for settings preference.
     */
    fun getStatusSummary(): String {
        return appLockerManager.getSummaryText()
    }
}
