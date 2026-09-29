package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.TurnFeedback

/** The sentence run: what a placement is worth, where the ladder takes it, what a close books. */
class SentenceScrambleRunTest {

    private val words = listOf(
        ScrambleFixture.word("mouse", "Maus", seed = 1),
        ScrambleFixture.word("run", "laufen", seed = 2),
    )

    private val phrases = listOf(
        ScrambleFixture.phrase("runs", "die Maus läuft.", listOf("mouse", "run"), seed = 10),
        ScrambleFixture.phrase("sleeps", "die Maus schläft dort.", listOf("mouse", "run"), seed = 11),
        ScrambleFixture.phrase("eats", "die Maus frisst.", listOf("mouse", "run"), seed = 12),
        ScrambleFixture.phrase("waits", "die Maus wartet hier.", listOf("mouse", "run"), seed = 13),
        ScrambleFixture.phrase("slow", "die Maus läuft sehr langsam.", listOf("mouse", "run"), seed = 14),
        ScrambleFixture.phrase("asks", "läuft die Maus?", listOf("mouse", "run"), seed = 15),
    )

    /** Two phrases to a band: three Sprossen over the six phrases, the first of them all three words. */
    private fun report(bandSize: Int = 2) = SentenceScrambleAvailability.report(ScrambleFixture.box(words + phrases))
        .copy(bandSize = bandSize)

    private fun config(bandSize: Int = 2) = SentenceScrambleRunConfig(report(bandSize))

    private fun open(level: Int = 1, seed: Int = 5, bandSize: Int = 2) =
        SentenceScrambleRun.openAt(config(bandSize), level, Random(seed))

    private fun answered(state: SentenceScrambleRunState, correctly: Boolean = true) =
        reduce(arrange(state, correctly), SentenceScrambleIntent.ConfirmPending).state

    private fun reduce(
        state: SentenceScrambleRunState,
        intent: SentenceScrambleIntent,
        seed: Int = 11,
    ) = SentenceScrambleRun.reduce(state, intent, Random(seed))

    /** Place every atom in the order the phrase was authored in. */
    private fun arrange(state: SentenceScrambleRunState, correctly: Boolean): SentenceScrambleRunState {
        val task = assertNotNull(state.task)
        val order = task.canonical.let { if (correctly) it else it.reversed() }
        return order.fold(state) { carried, atom ->
            reduce(carried, SentenceScrambleIntent.PlaceAtom(task.shuffled.indexOfFirst { it.id == atom.id })).state
        }
    }

    /** The deal is the phrase's own atoms, in an order that is not the phrase's. */
    @Test
    fun theAtomsAreDealtOutOfOrder() {
        val task = assertNotNull(open().task)
        assertEquals(task.canonical.map { it.id }.sorted(), task.shuffled.map { it.id }.sorted())
        assertFalse(ScrambleGrading.isSolved(task.shuffled, task.canonical), "dealt in its own order")
        assertEquals(SentenceScrambleAvailability.MIN_ATOMS, task.words)
        assertEquals("en-${task.cardId}", task.gloss)
        assertEquals(ScrambleTokenizer.joined(task.canonical), task.display.removeSuffix("."))
    }

    /** A miss drops a band, and the band below asks genuinely easier phrases. */
    @Test
    fun aMissDropsToTheEasierBand() {
        val report = config().report
        for (seed in 1..8) {
            val state = open(level = 2, seed = seed)
            assertTrue(assertNotNull(state.task).cardId in report.phrasesAt(2).map { it.card.id })
            val missed = answered(state, correctly = false)
            assertEquals(1, missed.level)
            assertTrue(assertNotNull(missed.task).cardId in report.phrasesAt(1).map { it.card.id })
        }
    }

    /**
     * A question mark is dealt and placed like any other chip: riding its word it would name
     * the last one, and the sentence's own full stop is dropped for the same reason.
     */
    @Test
    fun aMarkIsAChipToPlaceAndTheFullStopIsGone() {
        val asks = config().report.phrases.single { it.card.id == "asks" }
        assertEquals(listOf("läuft", "die", "Maus", "?"), asks.atoms.map { it.text })
        assertEquals(SentenceScrambleAvailability.MIN_ATOMS, asks.words)
        val slow = config().report.phrases.single { it.card.id == "slow" }
        assertEquals(listOf("die", "Maus", "läuft", "sehr", "langsam"), slow.atoms.map { it.text })
        assertEquals("die Maus läuft sehr langsam.", slow.card.target.text, "the reveal keeps it")
    }

