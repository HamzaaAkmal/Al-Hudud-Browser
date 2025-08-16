/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.security

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.settings

/**
 * Accessibility service that monitors text input across all apps to detect
 * and block harmful keywords while App Locker protection is active.
 */
class KeywordProtectionAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KeywordProtectionAS"
        private const val NOTIFICATION_ID = 3001
        private const val NOTIFICATION_CHANNEL_ID = "keyword_protection_service"
        
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
            "com.tiktok.musically", // TikTok
            "org.telegram.messenger", // Telegram
            "com.viber.voip", // Viber
            "com.discord", // Discord
            "com.zhiliaoapp.musically", // TikTok
            "com.ss.android.ugc.trill", // TikTok Lite
            "org.mozilla.firefox", // Firefox
            "org.mozilla.fenix", // Firefox Fenix
            "com.brave.browser", // Brave Browser
            "com.opera.browser", // Opera
            "com.microsoft.emmx", // Edge
            "com.duckduckgo.mobile.android" // DuckDuckGo
        )
    }

    private lateinit var securityOverlayManager: SecurityOverlayManager
    private lateinit var securityBypassManager: SecurityBypassManager
    private var isMonitoringActive = false
    private var keywordCheckReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Keyword protection accessibility service connected")
        
        securityOverlayManager = SecurityOverlayManager(this)
        securityBypassManager = SecurityBypassManager(this)
        
        configureService()
        updateMonitoringState()
        createNotificationChannel()
        startForegroundService()
        registerKeywordCheckReceiver()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !shouldMonitor()) return
        
        // Skip monitoring during grace period
        if (securityOverlayManager.isInGracePeriod()) {
            return
        }
        
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
            Log.e(TAG, "Error handling accessibility event", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Keyword protection accessibility service interrupted")
        isMonitoringActive = false
    }

    /**
     * Handle text input changes to detect banned keywords.
     */
    private fun handleTextChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // Skip our own package
        if (packageName == applicationContext.packageName) return
        
        // Get text from available sources
        val eventText = event.text?.joinToString(" ") ?: ""
        val beforeText = event.beforeText?.toString() ?: ""
        val contentDescription = event.contentDescription?.toString() ?: ""
        
        // Check all text sources
        val textsToCheck = listOf(eventText, beforeText, contentDescription).filter { it.isNotBlank() }
        
        for (text in textsToCheck) {
            if (containsBannedKeyword(text)) {
                Log.w(TAG, "SECURITY: Banned keyword detected in text input - $packageName")
                triggerKeywordProtection(packageName, text)
                return // Stop after first detection
            }
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
            Log.d(TAG, "Search/input field focused in $packageName - monitoring for text input")
            
            // Check current text content immediately
            val currentText = sourceNode.text?.toString()
            if (!currentText.isNullOrBlank() && containsBannedKeyword(currentText)) {
                Log.w(TAG, "SECURITY: Banned keyword found in focused field - $packageName")
                triggerKeywordProtection(packageName, currentText)
            }
        }
    }

    /**
     * Handle window content changes to scan for text content.
     */
    private fun handleContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // Skip our own package
        if (packageName == applicationContext.packageName) return
        
        // Only scan content for monitored packages to avoid performance issues
        if (MONITORED_PACKAGES.contains(packageName)) {
            scanNodeForKeywords(event.source, packageName)
        }
    }

    /**
     * Recursively scan accessibility node for banned keywords.
     */
    private fun scanNodeForKeywords(node: AccessibilityNodeInfo?, packageName: String) {
        if (node == null) return
        
        try {
            // Check text content
            val nodeText = node.text?.toString()
            if (!nodeText.isNullOrBlank() && containsBannedKeyword(nodeText)) {
                Log.w(TAG, "SECURITY: Banned keyword found in content - $packageName")
                triggerKeywordProtection(packageName, nodeText)
                return
            }
            
            // Check content description
            val contentDesc = node.contentDescription?.toString()
            if (!contentDesc.isNullOrBlank() && containsBannedKeyword(contentDesc)) {
                Log.w(TAG, "SECURITY: Banned keyword found in content description - $packageName")
                triggerKeywordProtection(packageName, contentDesc)
                return
            }
            
            // Recursively check child nodes (limit depth for performance)
            for (i in 0 until minOf(node.childCount, 10)) {
                val child = node.getChild(i)
                if (child != null) {
                    scanNodeForKeywords(child, packageName)
                    // Note: recycle() is deprecated in newer Android versions
                    // The system will handle cleanup automatically
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning node for keywords", e)
        }
    }

    /**
     * Check if text contains any banned keywords.
     */
    private fun containsBannedKeyword(text: String): Boolean {
        if (text.isBlank()) return false
        
        val bannedKeywords = settings().bannedKeywords
        if (bannedKeywords.isEmpty()) {
            Log.d(TAG, "No keywords configured for protection")
            return false
        }
        
        // Normalize text for better matching
        val normalizedText = text.lowercase().trim()
        
        // Check each keyword with multiple matching strategies
        for (keyword in bannedKeywords) {
            val normalizedKeyword = keyword.lowercase().trim()
            if (normalizedKeyword.isEmpty()) continue
            
            // Exact match
            if (normalizedText.contains(normalizedKeyword)) {
                Log.w(TAG, "SECURITY: Exact keyword match found: '$keyword' in text")
                return true
            }
            
            // Word boundary match (as complete words)
            val wordPattern = "\\b${Regex.escape(normalizedKeyword)}\\b"
            try {
                if (normalizedText.contains(Regex(wordPattern))) {
                    Log.w(TAG, "SECURITY: Word boundary keyword match found: '$keyword' in text")
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in regex matching for keyword '$keyword'", e)
            }
            
            // Split text by common separators and check each part
            val textParts = normalizedText.split(" ", "\n", "\t", ".", ",", "!", "?", ";", ":")
            for (part in textParts) {
                val cleanPart = part.trim()
                if (cleanPart == normalizedKeyword) {
                    Log.w(TAG, "SECURITY: Part match keyword found: '$keyword' in text part '$cleanPart'")
                    return true
                }
            }
        }
        
        return false
    }

    /**
     * Trigger keyword protection when banned content is detected.
     */
    private fun triggerKeywordProtection(packageName: String, detectedText: String) {
        Log.w(TAG, "SECURITY: Triggering keyword protection for $packageName - text: ${detectedText.take(20)}...")
        
        try {
            // Show smart protection overlay with countdown timer
            securityOverlayManager.showKeywordProtectionOverlay(packageName)
            
            Log.w(TAG, "Smart keyword protection overlay shown - user has 15 seconds to fix")
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering keyword protection", e)
        }
    }
    
    /**
     * Register broadcast receiver for keyword checks after grace period.
     */
    private fun registerKeywordCheckReceiver() {
        keywordCheckReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "org.mozilla.fenix.CHECK_KEYWORDS_AFTER_GRACE") {
                    val packageName = intent.getStringExtra("package_name")
                    if (!packageName.isNullOrBlank()) {
                        Log.d(TAG, "Received keyword check request for $packageName after grace period")
                        performDelayedKeywordCheck(packageName)
                    }
                }
            }
        }
        
        val filter = IntentFilter("org.mozilla.fenix.CHECK_KEYWORDS_AFTER_GRACE")
        registerReceiver(keywordCheckReceiver, filter)
        Log.d(TAG, "Keyword check receiver registered")
    }
    
    /**
     * Perform keyword check after grace period.
     */
    private fun performDelayedKeywordCheck(packageName: String) {
        try {
            // Get current active windows and scan for keywords
            val windows = windows
            for (window in windows) {
                val rootNode = window.root
                if (rootNode != null) {
                    val windowPackage = rootNode.packageName?.toString()
                    if (windowPackage == packageName) {
                        Log.d(TAG, "Scanning $packageName for keywords after grace period")
                        scanNodeForKeywords(rootNode, packageName)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing delayed keyword check", e)
        }
    }

    /**
     * Check if monitoring should be active.
     */
    private fun shouldMonitor(): Boolean {
        try {
            // Check if keyword protection is enabled
            if (!settings().isKeywordProtectionEnabled) {
                if (isMonitoringActive) {
                    Log.d(TAG, "Keyword protection disabled, stopping monitoring")
                    isMonitoringActive = false
                }
                return false
            }
            
            // Check if within bypass window
            if (securityBypassManager.isWithinBypassWindow()) {
                if (isMonitoringActive) {
                    Log.d(TAG, "Within bypass window, pausing monitoring")
                    isMonitoringActive = false
                }
                return false
            }
            
            if (!isMonitoringActive) {
                Log.d(TAG, "Keyword protection monitoring activated")
                isMonitoringActive = true
            }
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking monitoring state", e)
            return false
        }
    }

    /**
     * Check if a node represents a search or input field.
     */
    private fun isSearchOrInputField(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString()?.lowercase() ?: ""
        val viewIdResourceName = node.viewIdResourceName?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        
        // Check for input field class names
        if (className.contains("edittext") || className.contains("input") || className.contains("search")) {
            return true
        }
        
        // Check for search-related IDs and descriptions
        val searchTerms = listOf("search", "query", "input", "text", "edit")
        return searchTerms.any { term ->
            viewIdResourceName.contains(term) || contentDesc.contains(term)
        }
    }

    /**
     * Configure the accessibility service.
     */
    private fun configureService() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                   AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        setServiceInfo(info)
        Log.d(TAG, "Accessibility service configured")
    }

    /**
     * Update monitoring state based on settings.
     */
    private fun updateMonitoringState() {
        shouldMonitor() // This will update isMonitoringActive
        Log.d(TAG, "Monitoring state updated: $isMonitoringActive")
    }

    /**
     * Create notification channel for foreground service.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Keyword Protection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service that monitors text input for spiritual protection"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Start the service as a foreground service with notification.
     */
    private fun startForegroundService() {
        val notification = createServiceNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Started as foreground service")
    }

    /**
     * Create the notification for the foreground service.
     */
    private fun createServiceNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Keyword Protection Active")
            .setContentText("Monitoring text input for spiritual safety")
            .setSmallIcon(R.drawable.ic_lock_24)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Navigate to home screen.
     */
    private fun navigateToHome() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to home", e)
        }
    }

    /**
     * Clear recent tasks to prevent easy return to blocked app.
     */
    private fun clearRecentTasks() {
        try {
            // Perform global action to show recent apps and clear them
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing recent tasks", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Keyword protection accessibility service destroyed")
        isMonitoringActive = false
        
        // Unregister broadcast receiver
        keywordCheckReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "Keyword check receiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering keyword check receiver", e)
            }
        }
        keywordCheckReceiver = null
    }
}
