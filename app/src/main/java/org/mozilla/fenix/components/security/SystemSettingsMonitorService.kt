/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.security

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
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
 * Foreground service that monitors system settings navigation to protect
 * accessibility service and device admin settings from unauthorized changes.
 */
class SystemSettingsMonitorService : Service() {

    companion object {
        private const val TAG = "SystemSettingsMonitor"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "security_monitor_channel"
        private const val MONITOR_INTERVAL_MS = 1000L // Check every second
        
        // Settings actions to monitor
        private const val ACCESSIBILITY_SETTINGS = "android.settings.ACCESSIBILITY_SETTINGS"
        private const val DEVICE_ADMIN_SETTINGS = "android.settings.DEVICE_ADMIN_SETTINGS"
        private const val SECURITY_SETTINGS = "android.provider.Settings.ACTION_SECURITY_SETTINGS"
        private const val APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
        private const val MANAGE_OVERLAY_PERMISSION = "android.settings.action.MANAGE_OVERLAY_PERMISSION"
        private const val MANAGE_APPLICATIONS_SETTINGS = "android.settings.MANAGE_APPLICATIONS_SETTINGS"
        
        // Settings activities to monitor for browser package
        private val MONITORED_ACTIVITIES = setOf(
            "com.android.settings.applications.InstalledAppDetailsActivity",
            "com.android.settings.applications.AppInfoBase",
            "com.android.settings.applications.specialaccess.deviceadmin.DeviceAdminSettings",
            "com.android.settings.applications.specialaccess.SpecialAccessSettings",
            "com.android.settings.accessibility.AccessibilitySettingsActivity",
            "com.android.settings.accessibility.ToggleAccessibilityServiceActivity", 
            "com.android.settings.Settings\$DrawOverlayDetailsActivity",
            "com.android.settings.applications.DrawOverlayDetails",
            "com.android.settings.DeviceAdminAdd"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private var isMonitoring = false
    
    private lateinit var securityOverlayManager: SecurityOverlayManager
    private lateinit var usageStatsManager: UsageStatsManager
    
    private var lastCheckedTime = 0L
    private var isSettingsAppActive = false
    private var protectionTriggered = false

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen turned on, resuming monitoring")
                    startMonitoring()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen turned off, pausing monitoring")
                    stopMonitoring()
                    securityOverlayManager.hideOverlay()
                    protectionTriggered = false
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SystemSettingsMonitorService created")
        
        securityOverlayManager = SecurityOverlayManager(this)
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        createNotificationChannel()
        registerScreenReceiver()
        
        lastCheckedTime = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "SystemSettingsMonitorService started")
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start monitoring if App Locker is enabled
        if (settings().isAppLockerEnabled) {
            startMonitoring()
        }
        
        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SystemSettingsMonitorService destroyed")
        
        stopMonitoring()
        securityOverlayManager.destroy()
        
        try {
            unregisterReceiver(screenOnReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering screen receiver", e)
        }
    }

    /**
     * Start monitoring system settings access.
     */
    private fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        Log.d(TAG, "Starting system settings monitoring")
        
        monitoringRunnable = object : Runnable {
            override fun run() {
                if (isMonitoring) {
                    checkForSettingsAccess()
                    handler.postDelayed(this, MONITOR_INTERVAL_MS)
                }
            }
        }
        
        handler.post(monitoringRunnable!!)
    }

    /**
     * Stop monitoring system settings access.
     */
    private fun stopMonitoring() {
        if (!isMonitoring) return
        
        isMonitoring = false
        Log.d(TAG, "Stopping system settings monitoring")
        
        monitoringRunnable?.let { handler.removeCallbacks(it) }
        monitoringRunnable = null
    }