    /** Committing the last atom IS the answer; a right arrangement books it and arms the beat. */
    @Test
    fun thePlacementThatCompletesTheArrangementIsTheAnswer() {
        var state = open()
        val task = assertNotNull(state.task)
        state = reduce(state, SentenceScrambleIntent.PlaceAtom(task.shuffled.indexOfFirst { it.id == 0 }))
            .state
        assertEquals(TurnFeedback.Neutral, state.feedback, "an incomplete arrangement decides nothing")
        assertEquals(task.size - 1, state.remaining)

        val done = arrange(open(), correctly = true)
        assertEquals(TurnFeedback.Correct, done.feedback)
        assertTrue(done.complete)
        assertEquals(ScrambleTokenizer.joined(done.task!!.canonical), done.arranged)
    }

    /** A wrong order opens the card on the authored one rather than waiting to be permuted. */
    @Test
    fun aWrongArrangementRevealsTheAuthoredOrder() {
        val wrong = arrange(open(), correctly = false)
        assertEquals(TurnFeedback.Revealed, wrong.feedback)
        assertTrue(wrong.showsAnswer)
        val booked = reduce(wrong, SentenceScrambleIntent.ConfirmPending).state
        assertEquals(listOf(AnswerOutcome.Wrong), booked.outcomes)
        assertEquals(emptyList(), booked.placed, "the next question starts empty")
    }

    /** The placement that grades, and the task it graded. */
    private fun graded(correctly: Boolean): Pair<SentenceScrambleTask, SentenceScrambleReduction> {
        val state = open()
        val task = assertNotNull(state.task)
        val order = task.canonical.let { if (correctly) it else it.reversed() }
        val place = { atom: ScrambleAtom -> SentenceScrambleIntent.PlaceAtom(task.shuffled.indexOfFirst { it.id == atom.id }) }
        val waiting = order.dropLast(1).fold(state) { carried, atom -> reduce(carried, place(atom)).state }
        return task to reduce(waiting, place(order.last()))
    }

    /** A right arrangement ends on the sound of the phrase. */
    @Test
    fun aRightArrangementSaysThePhrase() {
        val (task, reduction) = graded(correctly = true)
        assertTrue(DrillEffect.SayAnswer(task.display, "de") in reduction.effects)
    }

    /** A wrong one says the phrase as authored, not as it was put together. */
    @Test
    fun aWrongArrangementSaysTheAuthoredPhrase() {
        val (task, reduction) = graded(correctly = false)
        assertTrue(DrillEffect.SayAnswer(task.display, "de") in reduction.effects)
    }

    /** An atom can be taken back while the arrangement is still the learner's to give. */
    @Test
    fun anAtomComesBackWhileTheArrangementIsOpen() {
        var state = open()
        val task = assertNotNull(state.task)
        state = reduce(state, SentenceScrambleIntent.PlaceAtom(0)).state
        state = reduce(state, SentenceScrambleIntent.PlaceAtom(1)).state
        assertTrue(state.isPlaced(0))
        state = reduce(state, SentenceScrambleIntent.ReturnAtom(0)).state
        assertFalse(state.isPlaced(0))
        assertEquals(listOf(task.shuffled[1]), state.placedAtoms)
        // The same atom twice, and an atom nobody dealt, change nothing.
        assertEquals(state, reduce(state, SentenceScrambleIntent.PlaceAtom(1)).state)
        assertEquals(state, reduce(state, SentenceScrambleIntent.PlaceAtom(task.size + 3)).state)
    }

    /** Once the question is decided the arrangement stands — nothing may be taken back out of it. */
    @Test
    fun aDecidedArrangementIsNotRearranged() {
        val done = arrange(open(), correctly = true)
        assertEquals(done, reduce(done, SentenceScrambleIntent.ReturnAtom(0)).state)
    }

    /** A phrase arranged clean is never asked again — its order does not change with the Sprosse. */
    @Test
    fun aPhraseArrangedCleanIsRetired() {
        var state = open()
        val first = assertNotNull(state.task).cardId
        state = arrange(state, correctly = true)
        state = reduce(state, SentenceScrambleIntent.ConfirmPending).state
        assertTrue(DrillSolved.sentenceKey(first) in state.solved)
        assertFalse(assertNotNull(state.task).cardId == first)
    }

    /** The ladder climbs band by band and ends on its summary once the top band is answered out. */
    @Test
    fun aLadderAnsweredOutEndsTheRun() {
        var state = open()
        repeat(phrases.size + 2) {
            if (state.finished) return@repeat
            state = answered(state)
            assertTrue(state.level <= state.config.report.maxLevel, "Sprosse ${state.level} holds nothing")
        }
        assertTrue(state.finished)
        assertNull(state.task)
    }

