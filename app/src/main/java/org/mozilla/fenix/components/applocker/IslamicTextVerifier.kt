/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

/**
 * Utility class for Islamic Text Challenge verification
 */
object IslamicTextVerifier {

    /**
     * Verify if user input matches reference text exactly
     * @param userInput The text typed by the user
     * @param referenceText The reference text to match against
     * @return true if texts match exactly, false otherwise
     */
    fun verifyExactMatch(userInput: String, referenceText: String): Boolean {
        return userInput == referenceText
    }

    /**
     * Get character-by-character difference analysis
     * @param userInput The text typed by the user
     * @param referenceText The reference text to match against
     * @return Pair of (matched characters, total characters)
     */
    fun getMatchAnalysis(userInput: String, referenceText: String): Pair<Int, Int> {
        val minLength = minOf(userInput.length, referenceText.length)
        var matchedChars = 0

        for (i in 0 until minLength) {
            if (userInput[i] == referenceText[i]) {
                matchedChars++
            }
        }

        return Pair(matchedChars, referenceText.length)
    }

    /**
     * Get detailed verification result with error information
     * @param userInput The text typed by the user
     * @param referenceText The reference text to match against
     * @return VerificationResult with details
     */
    fun getDetailedVerification(userInput: String, referenceText: String): VerificationResult {
        val isExactMatch = verifyExactMatch(userInput, referenceText)
        val (matched, total) = getMatchAnalysis(userInput, referenceText)
        val accuracy = if (total > 0) (matched.toFloat() / total.toFloat()) * 100 else 0f

        val errorDetails = if (!isExactMatch) {
            buildString {
                if (userInput.length != referenceText.length) {
                    append("Length mismatch: ${userInput.length} vs ${referenceText.length}. ")
                }
                
                // Find first difference
                val minLength = minOf(userInput.length, referenceText.length)
                for (i in 0 until minLength) {
                    if (userInput[i] != referenceText[i]) {
                        append("First difference at position ${i + 1}: '${userInput[i]}' vs '${referenceText[i]}'. ")
                        break
                    }
                }
            }
        } else {
            "Perfect match!"
        }

        return VerificationResult(
            isExactMatch = isExactMatch,
            matchedCharacters = matched,
            totalCharacters = total,
            accuracy = accuracy,
            errorDetails = errorDetails
        )
    }

    /**
     * Data class for verification results
     */
    data class VerificationResult(
        val isExactMatch: Boolean,
        val matchedCharacters: Int,
        val totalCharacters: Int,
        val accuracy: Float,
        val errorDetails: String
    )
}
