/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.security

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.mozilla.fenix.ext.settings

/**
 * Accessibility service that monitors text input across all apps to detect
 * and block harmful keywords while App Locker protection is active.
 */
class KeywordProtectionAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KeywordProtectionAS"
        
        // Common social media and browser packages to monitor
        private val MONITORED_PACKAGES = setOf(
            "com.facebook.katana", // Facebook
            "com.instagram.android", // Instagram
            "com.google.android.youtube", // YouTube
            "com.google.android.apps.chrome", // Chrome
            "com.android.chrome", // Chrome
            "com.twitter.android", // Twitter/X
            "com.whatsapp", // WhatsApp
            "com.snapchat.android", // Snapchat
            "com.tiktok", // TikTok
            "com.reddit.frontpage", // Reddit
            "org.telegram.messenger", // Telegram
            "com.discord", // Discord
            "com.pinterest", // Pinterest
            "com.tumblr", // Tumblr
            "com.opera.browser", // Opera
            "org.mozilla.firefox", // Firefox
            "com.microsoft.emmx", // Edge
            "com.brave.browser", // Brave
            "com.duckduckgo.mobile.android", // DuckDuckGo
        )
    }

    private lateinit var securityOverlayManager: SecurityOverlayManager
    private lateinit var securityBypassManager: SecurityBypassManager
    private var isMonitoringActive = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Keyword protection accessibility service connected")
        
        securityOverlayManager = SecurityOverlayManager(this)
        securityBypassManager = SecurityBypassManager(this)
        
        configureService()
        updateMonitoringState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !shouldMonitor()) return
        
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    handleTextChanged(event)
                }
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    handleViewFocused(event)
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
     * Handle text input changes to detect banned keywords.
     */
    private fun handleTextChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // Skip our own package
        if (packageName == applicationContext.packageName) return
        
        val inputText = event.text?.joinToString(" ") ?: ""
        if (inputText.isBlank()) return
        
        if (containsBannedKeyword(inputText)) {
            Log.w(TAG, "SECURITY: Banned keyword detected in $packageName")
            triggerKeywordProtection(packageName, inputText)
        }
    }

    /**
     * Handle view focus changes to monitor search boxes and input fields.
     */
    private fun handleViewFocused(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // Skip our own package
        if (packageName == applicationContext.packageName) return
        
        // Check if focused element is a search or input field
        val sourceNode = event.source
        if (sourceNode != null && isSearchOrInputField(sourceNode)) {
            Log.d(TAG, "Search/input field focused in $packageName")
        }
    }

    /**
     * Handle content changes to catch text input in various UI frameworks.
     */
    private fun handleContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // Skip our own package
        if (packageName == applicationContext.packageName) return
        
        // Check for text content in the changed view
        val sourceNode = event.source
        if (sourceNode != null) {
            checkNodeForBannedContent(sourceNode, packageName)
        }
    }

    /**
     * Check if an accessibility node represents a search or input field.
     */
    private fun isSearchOrInputField(node: AccessibilityNodeInfo): Boolean {
        try {
            val className = node.className?.toString()?.lowercase()
            val contentDesc = node.contentDescription?.toString()?.lowercase()
            val hint = node.hintText?.toString()?.lowercase()
            
            val searchKeywords = listOf("search", "edit", "input", "text", "query")
            
            return searchKeywords.any { keyword ->
                className?.contains(keyword) == true ||
                contentDesc?.contains(keyword) == true ||
                hint?.contains(keyword) == true
            } || node.isEditable
        } catch (e: Exception) {
            Log.e(TAG, "Error checking input field", e)
            return false
        }
    }

    /**
     * Recursively check a node and its children for banned content.
     */
    private fun checkNodeForBannedContent(node: AccessibilityNodeInfo, packageName: String) {
        try {
            // Check current node text
            val nodeText = node.text?.toString()
            if (!nodeText.isNullOrBlank() && containsBannedKeyword(nodeText)) {
                Log.w(TAG, "SECURITY: Banned keyword detected in node content from $packageName")
                triggerKeywordProtection(packageName, nodeText)
                return
            }
            
            // Check content description
            val contentDesc = node.contentDescription?.toString()
            if (!contentDesc.isNullOrBlank() && containsBannedKeyword(contentDesc)) {
                Log.w(TAG, "SECURITY: Banned keyword detected in content description from $packageName")
                triggerKeywordProtection(packageName, contentDesc)
                return
            }
            
            // Recursively check children (limit depth to prevent performance issues)
            for (i in 0 until minOf(node.childCount, 20)) {
                val child = node.getChild(i)
                if (child != null) {
                    checkNodeForBannedContent(child, packageName)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking node content", e)
        }
    }

    /**
     * Check if text contains any banned keywords.
     */
    private fun containsBannedKeyword(text: String): Boolean {
        val lowerText = text.lowercase().trim()
        val bannedKeywords = settings().bannedKeywords
        
        return bannedKeywords.any { keyword ->
            lowerText.contains(keyword.lowercase())
        }
    }

    /**
     * Trigger keyword protection when banned content is detected.
     */
    private fun triggerKeywordProtection(packageName: String, detectedText: String) {
        Log.w(TAG, "SECURITY: Triggering keyword protection for $packageName")
        
        // Show protection overlay
        securityOverlayManager.showKeywordProtectionOverlay(packageName)
        
        // Force navigation back to home
        navigateToHome()
        
        // Clear recent tasks to prevent returning to the app
        clearRecentTasks()
    }

    /**
     * Check if monitoring should be active.
     */
    private fun shouldMonitor(): Boolean {
        return isMonitoringActive && 
               settings().isAppLockerEnabled && 
               settings().isKeywordProtectionEnabled &&
               securityBypassManager.shouldTriggerProtection()
    }

    /**
     * Update monitoring state based on settings.
     */
    private fun updateMonitoringState() {
        val shouldBeActive = settings().isAppLockerEnabled && 
                           settings().isKeywordProtectionEnabled
        
        if (shouldBeActive != isMonitoringActive) {
            isMonitoringActive = shouldBeActive
            Log.d(TAG, "Keyword monitoring ${if (isMonitoringActive) "activated" else "deactivated"}")
            
            if (isMonitoringActive) {
                configureService()
            }
        }
    }

    /**
     * Configure the accessibility service parameters.
     */
    private fun configureService() {
        val serviceInfo = AccessibilityServiceInfo().apply {
            // Monitor text input events
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            
            // Monitor all packages (or specific ones for better performance)
            packageNames = MONITORED_PACKAGES.toTypedArray()
            
            // Set feedback and flags
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                   AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            
            // Set notification timeout
            notificationTimeout = 100
        }
        
        setServiceInfo(serviceInfo)
        Log.d(TAG, "Keyword protection service configured")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Keyword protection accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Keyword protection accessibility service destroyed")
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

    /**
     * Clear recent tasks to prevent returning to blocked app.
     */
    private fun clearRecentTasks() {
        try {
            // This would require additional permissions and implementation
            // For now, we'll just log the intent
            Log.d(TAG, "Would clear recent tasks if permission available")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing recent tasks", e)
        }
    }
}
