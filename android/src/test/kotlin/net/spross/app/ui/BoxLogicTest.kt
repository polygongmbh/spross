package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.app.Chrome
import net.spross.kern.box.OwnWords
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.model.LanguageInfo

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

        assertEquals(chrome.boxOwnShelf, naming.title(OwnWords.AREA))
        assertEquals(chrome.boxOwnWordExplainer, naming.subtitle(OwnWords.AREA))
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
        assertEquals(listOf("Küche", "Bad", chrome.boxOwnShelf), searchable.map { it.title })
    }

    @Test
    fun aWordNeedsBothSidesBeforeItIsOneAndOneSideIsStillWorthKeeping() {
        assertFalse(OwnWordDraft(known = "Haus").isPair)
        assertFalse(OwnWordDraft(learning = "nyumba").isPair)
        assertFalse(OwnWordDraft(known = "Haus", learning = "   ").isPair)
        assertTrue(OwnWordDraft(known = "Haus", learning = "nyumba").isPair)

        assertFalse(OwnWordDraft(emoji = "🏠").hasAnything)
        assertTrue(OwnWordDraft(known = "Haus").hasAnything)
        assertTrue(OwnWordDraft(learning = "nyumba").hasAnything)
    }

    @Test
    fun aSuggestionCarriesTheOneSideItHasAndIsNamedAfterWhicheverThatIs() {
        val onlyLearnt = OwnWordDraft(learning = " nyumba ").word("de", "sw", emptySet())
        requireNotNull(onlyLearnt)
        assertEquals("${OwnWords.ID_PREFIX}nyumba", onlyLearnt.id)
        assertEquals(mapOf("sw" to "nyumba"), onlyLearnt.texts)
        assertTrue(onlyLearnt.isSuggestion(source = "de", target = "sw"))

        val onlyKnown = OwnWordDraft(known = " Haus ").word("de", "sw", emptySet())
        requireNotNull(onlyKnown)
        assertEquals("${OwnWords.ID_PREFIX}haus", onlyKnown.id)
        assertEquals(mapOf("de" to "Haus"), onlyKnown.texts)
        assertTrue(onlyKnown.isSuggestion(source = "de", target = "sw"))
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
    fun aDraftWithNeitherSideWrittenMakesNoWordAtAll() {
        assertNull(OwnWordDraft(emoji = "🏠", learning = "  ").word("de", "sw", emptySet()))
    }

    @Test
    fun theSameWordWrittenTwiceCountsUpRatherThanCollides() {
        val taken = setOf("${OwnWords.ID_PREFIX}nyumba")
        val word = OwnWordDraft(known = "Haus", learning = "nyumba").word("de", "sw", taken)

        assertEquals("${OwnWords.ID_PREFIX}nyumba-2", word?.id)
    }

    @Test
    fun anEditKeepsTheWordsIdAndKindRatherThanMintingNewOnes() {
        val stored = OwnWords.write(
            id = "${OwnWords.ID_PREFIX}nyumba",
            kind = OwnWords.DEFAULT_KIND,
            emoji = "🏠",
            texts = mapOf("de" to "Haus", "sw" to "nyumba"),
        )
        val draft = OwnWordDraft.of(stored, source = "de", target = "sw")

        assertEquals("Haus", draft.known)
        assertEquals("nyumba", draft.learning)
        assertEquals("🏠", draft.emoji)

        // The typo fixed on the learnt side would mint a different id for a new word; an
        // edit keeps this one, and with it the schedule and the queue slot.
        val fixed = draft.copy(learning = "nyumbani").word("de", "sw", setOf(stored.id))
        assertEquals(stored.id, fixed?.id)
        assertEquals(mapOf("de" to "Haus", "sw" to "nyumbani"), fixed?.texts)
        assertEquals(stored.kind, fixed?.kind)
    }

    @Test
    fun swappingExchangesTheTwoSidesAndLeavesTheRestAlone() {
        val draft = OwnWordDraft(known = "nyumba", learning = "Haus", emoji = "🏠").swapped()

        assertEquals("Haus", draft.known)
        assertEquals("nyumba", draft.learning)
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
