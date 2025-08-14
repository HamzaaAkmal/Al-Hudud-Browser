/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap

/**
 * Optimized session manager for App Locker to reduce lag and improve performance.
 */
class OptimizedUnlockSessionManager {
    
    companion object {
        private const val SESSION_TIMEOUT_MS = 300_000L // 5 minutes
        private const val CLEANUP_INTERVAL_MS = 60_000L // 1 minute cleanup
    }

    private val unlockedApps = ConcurrentHashMap<String, Long>()
    private val foregroundApps = ConcurrentHashMap<String, Long>()
    private val handler = Handler(Looper.getMainLooper())
    
    private val cleanupRunnable = object : Runnable {
        override fun run() {
            cleanupExpiredSessions()
            handler.postDelayed(this, CLEANUP_INTERVAL_MS)
        }
    }

    init {
        // Start periodic cleanup
        handler.post(cleanupRunnable)
    }

    /**
     * Mark an app as temporarily unlocked.
     */
    fun unlockApp(packageName: String) {
        val currentTime = System.currentTimeMillis()
        unlockedApps[packageName] = currentTime
        foregroundApps[packageName] = currentTime
    }

    /**
     * Check if an app is temporarily unlocked.
     */
    fun isTemporarilyUnlocked(packageName: String): Boolean {
        val unlockTime = unlockedApps[packageName] ?: return false
        val currentTime = System.currentTimeMillis()
        
        return if (currentTime - unlockTime < SESSION_TIMEOUT_MS) {
            // Update last access time
            foregroundApps[packageName] = currentTime
            true
        } else {
            // Session expired
            unlockedApps.remove(packageName)
            foregroundApps.remove(packageName)
            false
        }
    }

    /**
     * Mark an app as in foreground (extends session).
     */
    fun markAppInForeground(packageName: String) {
        if (unlockedApps.containsKey(packageName)) {
            foregroundApps[packageName] = System.currentTimeMillis()
        }
    }

    /**
     * Clear all unlock sessions (e.g., on screen off).
     */
    fun clearAllSessions() {
        unlockedApps.clear()
        foregroundApps.clear()
    }

    /**
     * Clean up expired sessions to prevent memory leaks.
     */
    private fun cleanupExpiredSessions() {
        val currentTime = System.currentTimeMillis()
        val expiredApps = mutableListOf<String>()

        unlockedApps.forEach { (packageName, unlockTime) ->
            if (currentTime - unlockTime > SESSION_TIMEOUT_MS) {
                expiredApps.add(packageName)
            }
        }

        expiredApps.forEach { packageName ->
            unlockedApps.remove(packageName)
            foregroundApps.remove(packageName)
        }
    }

    /**
     * Get session info for debugging.
     */
    fun getSessionInfo(): Map<String, Long> {
        return unlockedApps.toMap()
    }

    /**
     * Cleanup resources.
     */
    fun destroy() {
        handler.removeCallbacks(cleanupRunnable)
        clearAllSessions()
    }
}

/**
 * Optimized app info cache to reduce loading times.
 */
class AppInfoCache {
    
    companion object {
        private const val CACHE_EXPIRY_MS = 600_000L // 10 minutes
    }

    private var cachedApps: List<AppInfo>? = null
    private var lastCacheTime: Long = 0

    /**
     * Get cached apps or refresh if expired.
     */
    fun getCachedApps(refreshFunction: () -> List<AppInfo>): List<AppInfo> {
        val currentTime = System.currentTimeMillis()
        
        return if (cachedApps == null || currentTime - lastCacheTime > CACHE_EXPIRY_MS) {
            val apps = refreshFunction()
            cachedApps = apps
            lastCacheTime = currentTime
            apps
        } else {
            cachedApps!!
        }
    }

    /**
     * Invalidate cache to force refresh.
     */
    fun invalidateCache() {
        cachedApps = null
        lastCacheTime = 0
    }
}
