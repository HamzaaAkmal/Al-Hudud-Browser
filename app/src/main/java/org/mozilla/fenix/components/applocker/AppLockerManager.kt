/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.mozilla.fenix.ext.settings

/**
 * Manager class for App Locker functionality integration within IceCraven browser.
 * Handles app locking configuration, permission management, and status tracking.
 */
class AppLockerManager(private val context: Context) {

    companion object {
        private const val APP_LOCKER_SERVICE_NAME = "org.mozilla.fenix.components.applocker.AppLockAccessibilityService"
    }

    private val appInfoCache = AppInfoCache()

    /**
     * Check if App Locker is enabled.
     */
    fun isAppLockerEnabled(): Boolean {
        return context.settings().isAppLockerEnabled && isAccessibilityServiceEnabled()
    }

    /**
     * Check if accessibility service is enabled for App Locker.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return accessibilityServices?.contains(APP_LOCKER_SERVICE_NAME) == true
    }

    /**
     * Get the current status text for the preference.
     */
    fun getStatusText(): String {
        return if (isAppLockerEnabled()) {
            "App protection enabled"
        } else {
            "App protection disabled"
        }
    }

    /**
     * Get the summary text for the preference.
     */
    fun getSummaryText(): String {
        return if (isAppLockerEnabled()) {
            "Apps are protected with PIN authentication"
        } else {
            "Configure PIN protection for selected apps"
        }
    }

    /**
     * Get intent to open accessibility settings.
     */
    fun getAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    /**
     * Check if we have permission to query all packages (needed for app list).
     */
    fun hasQueryAllPackagesPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.QUERY_ALL_PACKAGES
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get list of installed apps that can be locked.
     */
    fun getInstallableApps(): List<AppInfo> {
        return appInfoCache.getCachedApps {
            loadInstallableApps()
        }
    }

    /**
     * Load apps from system (called by cache when needed).
     */
    private fun loadInstallableApps(): List<AppInfo> {
        if (!hasQueryAllPackagesPermission()) {
            return emptyList()
        }

        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        
        return installedApps
            .filter { appInfo ->
                // Filter out system apps and launcher apps
                val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                launchIntent != null && 
                appInfo.packageName != context.packageName &&
                !isSystemApp(appInfo.packageName)
            }
            .map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    name = appInfo.loadLabel(packageManager).toString(),
                    icon = try {
                        appInfo.loadIcon(packageManager)
                    } catch (e: Exception) {
                        null
                    },
                    isLocked = context.settings().appLockerProtectedApps.contains(appInfo.packageName)
                )
            }
            .sortedBy { it.name }
    }

    /**
     * Refresh app cache (call when apps are installed/uninstalled).
     */
    fun refreshAppsCache() {
        appInfoCache.invalidateCache()
    }

    /**
     * Check if an app is a system app that should be filtered out.
     */
    private fun isSystemApp(packageName: String): Boolean {
        // Don't filter Google/Android apps as they can be locked
        val systemPackagesToFilter = setOf(
            "android",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.inputmethod",
            "com.android.phone",
            "com.android.contacts",
            "com.android.dialer",
            "com.android.settings"
        )
        
        return systemPackagesToFilter.any { packageName.startsWith(it) }
    }

    /**
     * Get browser apps specifically.
     */
    fun getBrowserApps(): List<AppInfo> {
        val browserPackages = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev", 
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.opera.browser.beta",
            "com.opera.mini.native",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.kiwibrowser.browser",
            "com.sec.android.app.sbrowser",
            "com.UCMobile.intl",
            "org.mozilla.focus",
            "com.yandex.browser"
        )
        
        return getInstallableApps().filter { 
            browserPackages.contains(it.packageName) 
        }
    }

    /**
     * Get intent to launch Islamic Text Challenge Test Activity
     */
    fun getIslamicTextChallengeTestIntent(): Intent {
        return IslamicTextChallengeTestActivity.createIntent(context)
    }
}
