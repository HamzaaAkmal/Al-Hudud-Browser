/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import org.mozilla.fenix.ext.settings

/**
 * Central security manager that coordinates all browser security protection features.
 * Manages system-level protection services and overlay permissions.
 */
class SecurityManager(private val context: Context) {

    companion object {
        private const val TAG = "SecurityManager"
        
        // Permission request codes
        const val REQUEST_OVERLAY_PERMISSION = 1001
        const val REQUEST_USAGE_STATS_PERMISSION = 1002
    }

    private val securityBypassManager: SecurityBypassManager by lazy {
        SecurityBypassManager(context)
    }

    /**
     * Check if all required permissions are granted for security protection.
     */
    fun hasAllRequiredPermissions(): Boolean {
        return hasOverlayPermission() && hasUsageStatsPermission()
    }

    /**
     * Check if system alert window permission is granted.
     */
    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Permission not required on older Android versions
        }
    }

    /**
     * Check if usage stats permission is granted.
     */
    fun hasUsageStatsPermission(): Boolean {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            
            val mode = appOpsManager.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid,
                context.packageName
            )
            
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking usage stats permission", e)
            false
        }
    }

    /**
     * Request overlay permission from user.
     */
    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting overlay permission", e)
            }
        }
    }

    /**
     * Request usage stats permission from user.
     */
    fun requestUsageStatsPermission() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting usage stats permission", e)
        }
    }

    /**
     * Start system settings monitor service if App Locker is enabled.
     */
    fun startSecurityServices() {
        if (!context.settings().isAppLockerEnabled) {
            Log.d(TAG, "App Locker disabled, not starting security services")
            return
        }

        if (!hasAllRequiredPermissions()) {
            Log.w(TAG, "Missing required permissions for security services")
            return
        }

        try {
            // Start system settings monitor service
            val monitorIntent = Intent(context, SystemSettingsMonitorService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, monitorIntent)
            } else {
                context.startService(monitorIntent)
            }
            
            // Start intent monitor service
            val intentMonitorIntent = Intent(context, IntentMonitorService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intentMonitorIntent)
            } else {
                context.startService(intentMonitorIntent)
            }
            
            Log.i(TAG, "Security services started")
            
            // Note: Accessibility services (SecurityProtectionAccessibilityService and 
            // KeywordProtectionAccessibilityService) must be enabled manually by user
            // through Android accessibility settings. They cannot be started programmatically.
            Log.d(TAG, "Reminder: Accessibility services must be enabled manually in Settings")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting security services", e)
        }
    }

    /**
     * Stop all security services.
     */
    fun stopSecurityServices() {
        try {
            // Stop system settings monitor service
            val monitorIntent = Intent(context, SystemSettingsMonitorService::class.java)
            context.stopService(monitorIntent)
            
            // Stop intent monitor service
            val intentMonitorIntent = Intent(context, IntentMonitorService::class.java)
            context.stopService(intentMonitorIntent)
            
            Log.i(TAG, "Security services stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping security services", e)
        }
    }

    /**
     * Enable security protection (called when App Locker is enabled).
     */
    fun enableSecurityProtection() {
        Log.i(TAG, "Enabling system-level security protection")
        
        if (hasAllRequiredPermissions()) {
            startSecurityServices()
        } else {
            Log.w(TAG, "Cannot enable security protection - missing permissions")
        }
    }

    /**
     * Disable security protection (called when App Locker is disabled).
     */
    fun disableSecurityProtection() {
        Log.i(TAG, "Disabling system-level security protection")
        stopSecurityServices()
        
        // Clear any active bypass window when disabling protection
        securityBypassManager.clearBypassWindow()
    }

    /**
     * Record successful challenge completion and start bypass window.
     */
    fun recordChallengeCompletion() {
        securityBypassManager.recordChallengeCompletion()
        Log.i(TAG, "Challenge completion recorded, bypass window started")
    }

    /**
     * Check if currently within bypass window.
     */
    fun isWithinBypassWindow(): Boolean {
        return securityBypassManager.isWithinBypassWindow()
    }

    /**
     * Get remaining bypass time in milliseconds.
     */
    fun getRemainingBypassTime(): Long {
        return securityBypassManager.getRemainingBypassTime()
    }

    /**
     * Clear the bypass window manually.
     */
    fun clearBypassWindow() {
        securityBypassManager.clearBypassWindow()
        Log.i(TAG, "Bypass window manually cleared")
    }

    /**
     * Check if security protection is currently active.
     */
    fun isSecurityProtectionActive(): Boolean {
        return context.settings().isAppLockerEnabled && hasAllRequiredPermissions()
    }

    /**
     * Get status text for security protection.
     */
    fun getSecurityProtectionStatus(): String {
        return when {
            !context.settings().isAppLockerEnabled -> "Security protection disabled"
            !hasOverlayPermission() -> "Overlay permission required"
            !hasUsageStatsPermission() -> "Usage access permission required"
            else -> "Security protection active"
        }
    }

    /**
     * Get missing permissions list.
     */
    fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        
        if (!hasOverlayPermission()) {
            missing.add("System overlay permission")
        }
        
        if (!hasUsageStatsPermission()) {
            missing.add("Usage access permission")
        }
        
        return missing
    }

    /**
     * Show permission request dialog for missing permissions.
     */
    fun showPermissionRequestDialog() {
        val missing = getMissingPermissions()
        if (missing.isEmpty()) return
        
        // This would typically show a dialog explaining why permissions are needed
        // For now, we'll request them directly
        if (!hasOverlayPermission()) {
            requestOverlayPermission()
        } else if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        }
    }
}
