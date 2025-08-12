/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages temporary unlock sessions for protected apps.
 * Handles app foreground/background state and session timeouts.
 */
class UnlockSessionManager {

    companion object {
        private const val SESSION_TIMEOUT = 30_000L // 30 seconds
    }

    private val unlockedApps = ConcurrentHashMap<String, Long>()
    private val foregroundApps = ConcurrentHashMap<String, Boolean>()
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Unlock an app temporarily.
     */
    fun unlockApp(packageName: String) {
        val currentTime = System.currentTimeMillis()
        unlockedApps[packageName] = currentTime
        foregroundApps[packageName] = true

        // Schedule automatic lock after timeout
        handler.postDelayed({
            lockApp(packageName)
        }, SESSION_TIMEOUT)
    }

    /**
     * Lock an app (remove from unlocked state).
     */
    fun lockApp(packageName: String) {
        unlockedApps.remove(packageName)
        foregroundApps.remove(packageName)
    }

    /**
     * Check if an app is temporarily unlocked.
     */
    fun isTemporarilyUnlocked(packageName: String): Boolean {
        val unlockTime = unlockedApps[packageName] ?: return false
        val currentTime = System.currentTimeMillis()
        
        // Check if session has expired
        if (currentTime - unlockTime > SESSION_TIMEOUT) {
            lockApp(packageName)
            return false
        }
        
        return true
    }

    /**
     * Mark app as being in foreground.
     */
    fun markAppInForeground(packageName: String) {
        if (unlockedApps.containsKey(packageName)) {
            foregroundApps[packageName] = true
            // Extend session when app is actively used
            unlockedApps[packageName] = System.currentTimeMillis()
        }
    }

    /**
     * Mark app as being in background.
     */
    fun markAppInBackground(packageName: String) {
        foregroundApps[packageName] = false
    }

    /**
     * Clear all unlock sessions (e.g., when screen turns off).
     */
    fun clearAllSessions() {
        unlockedApps.clear()
        foregroundApps.clear()
    }

    /**
     * Get all currently unlocked apps.
     */
    fun getUnlockedApps(): Set<String> {
        return unlockedApps.keys.toSet()
    }
}
