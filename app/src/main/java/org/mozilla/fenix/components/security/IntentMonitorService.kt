/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.security

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.settings

/**
 * Enhanced service that monitors for specific intents targeting the browser package
 * to prevent bypass through direct deep-links or external app launches.
 */
class IntentMonitorService : Service() {

    companion object {
        private const val TAG = "IntentMonitorService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "intent_monitor_channel"
    }

    private lateinit var securityOverlayManager: SecurityOverlayManager
    private var isMonitoring = false

    private val intentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null || !settings().isAppLockerEnabled) return
            
            handleSuspiciousIntent(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "IntentMonitorService created")
        
        securityOverlayManager = SecurityOverlayManager(this)
        createNotificationChannel()
        registerIntentFilters()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "IntentMonitorService started")
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start monitoring if App Locker is enabled
        if (settings().isAppLockerEnabled) {
            startMonitoring()
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "IntentMonitorService destroyed")
        
        stopMonitoring()
        securityOverlayManager.destroy()
        
        try {
            unregisterReceiver(intentReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering intent receiver", e)
        }
    }

    /**
     * Register intent filters for suspicious activities.
     */
    private fun registerIntentFilters() {
        try {
            val filter = IntentFilter().apply {
                // Monitor for application details intents
                addAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                // Monitor for overlay permission intents
                addAction(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                // Monitor for accessibility settings intents
                addAction(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                // Monitor for device admin intents
                addAction(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                addAction("android.app.action.DEVICE_ADMIN_DISABLE_REQUESTED")
                
                // Add data scheme for package-specific intents
                addDataScheme("package")
            }
            
            registerReceiver(intentReceiver, filter)
            Log.d(TAG, "Intent filters registered")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error registering intent filters", e)
        }
    }

    /**
     * Handle potentially suspicious intents targeting the browser.
     */
    private fun handleSuspiciousIntent(intent: Intent) {
        val action = intent.action
        val data = intent.data
        val browserPackage = packageName
        
        Log.d(TAG, "Suspicious intent detected: action=$action, data=$data")
        
        // Check if intent targets the browser package
        val targetsBrowser = data?.schemeSpecificPart == browserPackage ||
                           intent.getStringExtra("android.provider.extra.APP_PACKAGE") == browserPackage ||
                           intent.getStringExtra("android.intent.extra.PACKAGE_NAME") == browserPackage
        
        if (!targetsBrowser) {
            Log.d(TAG, "Intent does not target browser package, ignoring")
            return
        }
        
        when (action) {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS -> {
                Log.w(TAG, "SECURITY: Blocking application details intent for browser")
                triggerAppInfoProtection()
            }
            
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION -> {
                Log.w(TAG, "SECURITY: Blocking overlay permission intent for browser")
                triggerOverlayPermissionProtection()
            }
            
            Settings.ACTION_ACCESSIBILITY_SETTINGS -> {
                Log.w(TAG, "SECURITY: Blocking accessibility settings intent")
                triggerAccessibilityProtection()
            }
            
            android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN,
            "android.app.action.DEVICE_ADMIN_DISABLE_REQUESTED" -> {
                Log.w(TAG, "SECURITY: Blocking device admin intent for browser")
                triggerDeviceAdminProtection()
            }
        }
    }

    /**
     * Start monitoring intents.
     */
    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        Log.d(TAG, "Intent monitoring started")
    }

    /**
     * Stop monitoring intents.
     */
    private fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false
        Log.d(TAG, "Intent monitoring stopped")
    }

    /**
     * Trigger app info protection overlay.
     */
    private fun triggerAppInfoProtection() {
        securityOverlayManager.showAppInfoProtectionOverlay()
        navigateToHome()
    }

    /**
     * Trigger overlay permission protection overlay.
     */
    private fun triggerOverlayPermissionProtection() {
        securityOverlayManager.showOverlayPermissionProtectionOverlay()
        navigateToHome()
    }

    /**
     * Trigger accessibility protection overlay.
     */
    private fun triggerAccessibilityProtection() {
        securityOverlayManager.showAccessibilityProtectionOverlay()
        navigateToHome()
    }

    /**
     * Trigger device admin protection overlay.
     */
    private fun triggerDeviceAdminProtection() {
        securityOverlayManager.showDeviceAdminProtectionOverlay()
        navigateToHome()
    }

    /**
     * Navigate to home and clear task stack.
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
     * Create notification channel for the foreground service.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Intent Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors intents to protect security features"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create notification for the foreground service.
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Active")
            .setContentText("Intent monitoring protection is running")
            .setSmallIcon(R.drawable.ic_lock_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}
