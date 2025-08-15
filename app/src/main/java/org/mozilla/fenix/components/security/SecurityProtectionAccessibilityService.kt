/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.security

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.mozilla.fenix.ext.settings

/**
 * Enhanced accessibility service that protects itself from being disabled
 * through Android system settings while App Locker is active.
 */
class SecurityProtectionAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SecurityProtectionAS"
        private const val SETTINGS_PACKAGE = "com.android.settings"
    }

    private lateinit var securityOverlayManager: SecurityOverlayManager
    private var browserServiceComponent: ComponentName? = null
    private var isMonitoringSettings = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Security protection accessibility service connected")
        
        securityOverlayManager = SecurityOverlayManager(this)
        
        // Get browser's accessibility service component name
        browserServiceComponent = ComponentName(
            applicationContext.packageName,
            "org.mozilla.fenix.components.applocker.AppLockAccessibilityService"
        )
        
        // Configure service to monitor settings
        configureService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !settings().isAppLockerEnabled) return
        
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged(event)
                }
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    handleViewClicked(event)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    handleContentChanged(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    /**
     * Handle window state changes to detect Settings navigation.
     */
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        val className = event.className?.toString()
        
        Log.d(TAG, "Window state changed: package=$packageName, class=$className")
        
        // Check if user navigated to Settings app
        if (packageName == SETTINGS_PACKAGE) {
            when {
                // Check for accessibility settings (all paths)
                className?.contains("AccessibilitySettings") == true ||
                className?.contains("ToggleAccessibilityService") == true ||
                className?.contains("accessibility") == true -> {
                    Log.i(TAG, "Accessibility settings detected (any path)")
                    isMonitoringSettings = true
                }
                
                // Check for device admin settings (all paths)
                className?.contains("DeviceAdminSettings") == true ||
                className?.contains("DeviceAdminAdd") == true ||
                className?.contains("SecuritySettings") == true ||
                className?.contains("device_admin") == true -> {
                    Log.i(TAG, "Device admin settings detected (any path)")
                    triggerDeviceAdminProtection()
                }
                
                // Check for app info settings (force stop, disable, uninstall)
                className?.contains("InstalledAppDetailsActivity") == true ||
                className?.contains("AppInfoBase") == true ||
                className?.contains("ApplicationInfo") == true -> {
                    Log.i(TAG, "App info settings detected")
                    if (isBrowserAppInfoTarget(event)) {
                        triggerAppInfoProtection()
                    }
                }
                
                // Check for overlay permission settings
                className?.contains("DrawOverlayDetails") == true ||
                className?.contains("OverlayPermission") == true ||
                className?.contains("SpecialAccess") == true -> {
                    Log.i(TAG, "Overlay permission settings detected")
                    if (isBrowserOverlayTarget(event)) {
                        triggerOverlayPermissionProtection()
                    }
                }
            }
        } else {
            // User left Settings app
            isMonitoringSettings = false
            securityOverlayManager.hideOverlay()
        }
    }

    /**
     * Handle view clicks to detect disable attempts.
     */
    private fun handleViewClicked(event: AccessibilityEvent) {
        if (!isMonitoringSettings || event.packageName != SETTINGS_PACKAGE) return
        
        // Check if click was on our accessibility service toggle
        val sourceNode = event.source
        if (sourceNode != null && isBrowserAccessibilityService(sourceNode)) {
            Log.w(TAG, "SECURITY: Accessibility service disable attempt detected!")
            triggerAccessibilityProtection()
        }
    }

    /**
     * Handle content changes to detect service state modifications.
     */
    private fun handleContentChanged(event: AccessibilityEvent) {
        if (!isMonitoringSettings || event.packageName != SETTINGS_PACKAGE) return
        
        // Monitor for changes in accessibility service list
        val sourceNode = event.source
        if (sourceNode != null) {
            checkForServiceToggleAttempt(sourceNode)
        }
    }

    /**
     * Check if the accessibility node relates to our browser's accessibility service.
     */
    private fun isBrowserAccessibilityService(node: AccessibilityNodeInfo): Boolean {
        try {
            // Check node text for our service name or package
            val nodeText = node.text?.toString()
            val contentDesc = node.contentDescription?.toString()
            
            val browserPackage = applicationContext.packageName
            val serviceName = "App Lock Accessibility Service"
            
            return nodeText?.contains(browserPackage) == true ||
                   nodeText?.contains(serviceName) == true ||
                   contentDesc?.contains(browserPackage) == true ||
                   contentDesc?.contains(serviceName) == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking browser service node", e)
            return false
        }
    }

    /**
     * Check for attempts to toggle our accessibility service.
     */
    private fun checkForServiceToggleAttempt(node: AccessibilityNodeInfo) {
        try {
            // Recursively check child nodes for our service
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    if (isBrowserAccessibilityService(child)) {
                        // Check if this is a toggle/switch being modified
                        if (child.isCheckable && child.isClickable) {
                            Log.w(TAG, "SECURITY: Browser accessibility service toggle detected!")
                            triggerAccessibilityProtection()
                            return
                        }
                    }
                    checkForServiceToggleAttempt(child)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking service toggle", e)
        }
    }

    /**
     * Trigger accessibility service protection overlay.
     */
    private fun triggerAccessibilityProtection() {
        Log.w(TAG, "SECURITY: Blocking accessibility service disable attempt")
        
        // Show protection overlay immediately
        securityOverlayManager.showAccessibilityProtectionOverlay()
        
        // Force navigation back to home
        navigateToHome()
    }

    /**
     * Trigger device admin protection overlay.
     */
    private fun triggerDeviceAdminProtection() {
        Log.w(TAG, "SECURITY: Blocking device admin settings access")
        
        // Show protection overlay immediately
        securityOverlayManager.showDeviceAdminProtectionOverlay()
        
        // Force navigation back to home
        navigateToHome()
    }

    /**
     * Check if app info settings are targeting the browser app.
     */
    private fun isBrowserAppInfoTarget(event: AccessibilityEvent): Boolean {
        // In a real implementation, you would check the intent extras or window content
        // For now, we'll assume any app info access during protection is suspicious
        return settings().isAppLockerEnabled
    }

    /**
     * Check if overlay permission settings are targeting the browser app.
     */
    private fun isBrowserOverlayTarget(event: AccessibilityEvent): Boolean {
        // In a real implementation, you would check the package name in the intent
        // For now, we'll assume any overlay permission access during protection is suspicious
        return settings().isAppLockerEnabled
    }

    /**
     * Trigger app info protection overlay.
     */
    private fun triggerAppInfoProtection() {
        Log.w(TAG, "SECURITY: Blocking app info access for browser")
        
        // Show protection overlay immediately
        securityOverlayManager.showAppInfoProtectionOverlay()
        
        // Force navigation back to home
        navigateToHome()
    }

    /**
     * Trigger overlay permission protection overlay.
     */
    private fun triggerOverlayPermissionProtection() {
        Log.w(TAG, "SECURITY: Blocking overlay permission modification for browser")
        
        // Show protection overlay immediately
        securityOverlayManager.showOverlayPermissionProtectionOverlay()
        
        // Force navigation back to home
        navigateToHome()
    }

    /**
     * Configure the accessibility service parameters.
     */
    private fun configureService() {
        val serviceInfo = AccessibilityServiceInfo().apply {
            // Monitor specific event types
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            
            // Monitor Settings package specifically
            packageNames = arrayOf(SETTINGS_PACKAGE)
            
            // Set feedback and flags
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            
            // Set notification timeout
            notificationTimeout = 100
        }
        
        setServiceInfo(serviceInfo)
        Log.d(TAG, "Security protection service configured")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Security protection accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Security protection accessibility service destroyed")
        securityOverlayManager.destroy()
    }

    /**
     * Force navigation back to home screen.
     */
    private fun navigateToHome() {
        try {
            val homeIntent = Intent().apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to home", e)
        }
    }
}
