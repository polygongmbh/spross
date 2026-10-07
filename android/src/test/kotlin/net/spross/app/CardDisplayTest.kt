package net.spross.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.spross.kern.model.Realization

/**
 * The WORDS this platform wraps around kern's reveal rules. Which forms are left to offer are
 * `model/DisplayText.kt`'s and tested there — what is checked here is the labeling.
 */
class CardDisplayTest {

    private val chrome = Chrome.forSource("de")

    private fun realization(
        text: String,
        plural: String? = null,
        teaches: List<String> = emptyList(),
        gender: String? = null,
    ) = Realization(
        lang = "de",
        text = text,
        teaches = teaches,
        grammar = buildMap {
            plural?.let { put("plural", it) }
            gender?.let { put("gender", it) }
        },
    )

    @Test
    fun theAlsoLineNamesTheFamilyKernLeftStanding() {
        val word = realization("die Verwaltung", teaches = listOf("das Amt", "die Behörde"))
        assertEquals("auch: das Amt / die Behörde", CardDisplay.alsoLine(word, chrome, "die Verwaltung"))
    }

    @Test
    fun aWordWithNothingLeftToOfferHasNoLine() {
        assertNull(CardDisplay.alsoLine(realization("nyumba"), chrome, "nyumba"))
        assertNull(
            CardDisplay.alsoLine(
                realization("das Amt", teaches = listOf("die Behörde")),
                chrome,
                listOf("das Amt", "die Behörde"),
            ),
        )
    }

    @Test
    fun theArticleComesOffTheGrammar() {
        assertEquals("die", CardDisplay.article(realization("die Küche", gender = "die")))
        assertNull(CardDisplay.article(realization("nyumba")))
    }
}