    /**
     * Check for unauthorized access to protected settings.
     */
    private fun checkForSettingsAccess() {
        try {
            val currentTime = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(lastCheckedTime, currentTime)
            
            var settingsAccessDetected = false
            var accessibilitySettingsDetected = false
            var deviceAdminSettingsDetected = false
            var appInfoSettingsDetected = false
            var overlayPermissionSettingsDetected = false
            
            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                
                // Check for Settings app activity
                if (event.packageName == "com.android.settings" && 
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    
                    settingsAccessDetected = true
                    isSettingsAppActive = true
                    
                    // Check for specific settings access through activity names
                    val className = event.className
                    
                    when {
                        // Accessibility settings detection (all paths)
                        className?.contains("AccessibilitySettings") == true ||
                        className?.contains("ToggleAccessibilityService") == true ||
                        className?.contains("accessibility") == true -> {
                            accessibilitySettingsDetected = true
                        }
                        
                        // Device admin settings detection (all paths)
                        className?.contains("DeviceAdminSettings") == true ||
                        className?.contains("DeviceAdminAdd") == true ||
                        className?.contains("SecuritySettings") == true ||
                        className?.contains("device_admin") == true -> {
                            deviceAdminSettingsDetected = true
                        }
                        
                        // App info settings detection (force stop, disable, uninstall paths)
                        className?.contains("InstalledAppDetailsActivity") == true ||
                        className?.contains("AppInfoBase") == true ||
                        className?.contains("ApplicationInfo") == true -> {
                            appInfoSettingsDetected = true
                        }
                        
                        // Overlay permission settings detection
                        className?.contains("DrawOverlayDetails") == true ||
                        className?.contains("OverlayPermission") == true ||
                        className?.contains("SpecialAccess") == true -> {
                            overlayPermissionSettingsDetected = true
                        }
                    }
                }
                
                // Check for Settings app pause/stop
                if (event.packageName == "com.android.settings" && 
                    (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                     event.eventType == UsageEvents.Event.ACTIVITY_STOPPED)) {
                    isSettingsAppActive = false
                    protectionTriggered = false
                }
            }
            
            // Trigger protection if needed
            if (settingsAccessDetected && isSettingsAppActive && !protectionTriggered) {
                when {
                    accessibilitySettingsDetected -> {
                        Log.i(TAG, "Accessibility settings access detected (any path) - triggering protection")
                        triggerAccessibilityProtection()
                    }
                    deviceAdminSettingsDetected -> {
                        Log.i(TAG, "Device admin settings access detected (any path) - triggering protection")
                        triggerDeviceAdminProtection()
                    }
                    appInfoSettingsDetected -> {
                        Log.i(TAG, "App info settings access detected - checking if browser targeted")
                        checkAppInfoTarget()
                    }
                    overlayPermissionSettingsDetected -> {
                        Log.i(TAG, "Overlay permission settings access detected - checking if browser targeted")
                        checkOverlayPermissionTarget()
                    }
                }
            }
            
            lastCheckedTime = currentTime
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking settings access", e)
        }
    }

    /**
     * Trigger accessibility service protection overlay.
     */
    private fun triggerAccessibilityProtection() {
        if (protectionTriggered) return
        
        Log.w(TAG, "SECURITY: Blocking unauthorized accessibility settings access")
        protectionTriggered = true
        
        // Show protection overlay
        securityOverlayManager.showAccessibilityProtectionOverlay()
        
        // Send broadcast to close Settings activity
        try {
            val closeIntent = Intent().apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(closeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Settings activity", e)
        }
    }

    /**
     * Trigger device admin protection overlay.
     */
    private fun triggerDeviceAdminProtection() {
        if (protectionTriggered) return
        
        Log.w(TAG, "SECURITY: Blocking unauthorized device admin settings access")
        protectionTriggered = true
        
        // Show protection overlay
        securityOverlayManager.showDeviceAdminProtectionOverlay()
        
        // Send broadcast to close Settings activity
        try {
            val closeIntent = Intent().apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(closeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Settings activity", e)
        }
    }

    /**
     * Check if app info settings are targeting the browser.
     */
    private fun checkAppInfoTarget() {
        // This would be enhanced with intent monitoring in a real implementation
        // For now, we'll protect proactively when app info is accessed during active protection
        if (settings().isAppLockerEnabled) {
            Log.w(TAG, "SECURITY: Blocking app info access while protection is active")
            triggerAppInfoProtection()
        }
    }

    /**
     * Check if overlay permission settings are targeting the browser.
     */
    private fun checkOverlayPermissionTarget() {
        // This would be enhanced with intent monitoring in a real implementation
        // For now, we'll protect proactively when overlay permission settings are accessed
        if (settings().isAppLockerEnabled) {
            Log.w(TAG, "SECURITY: Blocking overlay permission modification while protection is active")
            triggerOverlayPermissionProtection()
        }
    }

    /**
     * Trigger app info protection overlay.
     */
    private fun triggerAppInfoProtection() {
        if (protectionTriggered) return
        
        Log.w(TAG, "SECURITY: Blocking unauthorized app info access")
        protectionTriggered = true
        
        // Show protection overlay
        securityOverlayManager.showAppInfoProtectionOverlay()
        
        // Close Settings and return to home
        navigateToHome()
    }

    /**
     * Trigger overlay permission protection overlay.
     */
    private fun triggerOverlayPermissionProtection() {
        if (protectionTriggered) return
        
        Log.w(TAG, "SECURITY: Blocking unauthorized overlay permission modification")
        protectionTriggered = true
        
        // Show protection overlay
        securityOverlayManager.showOverlayPermissionProtectionOverlay()
        
        // Close Settings and return to home
        navigateToHome()
    }

    /**
     * Navigate to home and clear task stack.
     */
    private fun navigateToHome() {
        try {
            val closeIntent = Intent().apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(closeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to home", e)
        }
    }

    /**
     * Register screen state receiver.
     */
    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenOnReceiver, filter)
    }

    /**
     * Create notification channel for the foreground service.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Security Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors system settings to protect security features"
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
            .setContentText("App Locker protection is running")
            .setSmallIcon(R.drawable.ic_lock_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}
