package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.CardKind
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Rating

class OwnWordsTests {

    private val umbrella = OwnWord(
        id = "own:regenschirm",
        kind = OwnWords.DEFAULT_KIND,
        emoji = "☂️",
        texts = mapOf("de" to "Regenschirm", "sw" to "mwavuli"),
    )

    /** de → sw, matching [Box.stamp]. */
    private fun box(): BoxState = Box.state(listOf(Box.word(1), Box.word(2)))

    // Taking a word in

    @Test
    fun anAddedWordJoinsAsACardInItsOwnArea() {
        val state = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        val card = state.cards.getValue(umbrella.id)
        assertEquals(OwnWords.AREA, card.area)
        assertEquals("mwavuli", card.target.text)
        assertEquals("Regenschirm", card.source.text)
        assertEquals("☂️", card.emoji)
    }

    @Test
    fun anAddedWordIsPackedWithoutBeingAskedTwice() {
        val state = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        assertEquals(listOf(umbrella.id), state.enqueued)
    }

    @Test
    fun ownWordsSortBehindEveryCatalogWord() {
        val state = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        val catalogTop = state.cards.values.filterNot { OwnWords.owns(it.id) }.maxOf { it.seedIndex }
        assertTrue(state.cards.getValue(umbrella.id).seedIndex > catalogTop)
    }

    @Test
    fun theSameWordIsNotTakenInTwice() {
        val once = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        assertEquals(once, BoxEngine.addOwnWord(once, umbrella, Box.day1))
    }

    @Test
    fun anIdWithoutThePrefixIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            BoxEngine.addOwnWord(box(), umbrella.copy(id = "regenschirm"), Box.day1)
        }
    }

    // Coverage — the catalog's rule, applied to the learner's own words

    @Test
    fun aWordTheProfileCannotJoinIsKeptButNotStudied() {
        val halfWritten = umbrella.copy(texts = mapOf("de" to "Regenschirm"))
        val state = BoxEngine.addOwnWord(box(), halfWritten, Box.day1)
        assertNull(state.cards[halfWritten.id])
        assertTrue(state.enqueued.isEmpty())
        // why: not studiable is not the same as lost — the sw side can still be written.
        assertEquals(listOf(halfWritten.id), state.ownWords.map { it.id })
    }

    @Test
    fun aSourceSwitchDropsAnUncoveredWordAndRevivesItOnTheWayBack() {
        val state = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        val english = JoinStamp("en", "sw", "fixture")
        val switched = BoxEngine.rejoin(state, emptyList(), english)
        assertNull(switched.cards[umbrella.id])

        val back = BoxEngine.rejoin(switched, emptyList(), Box.stamp)
        assertEquals("mwavuli", back.cards.getValue(umbrella.id).target.text)
    }

    // Taking a word back out

    @Test
    fun removingAWordTakesItsCardScheduleAndQueuePlaceWithIt() {
        var state = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        state = Box.answered(state, umbrella.id, Rating.Good, Box.day1)
        assertTrue(state.scheduling.containsKey(umbrella.id))

        val removed = BoxEngine.removeOwnWord(state, umbrella.id)
        assertNull(removed.cards[umbrella.id])
        assertNull(removed.scheduling[umbrella.id])
        assertTrue(removed.ownWords.isEmpty())
        assertTrue(umbrella.id !in removed.enqueued)
    }

    @Test
    fun aCatalogWordIsNotTheLearnersToRemove() {
        val state = box()
        assertEquals(state, BoxEngine.removeOwnWord(state, "w01"))
    }

    @Test
    fun removingOneWordRenumbersNeitherTheOthersNorTheCatalog() {
        val second = OwnWord("own:kaugummi", CardKind.Noun, null, mapOf("de" to "Kaugummi", "sw" to "ubani"))
        var state = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        state = BoxEngine.addOwnWord(state, second, Box.day1)
        val removed = BoxEngine.removeOwnWord(state, umbrella.id)
        assertEquals(OwnWords.SEED_BASE, removed.cards.getValue(second.id).seedIndex)
        assertEquals(listOf(second.id), removed.ownWords.map { it.id })
    }

    // A fresh start is about progress, never about content

    @Test
    fun resetClearsTheProgressAndKeepsTheWords() {
        var state = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        state = Box.answered(state, umbrella.id, Rating.Good, Box.day1)

        val fresh = BoxEngine.reset(state)
        assertTrue(fresh.scheduling.isEmpty())
        assertTrue(fresh.enqueued.isEmpty())
        assertEquals(listOf(umbrella.id), fresh.ownWords.map { it.id })
        assertEquals("mwavuli", fresh.cards.getValue(umbrella.id).target.text)
    }

    // Minting ids

    @Test
    fun aMintedIdIsPrefixedAndReadable() {
        assertEquals("own:regenschirm", OwnWords.mint("Regenschirm", emptySet()))
    }

    @Test
    fun aMintedIdCarriesNoSeparatorAnIdMayNotHold() {
        val id = OwnWords.mint("Guten Tag / Hallo | Servus", emptySet())
        assertTrue('|' !in id && '/' !in id, id)
        assertTrue(OwnWords.owns(id), id)
    }

    @Test
    fun aTakenIdCountsUpInsteadOfColliding() {
        val taken = setOf("own:regenschirm", "own:regenschirm-2")
        assertEquals("own:regenschirm-3", OwnWords.mint("Regenschirm", taken))
    }

    @Test
    fun aWordWithNoLettersStillMintsAnId() {
        assertEquals("own:word", OwnWords.mint("!?!", emptySet()))
    }
}
