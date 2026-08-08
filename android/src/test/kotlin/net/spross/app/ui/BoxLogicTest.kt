package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.app.Chrome
import net.spross.kern.box.OwnWords
import net.spross.kern.catalog.LanguageChoices

/**
 * What the box browser ADDS around kern's rules: how a shelf names itself to this reader,
 * what the search is handed to match on, and when a picker tap is worth re-joining the box
 * for. The matching, the ranking and the id minting are kern's and tested there.
 */
class BoxLogicTest {

    private val chrome = Chrome.forSource("de")

    private val naming = AreaNaming(
        chrome = chrome,
        catalogTitle = { mapOf("kitchen" to "Küche", "bath" to "Bad")[it] },
        catalogSubtitle = { mapOf("kitchen" to "Hier duftet es")[it] },
        catalogEmoji = { mapOf("kitchen" to "🍳")[it] },
    )

    @Test
    fun theOwnShelfIsNamedByChromeAndCatalogShelvesNameThemselves() {
        assertEquals("Küche", naming.title("kitchen"))
        assertEquals("Hier duftet es", naming.subtitle("kitchen"))
        assertEquals("🍳", naming.emoji("kitchen"))

        assertEquals(chrome.ownWordsTitle, naming.title(OwnWords.AREA))
        assertEquals(chrome.ownWordsExplainer, naming.subtitle(OwnWords.AREA))
        assertEquals(OwnWords.EMOJI, naming.emoji(OwnWords.AREA))
    }

    @Test
    fun anAreaTheCatalogCannotNameFallsBackToItsKeyRatherThanABlank() {
        assertEquals("attic", naming.title("attic"))
        assertNull(naming.subtitle("attic"))
        assertEquals("📦", naming.emoji("attic"))
    }

    @Test
    fun theSearchMatchesOnTheHeadingTheLearnerRead() {
        val searchable = naming.searchable(listOf("kitchen", "bath", OwnWords.AREA))

        assertEquals(listOf("kitchen", "bath", OwnWords.AREA), searchable.map { it.area })
        // Nobody types "own" looking for their own words.
        assertEquals(listOf("Küche", "Bad", chrome.ownWordsTitle), searchable.map { it.title })
    }

    @Test
    fun aWordNeedsBothSidesBeforeItIsOne() {
        assertFalse(OwnWordDraft(known = "Haus").isComplete)
        assertFalse(OwnWordDraft(learning = "nyumba").isComplete)
        assertFalse(OwnWordDraft(known = "Haus", learning = "   ").isComplete)
        assertTrue(OwnWordDraft(known = "Haus", learning = "nyumba").isComplete)
    }

    @Test
    fun theWordIsTrimmedItsIdComesFromTheLearntSideAndAnEmptyPictureIsNone() {
        val word = OwnWordDraft(known = "  Haus ", learning = " nyumba ", emoji = "  ")
            .word(source = "de", target = "sw", taken = emptySet())

        requireNotNull(word)
        assertEquals("${OwnWords.ID_PREFIX}nyumba", word.id)
        assertNull(word.emoji)
        assertEquals(mapOf("de" to "Haus", "sw" to "nyumba"), word.texts)
        assertEquals(OwnWords.DEFAULT_KIND, word.kind)
    }

    @Test
    fun anIncompleteDraftMakesNoWordAtAll() {
        assertNull(OwnWordDraft(known = "x", learning = "  ").word("de", "sw", emptySet()))
    }

    @Test
    fun theSameWordWrittenTwiceCountsUpRatherThanCollides() {
        val taken = setOf("${OwnWords.ID_PREFIX}nyumba")
        val word = OwnWordDraft(known = "Haus", learning = "nyumba").word("de", "sw", taken)

        assertEquals("${OwnWords.ID_PREFIX}nyumba-2", word?.id)
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
