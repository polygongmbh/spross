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
        assertEquals(DlLight.articleTint("der"), DlLight.articleTint("el"))
        assertEquals(DlLight.articleTint("die"), DlLight.articleTint("la"))
    }

    @Test
    fun pluralAndIndefiniteArticlesFollowTheirGender() {
        assertEquals(DlLight.articleTint("el"), DlLight.articleTint("los"))
        assertEquals(DlLight.articleTint("el"), DlLight.articleTint("un"))
        assertEquals(DlLight.articleTint("la"), DlLight.articleTint("las"))
        assertEquals(DlLight.articleTint("la"), DlLight.articleTint("una"))
    }

    @Test
    fun twoGenderLanguagesNeverReachTheNeuter() {
        val neuter = DlLight.articleTint("das")
        for (article in listOf("el", "la", "los", "las", "un", "una")) {
            assertTrue(DlLight.articleTint(article) != neuter, "$article took the neuter hue")
        }
    }

    @Test
    fun anUnknownOrAbsentArticleIsNeutral() {
        assertNull(DlLight.articleTint(null))
        assertNull(DlLight.articleTint("the"))
    }

    /**
     * Each gender reaches for its own token, in whichever scheme it is asked.
     * The hexes those tokens carry belong to `App/Sources/Design/Theme.swift` and are
     * held to it by kern's `PaletteParityTest`; what is this test's is the mapping.
     */
    @Test
    fun eachGenderReachesForItsOwnTokenInBothSchemes() {
        for (palette in listOf(DlLight, DlDark)) {
            assertEquals(palette.der, palette.articleTint("der"))
            assertEquals(palette.die, palette.articleTint("die"))
            assertEquals(palette.das, palette.articleTint("das"))
        }
        // The two columns really are two: a tint that ignored its palette would satisfy
        // every mapping above while painting light hues onto a dark screen.
        assertTrue(DlLight.articleTint("der") != DlDark.articleTint("der"))
    }

    /**
     * The regression this table exists for: the article used to be sliced off the front
     * of `text`, which tinted the head of every multi-word noun as though it were one.
     */
    @Test
    fun theArticleComesFromGrammarNotFromTheFirstWord() {
        val toothpaste = DlLight.articleColoredText(noun("es", "pasta de dientes", "la"))
        assertEquals("la pasta de dientes", toothpaste.text)
        assertEquals(listOf(0 to 2), toothpaste.spanStyles.map { it.start to it.end })
        assertEquals(DlLight.articleTint("la"), toothpaste.spanStyles.single().item.color)
    }

    @Test
    fun aSingleWordNounStillGetsItsArticle() {
        val fridge = DlLight.articleColoredText(noun("de", "Kühlschrank", "der"))
        assertEquals("der Kühlschrank", fridge.text)
        assertEquals(DlLight.articleTint("der"), fridge.spanStyles.single().item.color)
    }

    @Test
    fun aPluraliaTantumNounShowsThePluralArticle() {
        val holiday = DlLight.articleColoredText(noun("es", "vacaciones", "las"))
        assertEquals("las vacaciones", holiday.text)
        assertEquals(DlLight.articleTint("la"), holiday.spanStyles.single().item.color)
    }

    @Test
    fun aGenderlessTargetRendersTheTextAndNothingElse() {
        val house = DlLight.articleColoredText(noun("sw", "nyumba"))
        assertEquals("nyumba", house.text)
        assertTrue(house.spanStyles.isEmpty())
    }

    /** es `internet` is the one noun that deliberately carries no gender (RAE). */
    @Test
    fun aNounWithoutAGenderRendersLikeAGenderlessLanguage() {
        assertEquals("internet", DlLight.articleColoredText(noun("es", "internet")).text)
    }
}
