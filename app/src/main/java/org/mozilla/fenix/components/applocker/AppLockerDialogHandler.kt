/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.util.Log
import android.widget.EditText
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
 * Handler for App Locker dialog interactions and configuration.
 * Manages the user interaction flow for setting up and configuring App Locker.
 */
class AppLockerDialogHandler(private val fragment: Fragment) {

    private val context: Context get() = fragment.requireContext()
    private val appLockerManager = AppLockerManager(context)
    private val securityManager = SecurityManager(context)
    
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
            !areAllPermissionsGranted() -> showPermissionDialog()
            !context.settings().isAppLockerEnabled -> showSetupDialog()
            else -> showConfigurationDialog()
        }
    }

    /**
     * Check if all required permissions are granted for App Locker.
     */
    private fun areAllPermissionsGranted(): Boolean {
        // Check accessibility service
        if (!appLockerManager.isAccessibilityServiceEnabled()) {
            Log.d("AppLockerDialogHandler", "Accessibility service not enabled")
            return false
        }
        
        // Check overlay permission
        if (!securityManager.hasOverlayPermission()) {
            Log.d("AppLockerDialogHandler", "Overlay permission not granted")
            return false
        }
        
        // Check usage stats permission
        if (!securityManager.hasUsageStatsPermission()) {
            Log.d("AppLockerDialogHandler", "Usage stats permission not granted")
            return false
        }
        
        Log.d("AppLockerDialogHandler", "All required permissions granted")
        return true
    }

    /**
     * Show permission dialog to ensure all required permissions are granted.
     */
    private fun showPermissionDialog() {
        val missingPermissions = mutableListOf<String>()
        
        if (!appLockerManager.isAccessibilityServiceEnabled()) {
            missingPermissions.add("Accessibility Service (for monitoring app usage)")
        }
        
        if (!securityManager.hasOverlayPermission()) {
            missingPermissions.add("Display Over Other Apps (for security overlays)")
        }
        
        if (!securityManager.hasUsageStatsPermission()) {
            missingPermissions.add("Usage Access (for app protection)")
        }
        
        val permissionMessage = """
            App Locker requires the following permissions to function properly:
            
            ${missingPermissions.joinToString("\n• ", "• ")}
            
            Please grant all permissions to continue with App Locker setup.
            You will be redirected back once all permissions are granted.
        """.trimIndent()
        
        AlertDialog.Builder(context)
            .setTitle("Required Permissions")
            .setMessage(permissionMessage)
            .setPositiveButton("Grant Permissions") { _, _ ->
                requestNextMissingPermission()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(context, "App Locker cannot be enabled without required permissions", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Request permissions sequentially, ensuring each is granted before proceeding.
     */
    private fun requestNextMissingPermission() {
        when {
            !appLockerManager.isAccessibilityServiceEnabled() -> {
                Log.d("AppLockerDialogHandler", "Requesting accessibility service permission")
                openAccessibilitySettings()
            }
            !securityManager.hasOverlayPermission() -> {
                Log.d("AppLockerDialogHandler", "Requesting overlay permission")
                securityManager.requestOverlayPermission()
            }
            !securityManager.hasUsageStatsPermission() -> {
                Log.d("AppLockerDialogHandler", "Requesting usage stats permission")
                securityManager.requestUsageStatsPermission()
            }
            else -> {
                Log.d("AppLockerDialogHandler", "All permissions granted, proceeding to setup")
                Toast.makeText(context, "All permissions granted! Setting up App Locker...", Toast.LENGTH_SHORT).show()
                showSetupDialog()
            }
        }
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
            "Keyword Protection",
            "Test Islamic Text Challenge",
            "Disable App Locker"
        )

        AlertDialog.Builder(context)
            .setTitle("App Locker Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAppSelectionDialog()
                    1 -> showChangePinDialog()
                    2 -> showKeywordProtectionDialog()
                    3 -> showTestChallengeDialog()
                    4 -> showDisableDialog()
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
     * Show keyword protection settings dialog.
     */
    private fun showKeywordProtectionDialog() {
        val isEnabled = context.settings().isKeywordProtectionEnabled
        
        val message = """
            Keyword Protection monitors text input across apps (Facebook, Instagram, YouTube, Chrome, etc.) to block inappropriate content for spiritual safety.
            
            When enabled, searches containing harmful keywords will be blocked with a protective overlay.
            
            Current status: ${if (isEnabled) "ENABLED" else "DISABLED"}
            
            Note: Requires Keyword Protection Accessibility Service to be enabled in Android Settings.
        """.trimIndent()
        
        AlertDialog.Builder(context)
            .setTitle("Keyword Protection Settings")
            .setMessage(message)
            .setPositiveButton(if (isEnabled) "Disable" else "Enable") { _, _ ->
                toggleKeywordProtection()
            }
            .setNeutralButton("Manage Keywords") { _, _ ->
                showKeywordManagementDialog()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Toggle keyword protection on/off.
     */
    private fun toggleKeywordProtection() {
        val wasEnabled = context.settings().isKeywordProtectionEnabled
        context.settings().isKeywordProtectionEnabled = !wasEnabled
        
        val newStatus = if (!wasEnabled) "enabled" else "disabled"
        Toast.makeText(context, "Keyword Protection $newStatus", Toast.LENGTH_SHORT).show()
        
        if (!wasEnabled) {
            // Use SecurityManager to guide user to enable accessibility service
            securityManager.enableKeywordProtection()
        }
    }

    /**
     * Show setup instructions for keyword protection.
     */
    private fun showKeywordProtectionSetupDialog() {
        AlertDialog.Builder(context)
            .setTitle("Enable Keyword Protection Service")
            .setMessage("""
                To activate Keyword Protection, you need to enable the "Keyword Protection" accessibility service:
                
                1. Open Android Settings
                2. Go to Accessibility
                3. Find "Keyword Protection" service
                4. Turn it ON
                
                This service only monitors for harmful keywords and does not access personal data.
            """.trimIndent())
            .setPositiveButton("Open Settings") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Show keyword management dialog.
     */
    private fun showKeywordManagementDialog() {
        val currentKeywords = context.settings().bannedKeywords.toMutableSet()
        val keywordsList = currentKeywords.joinToString(", ")
        
        AlertDialog.Builder(context)
            .setTitle("Manage Banned Keywords")
            .setMessage("""
                Current banned keywords:
                $keywordsList
                
                These keywords will trigger protection when detected in any monitored app.
            """.trimIndent())
            .setPositiveButton("Reset to Default") { _, _ ->
                context.settings().bannedKeywords = setOf("porn", "anal", "sex", "xxx", "nude", "naked", "adult", "explicit")
                Toast.makeText(context, "Keywords reset to default list", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close") { dialog, _ ->
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
        
        // Start security protection services
        securityManager.enableSecurityProtection()
        
        Toast.makeText(context, "App Locker enabled", Toast.LENGTH_SHORT).show()
    }

    /**
     * Disable App Locker.
     */
    private fun disableAppLocker() {
        context.settings().isAppLockerEnabled = false
        context.settings().appLockerProtectedApps = emptySet()
        
        // Stop security protection services
        securityManager.disableSecurityProtection()
        
        Toast.makeText(context, "App Locker disabled", Toast.LENGTH_SHORT).show()
    }

    /**
     * Get status summary for settings preference.
     */
    fun getStatusSummary(): String {
        return appLockerManager.getSummaryText()
    }
}
