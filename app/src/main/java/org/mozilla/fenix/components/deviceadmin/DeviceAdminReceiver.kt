/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.deviceadmin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device admin receiver for preventing uninstall of the browser.
 * This receiver only handles basic device admin events and does not
 * request any additional permissions beyond uninstall prevention.
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // Device admin protection is now enabled
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // Device admin protection is now disabled
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        // Return a message to show when user tries to disable device admin
        return "Disabling device admin protection will allow the browser to be uninstalled without extra confirmation."
    }
}
