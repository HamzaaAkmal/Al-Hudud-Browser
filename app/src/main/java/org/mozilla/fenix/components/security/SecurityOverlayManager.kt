/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.mozilla.fenix.R

/**
 * Manages security overlay screens that block unauthorized access to system settings.
 * This overlay prevents users from disabling browser security features through Android settings.
 */
class SecurityOverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "SecurityOverlayManager"
        private const val KEYWORD_COUNTDOWN_SECONDS = 15
        private const val FIX_GRACE_PERIOD_SECONDS = 15
    }

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var overlayView: View? = null
    private var isOverlayVisible = false
    
    // Timer-related variables for keyword protection
    private var countdownHandler: Handler? = null
    private var countdownRunnable: Runnable? = null
    private var currentCountdown = KEYWORD_COUNTDOWN_SECONDS
    private var detectedPackageName: String? = null
    
    // Grace period management
    private var isInGracePeriod = false
    private var gracePeriodHandler: Handler? = null
    private var gracePeriodRunnable: Runnable? = null

    /**
     * Show accessibility service protection overlay.
     * Blocks access to disable accessibility service through settings.
     */
    fun showAccessibilityProtectionOverlay() {
        showSecurityOverlay(
            iconRes = R.drawable.ic_warning_amber_24,
            title = "⚠️ ACTION NOT ALLOWED",
            message = """For safety and security purposes, 
this action has been blocked.

First disable App Locker protection 
from the browser to modify 
security settings.""",
            buttonText = "Go Back to Home"
        )
    }

    /**
     * Show device admin protection overlay.
     * Blocks access to disable device admin through settings.
     */
    fun showDeviceAdminProtectionOverlay() {
        showSecurityOverlay(
            iconRes = R.drawable.ic_lock_24,
            title = "🔒 SECURITY PROTECTION ACTIVE",
            message = """Device Administrator settings 
cannot be modified directly.

To modify security settings:
1. Open the browser
2. Go to Privacy & Security
3. Disable App Locker Protection first
4. Then modify Device Admin settings

This protects your device from 
unauthorized security changes.""",
            buttonText = "Go Back to Home"
        )
    }

    /**
     * Show app info protection overlay.
     * Blocks force stop, disable, or uninstall through app info.
     */
    fun showAppInfoProtectionOverlay() {
        showSecurityOverlay(
            iconRes = R.drawable.ic_warning_amber_24,
            title = "⚠️ ACTION NOT ALLOWED",
            message = """You cannot stop or uninstall this app 
directly from settings.

Open the browser → Privacy & Security 
to disable protection first.""",
            buttonText = "Go Back to Home"
        )
    }

    /**
     * Show overlay permission protection overlay.
     * Blocks disabling "draw over other apps" permission.
     */
    fun showOverlayPermissionProtectionOverlay() {
        showSecurityOverlay(
            iconRes = R.drawable.ic_lock_24,
            title = "🔒 PROTECTED FEATURE",
            message = """Drawing over other apps is required 
for your security.

Disable protection in the browser first 
to modify this setting.""",
            buttonText = "Go Back to Home"
        )
    }

    /**
     * Show smart keyword protection overlay with countdown timer.
     * Gives user 15 seconds to fix the issue before blocking the app.
     */
    fun showKeywordProtectionOverlay(packageName: String) {
        // Skip showing overlay if we're in grace period
        if (isInGracePeriod) {
            Log.d(TAG, "In grace period, not showing overlay")
            return
        }
        
        if (isOverlayVisible) {
            Log.d(TAG, "Overlay already visible, ignoring request")
            return
        }
        
        detectedPackageName = packageName
        currentCountdown = KEYWORD_COUNTDOWN_SECONDS
        
        showSmartKeywordOverlay(packageName)
    }

    /**
     * Show the smart overlay with countdown timer and fix button.
     */
    @SuppressLint("InflateParams")
    private fun showSmartKeywordOverlay(packageName: String) {
        try {
            if (isOverlayVisible) {
                hideOverlay()
            }

            val layoutInflater = LayoutInflater.from(context)
            overlayView = layoutInflater.inflate(R.layout.keyword_protection_overlay_layout, null)
            
            val appName = getAppName(packageName)
            
            // Setup views
            val countdownTimer = overlayView?.findViewById<TextView>(R.id.countdown_timer)
            val appInfo = overlayView?.findViewById<TextView>(R.id.app_info)
            val fixNowButton = overlayView?.findViewById<Button>(R.id.fix_now_button)
            
            appInfo?.text = "App: $appName"
            countdownTimer?.text = currentCountdown.toString()
            
            // Setup fix button
            fixNowButton?.setOnClickListener {
                Log.d(TAG, "Fix Now button clicked, temporarily hiding overlay")
                temporarilyHideOverlay()
            }
            
            // Add overlay to window
            val layoutParams = createWindowLayoutParams()
            windowManager.addView(overlayView, layoutParams)
            isOverlayVisible = true
            
            Log.d(TAG, "Smart keyword protection overlay shown for $appName")
            
            // Start countdown timer
            startCountdownTimer()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing smart keyword protection overlay", e)
        }
    }

    /**
     * Start the countdown timer for keyword protection.
     */
    private fun startCountdownTimer() {
        stopCountdownTimer() // Stop any existing timer
        
        countdownHandler = Handler(Looper.getMainLooper())
        countdownRunnable = object : Runnable {
            override fun run() {
                if (currentCountdown > 0) {
                    // Update timer display
                    overlayView?.findViewById<TextView>(R.id.countdown_timer)?.text = currentCountdown.toString()
                    currentCountdown--
                    
                    // Schedule next update
                    countdownHandler?.postDelayed(this, 1000)
                } else {
                    // Timer expired - force close app and redirect
                    Log.w(TAG, "Countdown timer expired, force closing app")
                    forceCloseAndRedirect()
                }
            }
        }
        
        countdownHandler?.post(countdownRunnable!!)
    }

    /**
     * Stop the countdown timer.
     */
    private fun stopCountdownTimer() {
        countdownRunnable?.let { runnable ->
            countdownHandler?.removeCallbacks(runnable)
        }
        countdownHandler = null
        countdownRunnable = null
    }

    /**
     * Temporarily hide overlay to let user fix the keyword.
     * Starts a 15-second grace period during which no overlay will be shown.
     */
    private fun temporarilyHideOverlay() {
        Log.d(TAG, "Fix Now button clicked, starting ${FIX_GRACE_PERIOD_SECONDS}s grace period")
        hideOverlay()
        startGracePeriod()
    }
    
    /**
     * Start the grace period during which overlays are suppressed.
     */
    private fun startGracePeriod() {
        stopGracePeriod() // Stop any existing grace period
        
        isInGracePeriod = true
        gracePeriodHandler = Handler(Looper.getMainLooper())
        gracePeriodRunnable = Runnable {
            Log.d(TAG, "Grace period ended, resuming keyword monitoring")
            isInGracePeriod = false
            
            // After grace period, check if keywords are still present
            detectedPackageName?.let { packageName ->
                checkForKeywordsAfterGracePeriod(packageName)
            }
        }
        
        gracePeriodHandler?.postDelayed(gracePeriodRunnable!!, (FIX_GRACE_PERIOD_SECONDS * 1000).toLong())
        Log.d(TAG, "Grace period started for ${FIX_GRACE_PERIOD_SECONDS} seconds")
    }
    
    /**
     * Stop the grace period timer.
     */
    private fun stopGracePeriod() {
        gracePeriodRunnable?.let { runnable ->
            gracePeriodHandler?.removeCallbacks(runnable)
        }
        gracePeriodHandler = null
        gracePeriodRunnable = null
        isInGracePeriod = false
    }
    
    /**
     * Check for keywords after grace period ends.
     * If keywords are still present, show overlay again.
     */
    private fun checkForKeywordsAfterGracePeriod(packageName: String) {
        Log.d(TAG, "Checking for keywords after grace period for $packageName")
        
        // Send broadcast to accessibility service to perform keyword check
        val intent = Intent("org.mozilla.fenix.CHECK_KEYWORDS_AFTER_GRACE")
        intent.putExtra("package_name", packageName)
        context.sendBroadcast(intent)
    }

    /**
     * Force close the app and redirect to home when timer expires.
     */
    private fun forceCloseAndRedirect() {
        hideOverlay()
        
        // Navigate to home
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
            
            Log.d(TAG, "Forced navigation to home due to keyword protection timeout")
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to home", e)
        }
    }

    /**
     * Get a user-friendly app name from package name.
     */
    private fun getAppName(packageName: String): String {
        return when (packageName) {
            "com.facebook.katana" -> "Facebook"
            "com.instagram.android" -> "Instagram"
            "com.google.android.youtube" -> "YouTube"
            "com.google.android.apps.chrome", "com.android.chrome" -> "Chrome"
            "com.twitter.android" -> "Twitter"
            "com.whatsapp" -> "WhatsApp"
            "com.snapchat.android" -> "Snapchat"
            "com.tiktok" -> "TikTok"
            "com.reddit.frontpage" -> "Reddit"
            "org.telegram.messenger" -> "Telegram"
            "com.discord" -> "Discord"
            "com.pinterest" -> "Pinterest"
            "com.tumblr" -> "Tumblr"
            "com.opera.browser" -> "Opera"
            "org.mozilla.firefox" -> "Firefox"
            "com.microsoft.emmx" -> "Edge"
            "com.brave.browser" -> "Brave"
            "com.duckduckgo.mobile.android" -> "DuckDuckGo"
            else -> {
                try {
                    val packageManager = context.packageManager
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    "this app"
                }
            }
        }
    }

    /**
     * Show the security overlay with specified content.
     */
    @SuppressLint("InflateParams")
    private fun showSecurityOverlay(
        iconRes: Int,
        title: String,
        message: String,
        buttonText: String
    ) {
        if (isOverlayVisible) {
            hideOverlay()
        }

        try {
            // Create overlay view
            val inflater = LayoutInflater.from(context)
            overlayView = inflater.inflate(R.layout.security_overlay_layout, null)

            // Configure overlay content
            setupOverlayContent(overlayView!!, iconRes, title, message, buttonText)

            // Setup window parameters
            val layoutParams = createWindowLayoutParams()

            // Show overlay
            windowManager.addView(overlayView, layoutParams)
            isOverlayVisible = true

            // Add fade-in animation
            overlayView?.alpha = 0f
            overlayView?.animate()
                ?.alpha(1f)
                ?.setDuration(300)
                ?.start()

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to show security overlay", e)
        }
    }

    /**
     * Configure the overlay content with specified values.
     */
    private fun setupOverlayContent(
        view: View,
        iconRes: Int,
        title: String,
        message: String,
        buttonText: String
    ) {
        // Setup icon
        view.findViewById<ImageView>(R.id.security_icon)?.setImageResource(iconRes)

        // Setup title
        view.findViewById<TextView>(R.id.security_title)?.text = title

        // Setup message
        view.findViewById<TextView>(R.id.security_message)?.text = message

        // Setup button
        view.findViewById<Button>(R.id.security_button)?.apply {
            text = buttonText
            setOnClickListener {
                hideOverlay()
                goBackToHome()
            }
        }

        // Block background touches
        view.setOnTouchListener { _, _ -> true }
    }

    /**
     * Create window layout parameters for the overlay.
     */
    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        val layoutParams = WindowManager.LayoutParams()

        // Use appropriate window type based on Android version
        layoutParams.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        // Configure overlay behavior
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        // Set overlay properties
        layoutParams.format = PixelFormat.TRANSLUCENT
        layoutParams.gravity = Gravity.CENTER
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT

        return layoutParams
    }

    /**
     * Hide the security overlay.
     */
    fun hideOverlay() {
        if (isOverlayVisible && overlayView != null) {
            try {
                // Stop countdown timer if running
                stopCountdownTimer()
                
                windowManager.removeView(overlayView)
                overlayView = null
                isOverlayVisible = false
                // Don't reset detectedPackageName here - we might need it for grace period checks
                
                Log.d(TAG, "Security overlay hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide security overlay", e)
            }
        }
    }

    /**
     * Navigate back to home screen and clear Settings from recents.
     */
    private fun goBackToHome() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to navigate to home", e)
        }
    }

    /**
     * Check if overlay is currently visible.
     */
    fun isOverlayVisible(): Boolean = isOverlayVisible
    
    /**
     * Check if currently in grace period.
     */
    fun isInGracePeriod(): Boolean = isInGracePeriod

    /**
     * Clean up resources.
     */
    fun destroy() {
        stopGracePeriod()
        hideOverlay()
        detectedPackageName = null
    }
}
