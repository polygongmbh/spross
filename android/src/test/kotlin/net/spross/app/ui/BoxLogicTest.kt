package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.box.OwnWords
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.model.LanguageInfo

/**
 * What the box browser ADDS around kern's rules: how a written word reopens in the form,
 * and when a picker tap is worth re-joining the box for.
 * The naming, the matching, the ranking and the id minting are kern's and tested there.
 */
class BoxLogicTest {

    @Test
    fun aStoredWordOpensUnderTheProfilesTwoLanguages() {
        val stored = OwnWords.write(
            id = "${OwnWords.ID_PREFIX}nyumba",
            kind = OwnWords.DEFAULT_KIND,
            emoji = "🏠",
            texts = mapOf("de" to "Haus", "sw" to "nyumba"),
            comment = null,
        )
        val draft = OwnWordDraft.of(stored, source = "de", target = "sw")

        assertEquals("Haus", draft.sourceText)
        assertEquals("nyumba", draft.targetText)
        assertEquals("🏠", draft.emoji)
        assertEquals(stored, draft.editing)
    }

    @Test
    fun swappingExchangesTheTwoSidesAndLeavesTheRestAlone() {
        val draft = OwnWordDraft(sourceText = "nyumba", targetText = "Haus", emoji = "🏠").swapped()

        assertEquals("Haus", draft.sourceText)
        assertEquals("nyumba", draft.targetText)
        assertEquals("🏠", draft.emoji)
    }

    @Test
    fun thePictureIsCappedAtWhatKernAllowsAndCountsClustersNotChars() {
        assertEquals("", cappedPicture(""))
        assertEquals("🏠", cappedPicture("🏠"))
        assertEquals("🏠🍎", cappedPicture("🏠🍎"))
        // A third picture is dropped whole rather than cut through the middle of a glyph.
        assertEquals("🏠🍎", cappedPicture("🏠🍎🐾"))
        assertEquals(OwnWords.MAX_EMOJI, cappedPicture("abcdef").length)
    }

    @Test
    fun aLanguageLabelWearsItsFlagAndTheNameItCallsItselfBy() {
        val german = LanguageInfo(code = "de", name = "Deutsch", englishName = "German", flag = "🇩🇪")

        assertEquals("🇩🇪 Deutsch", flaggedLanguage(german, "de"))
        // A language the catalog does not carry falls back to its code — a visible content
        // bug rather than a blank label over a field.
        assertEquals("xx", flaggedLanguage(null, "xx"))
    }

    @Test
    fun aTapOnThePairAlreadyInForceRebuildsNothing() {
        val current = LanguageChoices.Selection("de", "es")

        assertNull(appliedPair(LanguageChoices.Selection("de", "es"), current))
        assertNull(appliedPair(LanguageChoices.Selection("de", null), current))
        assertEquals("es" to "de", appliedPair(LanguageChoices.Selection("es", "de"), current))
        assertEquals("en" to "es", appliedPair(LanguageChoices.Selection("en", "es"), current))
    }
}
