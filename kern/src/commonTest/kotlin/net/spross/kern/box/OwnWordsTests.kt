package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
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

    // Writing a word's text

    @Test
    fun aSlashJoinedAlternativeIsStoredWhole() {
        val word = OwnWords.write(
            id = "own:gari-yangu",
            kind = OwnWords.DEFAULT_KIND,
            emoji = null,
            texts = mapOf("de" to "mein Auto", "sw" to "gari yangu / gari langu"),
            comment = null,
        )
        assertEquals("gari yangu / gari langu", word.texts.getValue("sw"))
    }

    @Test
    fun aSuggestionKeepsEveryFormItWasWrittenWith() {
        val word = OwnWords.write(
            id = "own:kuandika",
            kind = OwnWords.DEFAULT_KIND,
            emoji = null,
            texts = mapOf("sw" to "kuandika / kuchora"),
            comment = "which one is it?",
        )
        assertEquals("kuandika / kuchora", word.texts.getValue("sw"))
        assertTrue(word.isSuggestion)
    }

    @Test
    fun aSlashJoinedAlternativeIsAcceptedBesideTheFirstForm() {
        val word = OwnWords.write(
            id = "own:gari-yangu",
            kind = OwnWords.DEFAULT_KIND,
            emoji = null,
            texts = mapOf("de" to "mein Auto", "sw" to "gari yangu / gari langu"),
            comment = null,
        )
        val card = OwnWords.cards(listOf(word), source = "de", target = "sw").single()
        assertEquals("gari yangu", card.target.text)
        assertEquals(listOf("gari langu"), card.target.variants)
    }

    @Test
    fun aPlainWordIsWrittenUnchanged() {
        val word = OwnWords.write(
            id = umbrella.id, kind = umbrella.kind, emoji = umbrella.emoji,
            texts = umbrella.texts, comment = null,
        )
        assertEquals(umbrella.texts, word.texts)
    }

    // Rewriting a word without losing the progress made on it

    @Test
    fun editingAWordKeepsItsScheduleAndItsPlaceInTheQueue() {
        var state = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        state = Box.answered(state, umbrella.id, Rating.Good, Box.day1)
        val before = state.scheduling.getValue(umbrella.id)

        val fixed = umbrella.copy(texts = mapOf("de" to "Regenschirm", "sw" to "mwamvuli"))
        val edited = BoxEngine.updateOwnWord(state, fixed)

        assertEquals(before, edited.scheduling.getValue(umbrella.id))
        assertEquals("mwamvuli", edited.cards.getValue(umbrella.id).target.text)
        assertEquals(1, edited.ownWords.size)
    }

    @Test
    fun editingKeepsWhenTheWordWasWrittenNotWhenItWasChanged() {
        val state = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        val written = state.ownWords.single().addedAt
        val edited = BoxEngine.updateOwnWord(state, umbrella.copy(emoji = "🌂"))
        assertEquals(written, edited.ownWords.single().addedAt)
        assertEquals("🌂", edited.cards.getValue(umbrella.id).emoji)
    }

    @Test
    fun fillingInTheMissingHalfTurnsASuggestionIntoAPackedCard() {
        val half = umbrella.copy(texts = mapOf("de" to "Regenschirm"))
        val state = BoxEngine.addOwnWord(box(), half, Box.day1)
        assertNull(state.cards[half.id])

        val completed = BoxEngine.updateOwnWord(state, umbrella)
        assertEquals("mwavuli", completed.cards.getValue(umbrella.id).target.text)
        assertEquals(listOf(umbrella.id), completed.enqueued)
    }

    @Test
    fun emptyingAHalfTurnsTheCardBackIntoASuggestionAndParksItsSchedule() {
        var state = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        state = Box.answered(state, umbrella.id, Rating.Good, Box.day1)

        val half = BoxEngine.updateOwnWord(state, umbrella.copy(texts = mapOf("de" to "Regenschirm")))
        assertNull(half.cards[umbrella.id])
        // why: inert, not lost — writing the sw side back brings the schedule with it.
        assertTrue(umbrella.id in half.scheduling)
    }

    @Test
    fun aWordTheLearnerNeverWroteIsNotTheirsToEdit() {
        val state = box()
        assertEquals(state, BoxEngine.updateOwnWord(state, umbrella))
    }

    // Forgetting one card's progress

    @Test
    fun forgettingDropsTheScheduleAndKeepsTheCard() {
        var state = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        state = Box.answered(state, umbrella.id, Rating.Good, Box.day1)

        val forgotten = BoxEngine.forget(state, umbrella.id)
        assertNull(forgotten.scheduling[umbrella.id])
        assertEquals("mwavuli", forgotten.cards.getValue(umbrella.id).target.text)
        assertEquals(1, forgotten.ownWords.size)
    }

    @Test
    fun forgettingACatalogWordKeepsItToo() {
        val state = Box.answered(box(), "w01", Rating.Good, Box.day1)
        val forgotten = BoxEngine.forget(state, "w01")
        assertNull(forgotten.scheduling["w01"])
        assertTrue("w01" in forgotten.cards)
    }

    @Test
    fun forgettingLeavesAReportStanding() {
        // why: a report is about the CONTENT — forgetting the answers does not make
        // the translation right.
        var state = Box.answered(box(), "w01", Rating.Good, Box.day1)
        state = BoxEngine.reportIssue(state, "w01", "wrong", null, Box.day1)
        assertTrue("w01" in BoxEngine.forget(state, "w01").reportedIssues)
    }

    @Test
    fun forgettingAWordTheBoxNeverIntroducedChangesNothing() {
        val state = box()
        assertEquals(state, BoxEngine.forget(state, "w01"))
    }

    // Suspending a word the box has never asked

    @Test
    fun aWordCanBeSuspendedBeforeItHasEverBeenAsked() {
        val state = BoxEngine.setSuspended(box(), "w01", true, Box.day1)
        val sched = state.scheduling.getValue("w01")
        assertTrue(sched.suspended)
        assertEquals(CardPhase.New, sched.phase)
        assertNull(sched.memory)
        assertNull(sched.due)
        assertTrue(Inventory.active(state).none { it.cardId == "w01" })
    }

    @Test
    fun wakingOneThatWasNeverAnsweredGivesItBackToGrowth() {
        // why: growth only ever reaches a card with NO schedule, so clearing the flag
        // and leaving the husk would make waking a word the one way to lose it.
        val suspended = BoxEngine.setSuspended(box(), "w01", true, Box.day1)
        val woken = BoxEngine.setSuspended(suspended, "w01", false, Box.day1)
        assertNull(woken.scheduling["w01"])
        assertEquals(box(), woken)
    }

    @Test
    fun wakingOneThatWasAnsweredKeepsItsSchedule() {
        var state = Box.answered(box(), "w01", Rating.Good, Box.day1)
        state = BoxEngine.setSuspended(state, "w01", true, Box.day1)
        val woken = BoxEngine.setSuspended(state, "w01", false, Box.day1)
        assertFalse(woken.scheduling.getValue("w01").suspended)
        assertEquals(1, woken.scheduling.getValue("w01").reviewCount)
    }

    @Test
    fun aCardTheProfileDoesNotHoldCannotBeSuspended() {
        val state = box()
        assertEquals(state, BoxEngine.setSuspended(state, "nope", true, Box.day1))
    }

    // Moving one onto the catalog word that caught up with it

    /** The word written by hand, answered [answers] times, beside a catalog word. */
    private fun written(answers: Int): BoxState {
        var state = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        repeat(answers) { state = Box.answered(state, umbrella.id, Rating.Good, Box.plusDays(Box.day1, it + 1.0)) }
        return state
    }

    @Test
    fun mergingMovesTheWordsProgressOntoTheCatalogCardAndTakesTheWordOut() {
        val merged = BoxEngine.mergeOwnWord(written(answers = 2), umbrella.id, "w01")
        assertEquals(2, merged.scheduling.getValue("w01").reviewCount)
        assertEquals("w01", merged.scheduling.getValue("w01").cardId)
        assertNull(merged.scheduling[umbrella.id])
        assertTrue(merged.ownWords.isEmpty())
        assertNull(merged.cards[umbrella.id])
    }

    /**
     * Two records of learning ONE word. Folding them together would read as twice the
     * exposure the word has had and overshoot its stability, so the longer one stands whole.
     */
    @Test
    fun theLongerHistoryWinsWhicheverSideItIsOn() {
        var state = written(answers = 3)
        state = Box.answered(state, "w01", Rating.Good, Box.day1)
        assertEquals(3, BoxEngine.mergeOwnWord(state, umbrella.id, "w01").scheduling.getValue("w01").reviewCount)

        var other = written(answers = 1)
        repeat(4) { other = Box.answered(other, "w01", Rating.Good, Box.plusDays(Box.day1, it + 1.0)) }
        assertEquals(4, BoxEngine.mergeOwnWord(other, umbrella.id, "w01").scheduling.getValue("w01").reviewCount)
    }

    @Test
    fun aWordSetAsideOnEitherSideStaysSetAsideAfterTheMerge() {
        val state = BoxEngine.setSuspended(written(answers = 2), umbrella.id, true, Box.day1)
        assertTrue(BoxEngine.mergeOwnWord(state, umbrella.id, "w01").scheduling.getValue("w01").suspended)
    }

    /** Merging a suggestion is the catalog answering what it was written for. */
    @Test
    fun mergingASuggestionPacksTheCatalogWordInItsPlace() {
        val half = OwnWord(id = "own:sonne", kind = OwnWords.DEFAULT_KIND, emoji = null,
                           texts = mapOf("de" to "Sonne"))
        val state = BoxEngine.addOwnWord(box(), half, Box.day1)
        val merged = BoxEngine.mergeOwnWord(state, half.id, "w01")
        assertEquals(listOf("w01"), merged.enqueued)
        assertTrue(merged.ownWords.isEmpty())
    }

    @Test
    fun aWordWaitingInTheQueueKeepsItsPlaceUnderTheCatalogId() {
        val state = BoxEngine.addOwnWord(box(), umbrella, Box.day1)
        assertEquals(listOf("w01"), BoxEngine.mergeOwnWord(state, umbrella.id, "w01").enqueued)
    }

    @Test
    fun onlyAWordTheLearnerWroteMergesAndOnlyOntoACardThisProfileHolds() {
        val state = written(answers = 1)
        assertEquals(state, BoxEngine.mergeOwnWord(state, "w02", "w01"))
        assertEquals(state, BoxEngine.mergeOwnWord(state, umbrella.id, "nope"))
        assertEquals(state, BoxEngine.mergeOwnWord(state, "own:nope", "w01"))
    }
}
