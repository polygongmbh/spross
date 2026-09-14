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
        ScrambleFixture.phrase("runs", "die Maus läuft", listOf("mouse", "run"), seed = 10),
        ScrambleFixture.phrase("sleeps", "die Maus schläft dort", listOf("mouse", "run"), seed = 11),
        ScrambleFixture.phrase("eats", "die Maus frisst", listOf("mouse", "run"), seed = 12),
        ScrambleFixture.phrase("waits", "die Maus wartet hier", listOf("mouse", "run"), seed = 13),
        ScrambleFixture.phrase("slow", "die Maus läuft sehr langsam", listOf("mouse", "run"), seed = 14),
    )

    private fun config() = SentenceScrambleRunConfig(
        SentenceScrambleAvailability.report(ScrambleFixture.box(words + phrases)),
    )

    private fun open(level: Int = 1, seed: Int = 5) =
        SentenceScrambleRun.openAt(config(), level, Random(seed))

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
        assertEquals(SentenceScrambleAvailability.MIN_ATOMS, task.size)
        assertEquals("en-${task.cardId}", task.gloss)
        assertEquals(ScrambleTokenizer.joined(task.canonical), task.display)
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
        assertEquals(done.task!!.display, done.arranged)
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

    /** Clean arrangements carry the Sprosse, and the Sprosse asks a longer phrase. */
    @Test
    fun cleanArrangementsCarryTheSprosseAndLengthenThePhrase() {
        var state = open()
        assertEquals(SentenceScrambleAvailability.MIN_ATOMS, assertNotNull(state.task).size)
        repeat(SentenceScrambleRun.WINS_TO_ADVANCE) {
            state = arrange(state, correctly = true)
            state = reduce(state, SentenceScrambleIntent.ConfirmPending).state
        }
        assertEquals(2, state.level)
        assertEquals(SentenceScrambleAvailability.MIN_ATOMS + 1, assertNotNull(state.task).size)
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

    /** A run with nothing left to ask ends on its summary rather than a blank card. */
    @Test
    fun aLadderAnsweredOutEndsTheRun() {
        var state = open()
        repeat(phrases.size + 2) {
            if (state.finished) return@repeat
            state = arrange(state, correctly = true)
            state = reduce(state, SentenceScrambleIntent.ConfirmPending).state
        }
        assertTrue(state.finished)
        assertNull(state.task)
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
        assertFalse(summary.newRecord, "the drill keeps no record store")
        assertTrue(closed.state.finished)
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
