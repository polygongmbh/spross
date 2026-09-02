package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.Realization

/**
 * The article device: which article each hue answers for, and where the article on a
 * card comes from. Both are pure functions, so neither needs a device to be pinned —
 * the palette they resolve against is passed in rather than read from a composition.
 */
class ArticleColorTest {

    private fun noun(lang: String, text: String, gender: String? = null) = Realization(
        lang = lang,
        text = text,
        grammar = gender?.let { mapOf("gender" to it) } ?: emptyMap(),
    )

    @Test
    fun spanishSharesTheGermanHues() {
        assertEquals(ThemeLight.articleTint("der"), ThemeLight.articleTint("el"))
        assertEquals(ThemeLight.articleTint("die"), ThemeLight.articleTint("la"))
    }

    @Test
    fun pluralAndIndefiniteArticlesFollowTheirGender() {
        assertEquals(ThemeLight.articleTint("el"), ThemeLight.articleTint("los"))
        assertEquals(ThemeLight.articleTint("el"), ThemeLight.articleTint("un"))
        assertEquals(ThemeLight.articleTint("la"), ThemeLight.articleTint("las"))
        assertEquals(ThemeLight.articleTint("la"), ThemeLight.articleTint("una"))
    }

    @Test
    fun twoGenderLanguagesNeverReachTheNeuter() {
        val neuter = ThemeLight.articleTint("das")
        for (article in listOf("el", "la", "los", "las", "un", "una")) {
            assertTrue(ThemeLight.articleTint(article) != neuter, "$article took the neuter hue")
        }
    }

    @Test
    fun anUnknownOrAbsentArticleIsNeutral() {
        assertNull(ThemeLight.articleTint(null))
        assertNull(ThemeLight.articleTint("the"))
    }

    /**
     * Each gender reaches for its own token, in whichever scheme it is asked.
     * The hexes those tokens carry are kern's `Palette`'s, held there by
     * [ThemePaletteTest]; what is this test's is the mapping.
     */
    @Test
    fun eachGenderReachesForItsOwnTokenInBothSchemes() {
        for (palette in listOf(ThemeLight, ThemeDark)) {
            assertEquals(palette.der, palette.articleTint("der"))
            assertEquals(palette.die, palette.articleTint("die"))
            assertEquals(palette.das, palette.articleTint("das"))
        }
        // The two columns really are two: a tint that ignored its palette would satisfy
        // every mapping above while painting light hues onto a dark screen.
        assertTrue(ThemeLight.articleTint("der") != ThemeDark.articleTint("der"))
    }

    /**
     * The regression this table exists for: the article used to be sliced off the front
     * of `text`, which tinted the head of every multi-word noun as though it were one.
     */
    @Test
    fun theArticleComesFromGrammarNotFromTheFirstWord() {
        val toothpaste = ThemeLight.articleColoredText(noun("es", "pasta de dientes", "la"))
        assertEquals("la pasta de dientes", toothpaste.text)
        assertEquals(listOf(0 to 2), toothpaste.spanStyles.map { it.start to it.end })
        assertEquals(ThemeLight.articleTint("la"), toothpaste.spanStyles.single().item.color)
    }

    @Test
    fun aSingleWordNounStillGetsItsArticle() {
        val fridge = ThemeLight.articleColoredText(noun("de", "Kühlschrank", "der"))
        assertEquals("der Kühlschrank", fridge.text)
        assertEquals(ThemeLight.articleTint("der"), fridge.spanStyles.single().item.color)
    }

    @Test
    fun aPluraliaTantumNounShowsThePluralArticle() {
        val holiday = ThemeLight.articleColoredText(noun("es", "vacaciones", "las"))
        assertEquals("las vacaciones", holiday.text)
        assertEquals(ThemeLight.articleTint("la"), holiday.spanStyles.single().item.color)
    }

    @Test
    fun aGenderlessTargetRendersTheTextAndNothingElse() {
        val house = ThemeLight.articleColoredText(noun("sw", "nyumba"))
        assertEquals("nyumba", house.text)
        assertTrue(house.spanStyles.isEmpty())
    }

    /** es `internet` is the one noun that deliberately carries no gender (RAE). */
    @Test
    fun aNounWithoutAGenderRendersLikeAGenderlessLanguage() {
        assertEquals("internet", ThemeLight.articleColoredText(noun("es", "internet")).text)
    }
}
