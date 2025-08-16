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
    }

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var overlayView: View? = null
    private var isOverlayVisible = false

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
     * Show keyword protection overlay.
     * Blocks harmful keyword searches detected across apps.
     */
    fun showKeywordProtectionOverlay(packageName: String) {
        val appName = getAppName(packageName)
        showSecurityOverlay(
            iconRes = R.drawable.ic_warning_amber_24,
            title = "⚠️ CONTENT BLOCKED",
            message = """Inappropriate content detected in $appName.

For your safety and spiritual well-being, 
this search has been blocked.

Consider using this time for beneficial 
activities like prayer, learning, or 
helping others.""",
            buttonText = "Return to Home"
        )
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
                windowManager.removeView(overlayView)
                overlayView = null
                isOverlayVisible = false
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to hide security overlay", e)
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
     * Clean up resources.
     */
    fun destroy() {
        hideOverlay()
    }
}
