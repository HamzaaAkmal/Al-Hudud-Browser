/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.applocker

import android.content.Context
import kotlin.random.Random

/**
 * Data class representing an Islamic text challenge
 */
data class IslamicTextChallenge(
    val id: Int,
    val title: String,
    val text: String,
    val highlightedWords: List<String>
)

/**
 * Database of Islamic text challenges for advanced typing security verification
 */
object IslamicTextChallengeDatabase {

    private val challenges = listOf(
        IslamicTextChallenge(
            id = 1,
            title = "Prophet's Migration (Hijra)",
            text = "In the luminous epoch of the Prophet's (PBUH) migration, the Muhajireen demonstrated extraordinary fortitude and unwavering devotion. The Kalma 'La ilaha illallahu Muhammadur rasulullah' means 'There is no deity except Allah, Muhammad is the messenger of Allah.' Their perseverance through tribulations exemplified the quintessential Islamic virtues of patience and submission to the Almighty's divine decree.",
            highlightedWords = listOf("luminous", "epoch", "extraordinary", "fortitude", "unwavering", "devotion", "perseverance", "tribulations", "exemplified", "quintessential", "submission", "Almighty's", "divine", "decree")
        ),
        IslamicTextChallenge(
            id = 2,
            title = "Battle of Badr",
            text = "The momentous Battle of Badr showcased the believers' unwavering faith against overwhelming adversaries. The Kalma 'La ilaha illallahu Muhammadur rasulullah' means 'There is no deity except Allah, Muhammad is the messenger of Allah.' Divine intervention manifested through angelic assistance, demonstrating Allah's sovereignty over all circumstances and His protection of the righteous who maintain steadfast devotion.",
            highlightedWords = listOf("momentous", "showcased", "unwavering", "overwhelming", "adversaries", "intervention", "manifested", "angelic", "sovereignty", "circumstances", "righteous", "steadfast", "devotion")
        ),
        IslamicTextChallenge(
            id = 3,
            title = "Companions' Sacrifice",
            text = "The illustrious Companions exhibited unprecedented sacrifice and dedication to Islamic principles. The Kalma 'La ilaha illallahu Muhammadur rasulullah' means 'There is no deity except Allah, Muhammad is the messenger of Allah.' Their magnanimous character and scrupulous adherence to righteousness established paradigmatic examples of spiritual excellence and unwavering commitment to divine guidance.",
            highlightedWords = listOf("illustrious", "unprecedented", "dedication", "magnanimous", "scrupulous", "adherence", "righteousness", "paradigmatic", "spiritual", "excellence", "unwavering", "commitment", "divine", "guidance")
        ),
        IslamicTextChallenge(
            id = 4,
            title = "Islamic Jurisprudence",
            text = "Islamic jurisprudence encompasses comprehensive methodologies for deriving legal rulings from fundamental sources. The Kalma 'La ilaha illallahu Muhammadur rasulullah' means 'There is no deity except Allah, Muhammad is the messenger of Allah.' Scholarly consensus and analogical reasoning facilitate interpretation of complex contemporary issues while maintaining fidelity to immutable divine principles.",
            highlightedWords = listOf("jurisprudence", "encompasses", "comprehensive", "methodologies", "fundamental", "consensus", "analogical", "facilitate", "interpretation", "contemporary", "fidelity", "immutable", "divine", "principles")
        ),
        IslamicTextChallenge(
            id = 5,
            title = "Quranic Commentary",
            text = "Exegetical scholarship provides profound insights into the multilayered meanings of divine revelation. The Kalma 'La ilaha illallahu Muhammadur rasulullah' means 'There is no deity except Allah, Muhammad is the messenger of Allah.' Meticulous linguistic analysis and contextual understanding illuminate the inexhaustible wisdom contained within each verse, facilitating spiritual enlightenment and intellectual growth.",
            highlightedWords = listOf("Exegetical", "scholarship", "profound", "multilayered", "revelation", "Meticulous", "linguistic", "contextual", "illuminate", "inexhaustible", "contained", "facilitating", "enlightenment", "intellectual")
        ),
        IslamicTextChallenge(
            id = 6,
            title = "Authentic Hadith",
            text = "Prophetic traditions preserve invaluable guidance through authenticated chains of transmission. The Kalma 'La ilaha illallahu Muhammadur rasulullah' means 'There is no deity except Allah, Muhammad is the messenger of Allah.' Rigorous verification methodologies ensure authenticity while safeguarding against fabrication, maintaining the integrity of prophetic wisdom for subsequent generations.",
            highlightedWords = listOf("traditions", "invaluable", "authenticated", "transmission", "Rigorous", "verification", "methodologies", "authenticity", "safeguarding", "fabrication", "integrity", "prophetic", "subsequent", "generations")
        ),
        IslamicTextChallenge(
            id = 7,
            title = "Caliphate History",
            text = "The Rashidun Caliphate exemplified exemplary governance through consultative decision-making and equitable administration. The Kalma 'La ilaha illallahu Muhammadur rasulullah' means 'There is no deity except Allah, Muhammad is the messenger of Allah.' Transparent accountability and meritocratic appointments characterized this foundational period, establishing precedents for just leadership and administrative excellence.",
            highlightedWords = listOf("Rashidun", "exemplified", "exemplary", "governance", "consultative", "equitable", "administration", "accountability", "meritocratic", "appointments", "foundational", "precedents", "administrative", "excellence")
        ),
        IslamicTextChallenge(
            id = 8,
            title = "Islamic Ethics",
            text = "Islamic moral philosophy emphasizes character refinement through consistent virtuous conduct. The Kalma 'La ilaha illallahu Muhammadur rasulullah' means 'There is no deity except Allah, Muhammad is the messenger of Allah.' Comprehensive ethical frameworks encompass interpersonal relationships, societal obligations, and spiritual development, fostering harmonious communities based on mutual respect and divine consciousness.",
            highlightedWords = listOf("philosophy", "emphasizes", "refinement", "consistent", "virtuous", "Comprehensive", "frameworks", "encompass", "interpersonal", "obligations", "fostering", "harmonious", "communities", "consciousness")
        ),
        IslamicTextChallenge(
            id = 9,
            title = "Spiritual Concepts",
            text = "Tasawwuf cultivates inner purification through disciplined spiritual practices and contemplative worship. The Kalma 'La ilaha illallahu Muhammadur rasulullah' means 'There is no deity except Allah, Muhammad is the messenger of Allah.' Mystical experiences facilitate transcendence of material attachments while maintaining equilibrium between worldly responsibilities and spiritual aspirations through divine remembrance.",
            highlightedWords = listOf("Tasawwuf", "cultivates", "purification", "disciplined", "contemplative", "Mystical", "facilitate", "transcendence", "attachments", "equilibrium", "responsibilities", "aspirations", "remembrance")
        ),
        IslamicTextChallenge(
            id = 10,
            title = "Contemporary Islamic Issues",
            text = "Modern Islamic scholarship addresses contemporary challenges through traditional methodological frameworks. The Kalma 'La ilaha illallahu Muhammadur rasulullah' means 'There is no deity except Allah, Muhammad is the messenger of Allah.' Scholarly consensus integrates technological advancement with immutable religious principles, ensuring authentic interpretation while accommodating legitimate innovation and societal progression.",
            highlightedWords = listOf("scholarship", "contemporary", "challenges", "methodological", "frameworks", "consensus", "integrates", "technological", "advancement", "immutable", "authentic", "interpretation", "accommodating", "progression")
        )
    )

    /**
     * Get a random Islamic text challenge
     */
    fun getRandomChallenge(): IslamicTextChallenge {
        return challenges[Random.nextInt(challenges.size)]
    }

    /**
     * Get all available challenges
     */
    fun getAllChallenges(): List<IslamicTextChallenge> {
        return challenges.toList()
    }

    /**
     * Get challenge by ID
     */
    fun getChallengeById(id: Int): IslamicTextChallenge? {
        return challenges.find { it.id == id }
    }
}