    /** Wins enough to climb, on the top band, keep the run there on what the band has left. */
    @Test
    fun theTopBandIsWhereTheLadderStops() {
        var state = open(bandSize = phrases.size)
        repeat(SentenceScrambleRun.WINS_TO_ADVANCE) { state = answered(state) }
        assertEquals(1, state.level)
        assertEquals(1, state.bestLevel)
        assertNotNull(state.task, "the band still has phrases to ask")
    }

    /** Closing books a pending answer as the tap would, and an untouched run reports nothing. */
    @Test
    fun closingBooksWhatWeiterWouldAndNothingMore() {
        assertNull(SentenceScrambleRun.close(open()).summary)

        val done = arrange(open(), correctly = true)
        val closed = SentenceScrambleRun.close(done)
        val summary = assertNotNull(closed.summary)
        assertEquals(1, summary.done)
        assertEquals(1, summary.bestStreak)
        assertFalse(summary.newRecord, "the drill keeps no streak record")
        assertTrue(closed.state.finished)
    }

    // MARK: - What the store keeps

    /** Every Sprosse climbed off clean is booked, and none the run still stands on. */
    @Test
    fun aSprosseClimbedCleanIsBooked() {
        var state = open()
        repeat(phrases.size - 1) { state = answered(state) }
        val closed = SentenceScrambleRun.close(state)
        assertEquals(3, closed.bestLevel)
        assertEquals(setOf(1, 2), closed.clearedSprossen)
    }

    /** A resumed run opens at the foot and passes a band the store holds on one clean arrangement. */
    @Test
    fun aResumedRunFastClimbsWhatTheStoreHolds() {
        val resumed = SentenceScrambleRunConfig(report(bandSize = 3), cleared = setOf(1))
        val state = SentenceScrambleRun.open(resumed, Random(7))
        assertEquals(1, state.level)
        assertEquals(2, answered(state).level)
    }

    /** A wrong arrangement takes the Sprosse's booking with it, however clean the rest of it runs. */
    @Test
    fun aMissForfeitsTheSprosseItFallsOn() {
        var state = arrange(open(), correctly = false)
        state = reduce(state, SentenceScrambleIntent.ConfirmPending).state
        assertEquals(1, state.level, "the foot of the ladder has nothing below it")

        repeat(phrases.size) {
            if (state.level > 1 || state.task == null) return@repeat
            state = arrange(state, correctly = true)
            state = reduce(state, SentenceScrambleIntent.ConfirmPending).state
        }
        assertTrue(state.level > 1, "the run climbs as it always did")
        val closed = SentenceScrambleRun.close(state)
        assertEquals(emptySet(), closed.clearedSprossen, "but the Sprosse is not the store's")
    }

    /** An alternative word order from `orders` is accepted but flags [alternativeMatch]. */
    @Test
    fun anAlternativeOrderIsAcceptedAndFlagged() {
        val canonical = listOf(ScrambleAtom(0, "gehen"), ScrambleAtom(1, "Sie"), ScrambleAtom(2, "geradeaus"))
        val placed = listOf(canonical[1], canonical[0], canonical[2])
        val altAtoms = listOf(ScrambleAtom(0, "Sie"), ScrambleAtom(1, "gehen"), ScrambleAtom(2, "geradeaus"))
        assertFalse(ScrambleGrading.matchesCanonical(placed, canonical))
        assertTrue(ScrambleGrading.isSolved(placed, canonical, listOf(altAtoms)))

        val ordered = words + listOf(
            ScrambleFixture.phrase("formal", "Gehen Sie geradeaus.",
                listOf("mouse"), seed = 20, orders = listOf("Sie gehen geradeaus.")),
        )
        val report = SentenceScrambleAvailability.report(ScrambleFixture.box(ordered))
        val phrase = report.phrases.single { it.card.id == "formal" }
        assertEquals(1, phrase.alternativeOrders.size)
    }

    /** The beat only ever arms on a clean answer. */
    @Test
    fun theBeatRidesACleanArrangementAlone() {
        val task = assertNotNull(open().task)
        val last = task.canonical.last()
        var state = open()
        for (atom in task.canonical.dropLast(1)) {
            state = reduce(state, SentenceScrambleIntent.PlaceAtom(task.shuffled.indexOfFirst { it.id == atom.id }))
                .state
        }
        val closing = reduce(state, SentenceScrambleIntent.PlaceAtom(task.shuffled.indexOfFirst { it.id == last.id }))
        assertTrue(closing.effects.any { it == DrillEffect.ArmAdvance(AdvanceTier.Explicit) })
    }
}
