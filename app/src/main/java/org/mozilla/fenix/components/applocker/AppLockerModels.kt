/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import android.graphics.drawable.Drawable

/**
 * Data class representing an application for the App Locker feature.
 */
data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable?,
    val isLocked: Boolean = false
)

/**
 * Data class representing a locked app entry in the database.
 */
data class LockedApp(
    val packageName: String,
    val pin: String,
    val appName: String = "",
    val isEnabled: Boolean = true
)
