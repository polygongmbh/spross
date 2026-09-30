package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.Realization

/**
 * The article device: which token each gender reaches for, and where the article on a card
 * comes from. Which article marks which gender is kern's (`articleGender`) and tested there.
 */
class ArticleColorTest {

    private fun noun(lang: String, text: String, gender: String? = null) = Realization(
        lang = lang,
        text = text,
        grammar = gender?.let { mapOf("gender" to it) } ?: emptyMap(),
    )

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

    /** The article is the grammar's, so a multi-word noun tints its article and nothing more. */
    @Test
    fun theArticleComesFromGrammarNotFromTheFirstWord() {
        val toothpaste = ThemeLight.articleColoredText(noun("es", "pasta de dientes", "la"))
        assertEquals("la pasta de dientes", toothpaste.text)
        assertEquals(listOf(0 to 2), toothpaste.spanStyles.map { it.start to it.end })
        assertEquals(ThemeLight.articleTint("la"), toothpaste.spanStyles.single().item.color)
    }

    /** The join is kern's `articledForm`: an elided article writes onto its noun. */
    @Test
    fun anElidedArticleWritesOntoItsNoun() {
        val water = ThemeLight.articleColoredText(noun("it", "acqua", "l'"))
        assertEquals("l'acqua", water.text)
        assertEquals(listOf(0 to 2), water.spanStyles.map { it.start to it.end })
    }

    @Test
    fun aGenderlessTargetRendersTheTextAndNothingElse() {
        val house = ThemeLight.articleColoredText(noun("sw", "nyumba"))
        assertEquals("nyumba", house.text)
        assertTrue(house.spanStyles.isEmpty())
    }
}
