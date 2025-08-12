/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.theme.FirefoxTheme

/**
 * Lock screen activity for App Locker feature.
 * Shows PIN input screen when a protected app is launched.
 */
class AppLockScreenActivity : ComponentActivity() {

    private var targetPackageName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPackageName = intent.getStringExtra("target_package") ?: run {
            finish()
            return
        }

        // Make it fullscreen and secure
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        setFinishOnTouchOutside(false)

        setContent {
            FirefoxTheme {
                LockScreenUI(
                    onPinSubmit = { enteredPin ->
                        handlePinVerification(enteredPin)
                    },
                    onCancel = {
                        finishAffinity()
                    }
                )
            }
        }

        // Disable back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(enabled = true) {
            override fun handleOnBackPressed() {
                // Do nothing - prevent going back
            }
        })
    }

    private fun handlePinVerification(enteredPin: String) {
        val storedPin = settings().appLockerMasterPin
        
        if (storedPin.isNotEmpty() && PasswordHasher.verifyPin(enteredPin, storedPin)) {
            // PIN is correct - unlock the app
            val unlockIntent = Intent(AppLockAccessibilityService.ACTION_UNLOCK_SUCCESS).apply {
                putExtra("package_name", targetPackageName)
            }
            sendBroadcast(unlockIntent)
            
            // Launch the target app
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
            finishAffinity()
        } else {
            Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun LockScreenUI(
    onPinSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "App Locked",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Enter your PIN to unlock",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = pin,
                    onValueChange = { newPin ->
                        if (newPin.length <= 4 && newPin.all { it.isDigit() }) {
                            pin = newPin
                            if (newPin.length == 4) {
                                onPinSubmit(newPin)
                            }
                        }
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = { onPinSubmit(pin) },
                        enabled = pin.length == 4,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Unlock")
                    }
                }
            }
        }
    }
}
