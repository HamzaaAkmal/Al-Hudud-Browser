/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.security

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.settings

/**
 * Test activity for verifying security protection functionality.
 * This activity helps developers and testers verify the security overlay system.
 */
class SecurityProtectionTestActivity : AppCompatActivity() {

    private lateinit var securityManager: SecurityManager
    private lateinit var overlayManager: SecurityOverlayManager
    
    private lateinit var statusText: TextView
    private lateinit var testAccessibilityButton: Button
    private lateinit var testDeviceAdminButton: Button
    private lateinit var testAppInfoButton: Button
    private lateinit var testOverlayPermissionButton: Button
    private lateinit var checkPermissionsButton: Button

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, SecurityProtectionTestActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_protection_test)
        
        initializeComponents()
        setupViews()
        updateStatus()
    }

    private fun initializeComponents() {
        securityManager = SecurityManager(this)
        overlayManager = SecurityOverlayManager(this)
    }

    private fun setupViews() {
        statusText = findViewById(R.id.status_text)
        testAccessibilityButton = findViewById(R.id.test_accessibility_button)
        testDeviceAdminButton = findViewById(R.id.test_device_admin_button)
        testAppInfoButton = findViewById(R.id.test_app_info_button)
        testOverlayPermissionButton = findViewById(R.id.test_overlay_permission_button)
        checkPermissionsButton = findViewById(R.id.check_permissions_button)

        testAccessibilityButton.setOnClickListener {
            overlayManager.showAccessibilityProtectionOverlay()
        }

        testDeviceAdminButton.setOnClickListener {
            overlayManager.showDeviceAdminProtectionOverlay()
        }

        testAppInfoButton.setOnClickListener {
            overlayManager.showAppInfoProtectionOverlay()
        }

        testOverlayPermissionButton.setOnClickListener {
            overlayManager.showOverlayPermissionProtectionOverlay()
        }

        checkPermissionsButton.setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private fun updateStatus() {
        val status = buildString {
            appendLine("Security Protection Status:")
            appendLine("App Locker Enabled: ${settings().isAppLockerEnabled}")
            appendLine("Overlay Permission: ${securityManager.hasOverlayPermission()}")
            appendLine("Usage Stats Permission: ${securityManager.hasUsageStatsPermission()}")
            appendLine("All Permissions: ${securityManager.hasAllRequiredPermissions()}")
            appendLine("Protection Active: ${securityManager.isSecurityProtectionActive()}")
            appendLine()
            appendLine("Status: ${securityManager.getSecurityProtectionStatus()}")
            
            val missing = securityManager.getMissingPermissions()
            if (missing.isNotEmpty()) {
                appendLine()
                appendLine("Missing Permissions:")
                missing.forEach { appendLine("• $it") }
            }
        }
        
        statusText.text = status
    }

    private fun checkAndRequestPermissions() {
        if (!securityManager.hasAllRequiredPermissions()) {
            securityManager.showPermissionRequestDialog()
        }
        
        // Update status after a delay to show changes
        statusText.postDelayed({ updateStatus() }, 500)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager.destroy()
    }
}
