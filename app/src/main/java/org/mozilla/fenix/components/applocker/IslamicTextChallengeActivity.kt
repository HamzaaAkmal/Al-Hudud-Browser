/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.mozilla.fenix.R

/**
 * Activity for Islamic Text Typing Challenge
 * Advanced security verification through precise typing of Islamic content
 */
class IslamicTextChallengeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHALLENGE_TYPE = "challenge_type"
        const val TYPE_APP_LOCKER_DISABLE = "app_locker_disable"
        const val TYPE_DEVICE_ADMIN_DISABLE = "device_admin_disable"
        const val CHALLENGE_TIME_LIMIT = 120000L // 2 minutes in milliseconds
        const val RESULT_CHALLENGE_SUCCESS = Activity.RESULT_OK
        const val RESULT_CHALLENGE_FAILED = Activity.RESULT_CANCELED

        fun createIntent(context: Context, challengeType: String): Intent {
            return Intent(context, IslamicTextChallengeActivity::class.java).apply {
                putExtra(EXTRA_CHALLENGE_TYPE, challengeType)
            }
        }
    }

    private lateinit var challengeTitleText: TextView
    private lateinit var instructionsText: TextView
    private lateinit var referenceText: TextView
    private lateinit var inputText: EditText
    private lateinit var timerText: TextView
    private lateinit var characterCountText: TextView
    private lateinit var startTypingButton: Button
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button

    private var currentChallenge: IslamicTextChallenge? = null
    private var challengeTimer: CountDownTimer? = null
    private var isChallengeStarted = false
    private var challengeType: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_islamic_text_challenge)

        challengeType = intent.getStringExtra(EXTRA_CHALLENGE_TYPE) ?: TYPE_APP_LOCKER_DISABLE
        
        initializeViews()
        setupChallenge()
        setupClickListeners()
        setupTextWatcher()
        setupBackPressedHandler()
    }

    private fun initializeViews() {
        challengeTitleText = findViewById(R.id.challenge_title)
        instructionsText = findViewById(R.id.instructions_text)
        referenceText = findViewById(R.id.reference_text)
        inputText = findViewById(R.id.input_text)
        timerText = findViewById(R.id.timer_text)
        characterCountText = findViewById(R.id.character_count)
        startTypingButton = findViewById(R.id.start_typing_button)
        confirmButton = findViewById(R.id.confirm_button)
        cancelButton = findViewById(R.id.cancel_button)
    }

    private fun setupChallenge() {
        // Set title based on challenge type
        val title = when (challengeType) {
            TYPE_APP_LOCKER_DISABLE -> "Security Verification Required - App Locker"
            TYPE_DEVICE_ADMIN_DISABLE -> "Security Verification Required - Device Admin"
            else -> "Security Verification Required"
        }
        challengeTitleText.text = title

        // Set instructions
        instructionsText.text = """
            To proceed with disabling protection, you must complete this Islamic text typing challenge:
            
            • Type the exact paragraph shown below (including all punctuation)
            • Purple highlighted words require special attention
            • You have exactly 2 minutes to complete
            • Any character mismatch will result in failure
            • Click 'Start Typing' to begin the timer
        """.trimIndent()

        // Get random challenge
        currentChallenge = IslamicTextChallengeDatabase.getRandomChallenge()
        currentChallenge?.let { challenge ->
            // Display reference text with purple highlights
            displayReferenceTextWithHighlights(challenge)
            
            // Initialize timer display
            timerText.text = "02:00"
            timerText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            
            // Initialize character count
            updateCharacterCount()
        }

        // Initially disable input and confirm button
        inputText.isEnabled = false
        confirmButton.isEnabled = false
    }

    private fun displayReferenceTextWithHighlights(challenge: IslamicTextChallenge) {
        val spannable = SpannableString(challenge.text)
        
        // Highlight each word in purple
        challenge.highlightedWords.forEach { word ->
            var startIndex = 0
            while (startIndex < challenge.text.length) {
                val index = challenge.text.indexOf(word, startIndex, ignoreCase = true)
                if (index == -1) break
                
                // Check if it's a whole word (not part of another word)
                val isWholeWord = (index == 0 || !challenge.text[index - 1].isLetter()) &&
                                 (index + word.length >= challenge.text.length || !challenge.text[index + word.length].isLetter())
                
                if (isWholeWord) {
                    spannable.setSpan(
                        BackgroundColorSpan(Color.parseColor("#9C27B0")), // Purple color
                        index,
                        index + word.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                startIndex = index + word.length
            }
        }
        
        referenceText.text = spannable
    }

    private fun setupClickListeners() {
        startTypingButton.setOnClickListener {
            startChallenge()
        }

        confirmButton.setOnClickListener {
            verifyAndConfirm()
        }

        cancelButton.setOnClickListener {
            cancelChallenge()
        }
    }

    private fun setupTextWatcher() {
        inputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateCharacterCount()
                updateConfirmButtonState()
            }
        })
    }

    private fun startChallenge() {
        isChallengeStarted = true
        startTypingButton.isEnabled = false
        inputText.isEnabled = true
        inputText.requestFocus()
        
        // Show keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(inputText, InputMethodManager.SHOW_IMPLICIT)

        // Start 2-minute countdown timer
        challengeTimer = object : CountDownTimer(CHALLENGE_TIME_LIMIT, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                timerText.text = String.format("%02d:%02d", minutes, seconds)
                
                // Change color as time decreases
                when {
                    millisUntilFinished > 60000 -> {
                        timerText.setTextColor(ContextCompat.getColor(this@IslamicTextChallengeActivity, android.R.color.holo_green_dark))
                    }
                    millisUntilFinished > 30000 -> {
                        timerText.setTextColor(ContextCompat.getColor(this@IslamicTextChallengeActivity, android.R.color.holo_orange_dark))
                    }
                    else -> {
                        timerText.setTextColor(ContextCompat.getColor(this@IslamicTextChallengeActivity, android.R.color.holo_red_dark))
                    }
                }
            }

            override fun onFinish() {
                onChallengeTimeout()
            }
        }.start()
    }

    private fun updateCharacterCount() {
        val currentLength = inputText.text.length
        val targetLength = currentChallenge?.text?.length ?: 0
        characterCountText.text = "$currentLength / $targetLength characters"
    }

    private fun updateConfirmButtonState() {
        val hasText = inputText.text.isNotEmpty()
        confirmButton.isEnabled = isChallengeStarted && hasText
    }

    private fun verifyAndConfirm() {
        val userInput = inputText.text.toString()
        val referenceText = currentChallenge?.text ?: ""

        val verification = IslamicTextVerifier.getDetailedVerification(userInput, referenceText)
        
        if (verification.isExactMatch) {
            // Perfect match - challenge successful
            onChallengeSuccess()
        } else {
            // Text mismatch - challenge failed with detailed feedback
            val errorMessage = "Text verification failed!\n\n" +
                    "Accuracy: ${String.format("%.1f", verification.accuracy)}%\n" +
                    "Matched: ${verification.matchedCharacters}/${verification.totalCharacters} characters\n\n" +
                    "Error: ${verification.errorDetails}"
            onChallengeFailed(errorMessage)
        }
    }

    private fun onChallengeSuccess() {
        challengeTimer?.cancel()
        Toast.makeText(this, "Challenge completed successfully!", Toast.LENGTH_SHORT).show()
        
        setResult(RESULT_CHALLENGE_SUCCESS)
        finish()
    }

    private fun onChallengeFailed(reason: String) {
        challengeTimer?.cancel()
        Toast.makeText(this, "Challenge failed: $reason", Toast.LENGTH_LONG).show()
        
        setResult(RESULT_CHALLENGE_FAILED)
        finish()
    }

    private fun onChallengeTimeout() {
        Toast.makeText(this, "Sorry, time expired. Challenge failed.", Toast.LENGTH_LONG).show()
        
        setResult(RESULT_CHALLENGE_FAILED)
        finish()
    }

    private fun cancelChallenge() {
        challengeTimer?.cancel()
        setResult(RESULT_CHALLENGE_FAILED)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        challengeTimer?.cancel()
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Prevent back button during challenge
                if (isChallengeStarted) {
                    Toast.makeText(this@IslamicTextChallengeActivity, "Please complete the challenge or click Cancel", Toast.LENGTH_SHORT).show()
                } else {
                    finish()
                }
            }
        })
    }
}
