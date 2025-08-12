/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Secure password hashing utility for App Locker PIN management.
 * Uses PBKDF2 with HMAC-SHA1 for secure PIN storage.
 */
object PasswordHasher {

    private const val ITERATIONS = 1000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    /**
     * Hash a PIN with optional salt for secure storage.
     * @param pin The PIN to hash
     * @param salt Optional salt bytes, generates new salt if not provided
     * @return Base64 encoded string containing salt + hash
     */
    fun hashPin(pin: String, salt: ByteArray = generateSalt()): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(salt + hash, Base64.NO_WRAP)
    }

    /**
     * Verify a PIN against stored hash.
     * @param pin The PIN to verify
     * @param stored The stored hash to verify against
     * @return true if PIN matches, false otherwise
     */
    fun verifyPin(pin: String, stored: String): Boolean {
        return try {
            val decoded = Base64.decode(stored, Base64.NO_WRAP)
            if (decoded.size < SALT_LENGTH) return false
            
            val salt = decoded.copyOfRange(0, SALT_LENGTH)
            val hashOfInput = hashPin(pin, salt)
            stored == hashOfInput
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generate a cryptographically secure random salt.
     * @return ByteArray containing random salt
     */
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }
}
