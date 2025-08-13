/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import org.mozilla.fenix.ext.settings

/**
 * Accessibility service for monitoring app launches and enforcing App Locker protection.
 * This service monitors when protected apps are launched and shows the lock screen.
 */
class AppLockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppLockAccessibilityService"
        const val ACTION_UNLOCK_SUCCESS = "org.mozilla.fenix.applocker.UNLOCK_SUCCESS"
    }

    private val unlockSessionManager = UnlockSessionManager()
    private var lastUnlockedApp: String? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                unlockSessionManager.clearAllSessions()
                lastUnlockedApp = null
            }
        }
    }

    private val unlockBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_UNLOCK_SUCCESS) {
                val packageName = intent.getStringExtra("package_name") ?: return
                unlockSessionManager.unlockApp(packageName)
                lastUnlockedApp = packageName
            }
        }
    }

    override fun onServiceConnected() {
        Log.d(TAG, "App Lock Accessibility service connected")

        // Register receivers
        val screenOffFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, screenOffFilter)

        val unlockSuccessFilter = IntentFilter(ACTION_UNLOCK_SUCCESS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockBroadcastReceiver, unlockSuccessFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unlockBroadcastReceiver, unlockSuccessFilter)
        }

        // Configure service info
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageNames = null // Listen to all packages
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        this.serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        
        // Enhanced logging for Google apps and critical debugging
        if (packageName.startsWith("com.google.") || packageName.startsWith("com.android.")) {
            Log.i(TAG, "GOOGLE/ANDROID APP DETECTED: $packageName, Event: ${event.eventType}")
        }
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "Window state changed for: $packageName")
                handleAppLaunch(packageName)
            }
            else -> {
                Log.v(TAG, "Other event type ${event.eventType} for: $packageName")
            }
        }
    }

    private fun handleAppLaunch(packageName: String) {
        // Add comprehensive logging for Google apps debugging
        Log.d(TAG, "handleAppLaunch called for package: $packageName")
        
        // Skip if app is temporarily unlocked
        if (unlockSessionManager.isTemporarilyUnlocked(packageName)) {
            Log.d(TAG, "Package $packageName is temporarily unlocked, marking foreground")
            unlockSessionManager.markAppInForeground(packageName)
            return
        }

        // Check if this app should be skipped (CRITICAL: This is where Google apps are bypassed!)
        val shouldSkip = shouldSkipApp(packageName)
        Log.d(TAG, "shouldSkipApp($packageName) = $shouldSkip")
        if (shouldSkip) {
            Log.w(TAG, "CRITICAL: Skipping app lock for $packageName - this may be the Google apps bypass bug!")
            return
        }

        // Check if app is protected
        val protectedApps = applicationContext.settings().appLockerProtectedApps
        val isAppLockerEnabled = applicationContext.settings().isAppLockerEnabled
        val isAppProtected = protectedApps.contains(packageName)
        
        Log.d(TAG, "App Locker enabled: $isAppLockerEnabled")
        Log.d(TAG, "Package $packageName is protected: $isAppProtected")
        Log.d(TAG, "Protected apps list: $protectedApps")
        
        if (isAppProtected && isAppLockerEnabled) {
            // Check if we switched from another app
            val switchedFromDifferentApp = packageName != lastUnlockedApp
            Log.d(TAG, "Switched from different app: $switchedFromDifferentApp (lastUnlockedApp: $lastUnlockedApp)")
            
            if (switchedFromDifferentApp) {
                Log.i(TAG, "Showing lock screen for protected app: $packageName")
                showLockScreen(packageName)
            } else {
                Log.d(TAG, "Same app launch, not showing lock screen")
            }
        } else {
            Log.d(TAG, "App $packageName not requiring lock screen (protected: $isAppProtected, enabled: $isAppLockerEnabled)")
        }
    }

    private fun shouldSkipApp(packageName: String): Boolean {
        // Enhanced Google Apps detection with comprehensive logging
        Log.d(TAG, "shouldSkipApp evaluation for: $packageName")
        
        // Our own browser app should always be skipped
        if (packageName == applicationContext.packageName) {
            Log.d(TAG, "Skipping our own package: $packageName")
            return true
        }
        
        // CRITICAL FIX: Remove Google apps bypass that was causing the security vulnerability
        // Previously this function was filtering out Google apps with com.android.* and android prefixes
        // Gmail (com.google.android.gm), Play Store (com.android.vending), Chrome (com.android.chrome)
        // were being bypassed due to these broad exclusions
        
        // Only skip essential system UI components that would break the device if locked
        val systemUIComponents = setOf(
            "com.android.systemui",
            "android.system.ui",
            "com.android.launcher",
            "com.android.settings" // Allow user to access settings if needed
        )
        
        if (systemUIComponents.any { packageName.startsWith(it) }) {
            Log.d(TAG, "Skipping essential system UI component: $packageName")
            return true
        }
        
        // SECURITY: DO NOT skip Google apps - they should be subject to App Locker like any other app
        if (packageName.startsWith("com.google.") || packageName.startsWith("com.android.")) {
            Log.i(TAG, "SECURITY FIX: Google/Android app $packageName will be subject to App Locker (not skipped)")
        }
        
        Log.d(TAG, "App $packageName will be processed for locking if configured")
        return false
    }

    private fun showLockScreen(packageName: String) {
        val intent = Intent(this, AppLockScreenActivity::class.java).apply {
            putExtra("target_package", packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.d(TAG, "App Lock Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
            unregisterReceiver(unlockBroadcastReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receivers", e)
        }
    }
}
