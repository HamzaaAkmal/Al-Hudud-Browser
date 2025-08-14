/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for Islamic Text Challenge implementation
 */
class IslamicTextChallengeTest {

    @Test
    fun testChallengeDatabaseIntegrity() {
        val challenges = IslamicTextChallengeDatabase.getAllChallenges()
        
        // Verify we have required number of challenges
        assertTrue("Should have at least 10 challenges", challenges.size >= 10)
        
        // Verify each challenge has required components
        challenges.forEach { challenge ->
            assertNotNull("Challenge should have title", challenge.title)
            assertNotNull("Challenge should have text", challenge.text)
            assertNotNull("Challenge should have highlighted words", challenge.highlightedWords)
            
            assertTrue("Challenge text should not be empty", challenge.text.isNotEmpty())
            assertTrue("Challenge should have highlighted words", challenge.highlightedWords.isNotEmpty())
            
            // Verify Kalma translation is present
            assertTrue(
                "Challenge should contain Kalma translation", 
                challenge.text.contains("La ilaha illallahu Muhammadur rasulullah") &&
                challenge.text.contains("There is no deity except Allah, Muhammad is the messenger of Allah")
            )
            
            // Verify text complexity (should be reasonably long)
            assertTrue("Challenge text should be complex (>200 chars)", challenge.text.length > 200)
            
            // Verify highlighted words are actually in the text
            challenge.highlightedWords.forEach { word ->
                assertTrue(
                    "Highlighted word '$word' should exist in text",
                    challenge.text.contains(word, ignoreCase = true)
                )
            }
        }
    }

    @Test
    fun testTextVerifierExactMatch() {
        val referenceText = "This is a test text with punctuation, and symbols!"
        
        // Test exact match
        assertTrue(
            "Exact match should return true",
            IslamicTextVerifier.verifyExactMatch(referenceText, referenceText)
        )
        
        // Test mismatch
        assertFalse(
            "Different text should return false",
            IslamicTextVerifier.verifyExactMatch("Different text", referenceText)
        )
        
        // Test case sensitivity
        assertFalse(
            "Case mismatch should return false",
            IslamicTextVerifier.verifyExactMatch(referenceText.lowercase(), referenceText)
        )
        
        // Test punctuation sensitivity
        assertFalse(
            "Punctuation mismatch should return false",
            IslamicTextVerifier.verifyExactMatch(referenceText.replace("!", "."), referenceText)
        )
    }

    @Test
    fun testTextVerifierAnalysis() {
        val referenceText = "Hello world"
        val userInput = "Hello worlD"
        
        val (matched, total) = IslamicTextVerifier.getMatchAnalysis(userInput, referenceText)
        
        assertEquals("Total should match reference length", referenceText.length, total)
        assertEquals("Should match 10 out of 11 characters", 10, matched)
    }

    @Test
    fun testDetailedVerification() {
        val referenceText = "Test text"
        val perfectMatch = "Test text"
        val imperfectMatch = "test text"
        
        // Test perfect match
        val perfectResult = IslamicTextVerifier.getDetailedVerification(perfectMatch, referenceText)
        assertTrue("Perfect match should be exact", perfectResult.isExactMatch)
        assertEquals("Perfect match should be 100%", 100f, perfectResult.accuracy, 0.1f)
        
        // Test imperfect match
        val imperfectResult = IslamicTextVerifier.getDetailedVerification(imperfectMatch, referenceText)
        assertFalse("Imperfect match should not be exact", imperfectResult.isExactMatch)
        assertTrue("Imperfect match should have lower accuracy", imperfectResult.accuracy < 100f)
    }

    @Test
    fun testRandomChallengeGeneration() {
        val challenge1 = IslamicTextChallengeDatabase.getRandomChallenge()
        val challenge2 = IslamicTextChallengeDatabase.getRandomChallenge()
        
        assertNotNull("Random challenge should not be null", challenge1)
        assertNotNull("Random challenge should not be null", challenge2)
        
        // Note: They might be the same due to randomness, but both should be valid
        assertTrue("Challenge 1 should have content", challenge1.text.isNotEmpty())
        assertTrue("Challenge 2 should have content", challenge2.text.isNotEmpty())
    }

    @Test
    fun testChallengeById() {
        val challenge = IslamicTextChallengeDatabase.getChallengeById(1)
        
        assertNotNull("Challenge with ID 1 should exist", challenge)
        assertEquals("Challenge ID should match", 1, challenge?.id)
        
        val nonExistentChallenge = IslamicTextChallengeDatabase.getChallengeById(999)
        assertNull("Non-existent challenge should return null", nonExistentChallenge)
    }

    @Test
    fun testHighlightedWordsComplexity() {
        val challenges = IslamicTextChallengeDatabase.getAllChallenges()
        
        challenges.forEach { challenge ->
            // Verify minimum number of highlighted words
            assertTrue(
                "Challenge '${challenge.title}' should have at least 10 highlighted words",
                challenge.highlightedWords.size >= 10
            )
            
            // Verify highlighted words are reasonably complex (length > 6)
            val complexWords = challenge.highlightedWords.filter { it.length > 6 }
            assertTrue(
                "Challenge '${challenge.title}' should have complex highlighted words",
                complexWords.size >= 5
            )
        }
    }
}
