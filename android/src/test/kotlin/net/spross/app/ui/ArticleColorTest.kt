package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.Realization

/**
 * The article device: which article each hue answers for, and where the article on a
 * card comes from. Both are pure functions, so neither needs a device to be pinned.
 */
class ArticleColorTest {

    private fun noun(lang: String, text: String, gender: String? = null) = Realization(
        lang = lang,
        text = text,
        grammar = gender?.let { mapOf("gender" to it) } ?: emptyMap(),
    )

    @Test
    fun spanishSharesTheGermanHues() {
        assertEquals(articleTint("der"), articleTint("el"))
        assertEquals(articleTint("die"), articleTint("la"))
    }

    @Test
    fun pluralAndIndefiniteArticlesFollowTheirGender() {
        assertEquals(articleTint("el"), articleTint("los"))
        assertEquals(articleTint("el"), articleTint("un"))
        assertEquals(articleTint("la"), articleTint("las"))
        assertEquals(articleTint("la"), articleTint("una"))
    }

    @Test
    fun twoGenderLanguagesNeverReachTheNeuter() {
        val neuter = articleTint("das")
        for (article in listOf("el", "la", "los", "las", "un", "una")) {
            assertTrue(articleTint(article) != neuter, "$article took the neuter hue")
        }
    }

    @Test
    fun anUnknownOrAbsentArticleIsNeutral() {
        assertNull(articleTint(null))
        assertNull(articleTint("the"))
    }

    /**
     * The regression this table exists for: the article used to be sliced off the front
     * of `text`, which tinted the head of every multi-word noun as though it were one.
     */
    @Test
    fun theArticleComesFromGrammarNotFromTheFirstWord() {
        val toothpaste = articleColoredText(noun("es", "pasta de dientes", "la"))
        assertEquals("la pasta de dientes", toothpaste.text)
        assertEquals(listOf(0 to 2), toothpaste.spanStyles.map { it.start to it.end })
        assertEquals(articleTint("la"), toothpaste.spanStyles.single().item.color)
    }

    @Test
    fun aSingleWordNounStillGetsItsArticle() {
        val fridge = articleColoredText(noun("de", "Kühlschrank", "der"))
        assertEquals("der Kühlschrank", fridge.text)
        assertEquals(articleTint("der"), fridge.spanStyles.single().item.color)
    }

    @Test
    fun aPluraliaTantumNounShowsThePluralArticle() {
        val holiday = articleColoredText(noun("es", "vacaciones", "las"))
        assertEquals("las vacaciones", holiday.text)
        assertEquals(articleTint("la"), holiday.spanStyles.single().item.color)
    }

    @Test
    fun aGenderlessTargetRendersTheTextAndNothingElse() {
        val house = articleColoredText(noun("sw", "nyumba"))
        assertEquals("nyumba", house.text)
        assertTrue(house.spanStyles.isEmpty())
    }

    /** es `internet` is the one noun that deliberately carries no gender (RAE). */
    @Test
    fun aNounWithoutAGenderRendersLikeAGenderlessLanguage() {
        assertEquals("internet", articleColoredText(noun("es", "internet")).text)
    }
}
