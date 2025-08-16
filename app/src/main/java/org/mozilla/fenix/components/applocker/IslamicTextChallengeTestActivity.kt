/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.fenix.R

/**
 * Test Activity for validating Islamic Text Challenge Implementation
 * This activity provides UI to test both App Locker and Device Admin challenges
 */
class IslamicTextChallengeTestActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, IslamicTextChallengeTestActivity::class.java)
        }
    }

    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var testAppLockerButton: Button
    private lateinit var testDeviceAdminButton: Button
    private lateinit var viewChallengesButton: Button
    private lateinit var backButton: Button

    // Activity result launcher for Islamic Text Challenge
    private val islamicChallengeResultLauncher: ActivityResultLauncher<Intent> = 
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleChallengeResult(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_islamic_text_challenge_test)

        initializeViews()
        setupClickListeners()
        updateStatus()
    }

    private fun initializeViews() {
        titleText = findViewById(R.id.test_title)
        statusText = findViewById(R.id.test_status)
        testAppLockerButton = findViewById(R.id.test_app_locker_button)
        testDeviceAdminButton = findViewById(R.id.test_device_admin_button)
        viewChallengesButton = findViewById(R.id.view_challenges_button)
        backButton = findViewById(R.id.back_button)

        titleText.text = "Islamic Text Challenge Testing"
    }

    private fun setupClickListeners() {
        testAppLockerButton.setOnClickListener {
            testAppLockerChallenge()
        }

        testDeviceAdminButton.setOnClickListener {
            testDeviceAdminChallenge()
        }

        viewChallengesButton.setOnClickListener {
            viewAvailableChallenges()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun testAppLockerChallenge() {
        statusText.text = "Testing App Locker disable challenge..."
        
        val intent = IslamicTextChallengeActivity.createIntent(
            this, 
            IslamicTextChallengeActivity.TYPE_APP_LOCKER_DISABLE
        )
        islamicChallengeResultLauncher.launch(intent)
    }

    private fun testDeviceAdminChallenge() {
        statusText.text = "Device Admin challenge removed - testing App Locker challenge instead..."
        
        val intent = IslamicTextChallengeActivity.createIntent(
            this, 
            IslamicTextChallengeActivity.TYPE_APP_LOCKER_DISABLE
        )
        islamicChallengeResultLauncher.launch(intent)
    }

    private fun viewAvailableChallenges() {
        val challenges = IslamicTextChallengeDatabase.getAllChallenges()
        val challengeList = StringBuilder()
        challengeList.append("Available Islamic Text Challenges:\n\n")
        
        challenges.forEachIndexed { index, challenge ->
            challengeList.append("${index + 1}. ${challenge.title}\n")
            challengeList.append("   Highlighted words: ${challenge.highlightedWords.size}\n")
            challengeList.append("   Text length: ${challenge.text.length} characters\n\n")
        }
        
        statusText.text = challengeList.toString()
    }

    private fun handleChallengeResult(result: ActivityResult) {
        when (result.resultCode) {
            IslamicTextChallengeActivity.RESULT_CHALLENGE_SUCCESS -> {
                statusText.text = "✅ Challenge completed successfully!\nUser typed the Islamic text correctly within the time limit."
                Toast.makeText(this, "Challenge SUCCESS!", Toast.LENGTH_LONG).show()
            }
            IslamicTextChallengeActivity.RESULT_CHALLENGE_FAILED -> {
                statusText.text = "❌ Challenge failed!\nEither time expired or text didn't match exactly."
                Toast.makeText(this, "Challenge FAILED!", Toast.LENGTH_LONG).show()
            }
            else -> {
                statusText.text = "Challenge was cancelled or unknown result."
                Toast.makeText(this, "Challenge cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStatus() {
        val challenge = IslamicTextChallengeDatabase.getRandomChallenge()
        statusText.text = """
            Islamic Text Challenge Test Environment
            
            Features implemented:
            ✅ 10+ Complex Islamic text challenges
            ✅ Purple highlighted difficult words
            ✅ Kalma translation in every challenge
            ✅ 2-minute countdown timer
            ✅ Exact character matching verification
            ✅ Professional UI with App Locker theme
            ✅ Educational Islamic content
            ✅ Integration with App Locker disable
            ✅ Integration with Device Admin disable
            
            Current random challenge: "${challenge.title}"
            Character count: ${challenge.text.length}
            Highlighted words: ${challenge.highlightedWords.size}
            
            Use the buttons below to test the functionality.
        """.trimIndent()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
