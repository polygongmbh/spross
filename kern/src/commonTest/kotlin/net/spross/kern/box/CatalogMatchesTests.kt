package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Realization

/**
 * The catalog catching up with a word the learner had to write themselves: which pairs are
 * offered, and which spellings are near enough to be the same word rather than a neighbor.
 */
class CatalogMatchesTests {

    private fun card(
        id: String,
        known: String,
        learning: String,
        teaches: List<String> = emptyList(),
        gender: String? = null,
    ) = Card(
        id = id, kind = CardKind.Noun, area = "area1", emoji = null, seedIndex = 1,
        components = emptyList(), feminineOf = null,
        source = Realization(lang = "de", text = known),
        target = Realization(
            lang = "sw", text = learning, teaches = teaches,
            grammar = gender?.let { mapOf("gender" to it) } ?: emptyMap(),
        ),
        promptFeminineMarker = false,
    )

    private fun own(id: String, texts: Map<String, String>) =
        OwnWord(id = OwnWords.ID_PREFIX + id, kind = OwnWords.DEFAULT_KIND, emoji = null, texts = texts)

    private fun box(catalog: List<Card>, vararg written: OwnWord): BoxState {
        var state = Box.state(catalog)
        written.forEach { state = BoxEngine.addOwnWord(state, it, Box.day1) }
        return state
    }

    private fun matchOf(state: BoxState): CatalogMatch? = CatalogMatches.of(state).singleOrNull()

    @Test
    fun aWordTheCatalogNowSpellsBothWaysIsMatchedWhole() {
        val state = box(
            listOf(card("umbrella", "Regenschirm", "mwavuli")),
            own("mwavuli", mapOf("de" to "Regenschirm", "sw" to "mwavuli")),
        )
        val match = matchOf(state)
        assertEquals("umbrella", match?.cardId)
        assertEquals(MatchSide.Both, match?.side)
    }

    /**
     * The correction case: one side spelled as the catalog spells it, the other saying
     * something else entirely. It is offered, and it is offered unticked — which of the two
     * words is right is the learner's call and nobody else's.
     */
    @Test
    fun aHalfTheCatalogSpellsDifferentlyIsOfferedOnTheSideThatAgrees() {
        val state = box(
            listOf(card("towel", "Handtuch", "kitambaa")),
            own("taulo", mapOf("de" to "Handtuch", "sw" to "taulo")),
        )
        assertEquals(MatchSide.KnownOnly, matchOf(state)?.side)

        val other = box(
            listOf(card("towel", "Handtuch", "kitambaa")),
            own("kitambaa", mapOf("de" to "Wischlappen", "sw" to "kitambaa")),
        )
        assertEquals(MatchSide.LearningOnly, matchOf(other)?.side)
    }

    /** A slip on the other side CONFIRMS the exact one rather than splitting off from it. */
    @Test
    fun aSlipBesideAnExactSideIsTheSameWordAndNotACorrection() {
        val state = box(
            listOf(card("towel", "Handtuch", "taula")),
            own("taulo", mapOf("de" to "Handtuch", "sw" to "taulo")),
        )
        assertEquals(MatchSide.Both, matchOf(state)?.side)
    }

    /** Neither side exact, both a slip off: still one word, said twice with typos. */
    @Test
    fun aWordTypedOverOnBothSidesStillFindsItsCatalogWord() {
        val state = box(
            listOf(card("bicycle", "Fahrrad", "baisikeli")),
            own("baiskeli", mapOf("de" to "Fahrad", "sw" to "baiskeli")),
        )
        assertEquals(MatchSide.Both, matchOf(state)?.side)
    }

    /**
     * A lone leaning side is the noise the arrival matcher exists to reject: sw `kupotea`
     * ("get lost") is one letter off `kupokea` ("receive") and they are two words.
     */
    @Test
    fun aSpellingASlipOffOnOneSideAloneIsNoMatch() {
        val state = box(
            listOf(card("receive", "empfangen", "kupokea")),
            own("kupotea", mapOf("de" to "verloren gehen", "sw" to "kupotea")),
        )
        assertNull(matchOf(state))
    }

    @Test
    fun aSuggestionIsMatchedOnTheOneSideItCarries() {
        val state = box(
            listOf(card("sun", "Sonne", "jua")),
            own("sonne", mapOf("de" to "Sonne")),
        )
        assertEquals(MatchSide.KnownOnly, matchOf(state)?.side)
    }

    @Test
    fun aRemarkNamesNoWordAndIsNeverMatched() {
        val note = own("note", emptyMap()).copy(comment = "the box scrolls back to the top")
        assertTrue(CatalogMatches.of(box(listOf(card("sun", "Sonne", "jua")), note)).isEmpty())
    }

    /** The forms the catalog accepts count as the catalog's own spelling. */
    @Test
    fun aSynonymOrAnArticledFormCountsAsTheCatalogsSpelling() {
        val state = box(
            listOf(card("umbrella", "Regenschirm", "mwavuli", teaches = listOf("mwamvuli"))),
            own("mwamvuli", mapOf("de" to "Sonnenschutz", "sw" to "mwamvuli")),
        )
        assertEquals(MatchSide.LearningOnly, matchOf(state)?.side)
    }

    /** A word the learner wrote cannot catch up with another word the learner wrote. */
    @Test
    fun ownWordsAreNeverMatchedAgainstEachOther() {
        val state = box(
            emptyList(),
            own("mwavuli", mapOf("de" to "Regenschirm", "sw" to "mwavuli")),
            own("mwavuli2", mapOf("de" to "Regenschirm", "sw" to "mwavuli")),
        )
        assertTrue(CatalogMatches.of(state).isEmpty())
    }

    /** Whole matches lead: they are the ones there is nothing left to judge about. */
    @Test
    fun theUnambiguousMatchesLeadTheList() {
        val state = box(
            listOf(card("sun", "Sonne", "jua"), card("umbrella", "Regenschirm", "mwavuli")),
            own("sonne", mapOf("de" to "Sonne")),
            own("mwavuli", mapOf("de" to "Regenschirm", "sw" to "mwavuli")),
        )
        assertEquals(
            listOf(MatchSide.Both, MatchSide.KnownOnly),
            CatalogMatches.of(state).map { it.side },
        )
    }
}
