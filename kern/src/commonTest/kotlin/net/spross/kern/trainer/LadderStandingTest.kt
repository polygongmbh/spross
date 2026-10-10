package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerControls
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Question
import net.spross.kern.session.Saying
import net.spross.kern.session.TurnFeedback

/** The booking every laddered drill run shares ([LadderStanding]); each run's own test keeps one wiring case. */
class LadderStandingTest {

    private val clean = DrillBooking(correct = true, clean = true)
    private val missed = DrillBooking(correct = false, clean = true)

    private fun standing(sprosse: Int = 1, wins: Int = 0) =
        LadderStanding(sprosse, sprosse, wins, emptySet(), DrillRunCore())

    /** The figures a run shell reads, and nothing a card needs. */
    private data class Run(
        override val core: DrillRunCore = DrillRunCore(),
        override val feedback: TurnFeedback = TurnFeedback.Neutral,
    ) : DrillRunProgress {
        override val index: Int = 0
        override val finished: Boolean = false
        override val promptSaying: Saying? = null
        override val answerSaying: Saying? = null
        override val question: Question? = null
        override val controls: AnswerControls? = null
    }

    private fun booked(run: Run, answer: DrillBooking) = run.copy(core = run.core.book(answer.correct, answer.clean, null))

    @Test
    fun cleanWinsClimbTheSprosseAndBookItForTheStore() {
        val climbed = standing().answered(clean, winsRequired = 2, solves = null)
            .answered(clean, winsRequired = 2, solves = null)
        assertEquals(2, climbed.sprosse)
        assertEquals(2, climbed.bestSprosse)
        assertEquals(0, climbed.winsAtSprosse)
        assertEquals(setOf(1), climbed.clearedSprossen)
        assertEquals(2, climbed.core.done)
    }

    @Test
    fun aMissDropsTheSprosseKeepsTheBestAndEndsTheClearing() {
        val dropped = standing(sprosse = 3).answered(missed, winsRequired = 1, solves = null)
        assertEquals(2, dropped.sprosse)
        assertEquals(3, dropped.bestSprosse)
        val climbedBack = dropped.answered(clean, winsRequired = 1, solves = null)
        assertEquals(3, climbedBack.sprosse)
        assertTrue(climbedBack.clearedSprossen.isEmpty(), "a run that slipped clears nothing more")
    }

    @Test
    fun theTopHoldsTheRunOnItsLastSprosse() {
        val held = standing(sprosse = 2).answered(clean, winsRequired = 1, solves = null, top = 2)
        assertEquals(2, held.sprosse)
        assertTrue(held.clearedSprossen.isEmpty(), "standing on a Sprosse is not leaving it")
    }

    @Test
    fun aSprosseClimbedPastLeavesItsWinsBehindAndIsBooked() {
        val banked = standing(sprosse = 2, wins = 1)
        assertEquals(1, banked.carriedTo(2).winsAtSprosse)
        val carried = banked.carriedTo(4)
        assertEquals(4, carried.sprosse)
        assertEquals(4, carried.bestSprosse)
        assertEquals(0, carried.winsAtSprosse)
        assertEquals(setOf(2), carried.clearedSprossen)
    }

    @Test
    fun closingBooksAPendingAnswerAsTheTapWouldAndARevealNot() {
        assertEquals(listOf(AnswerOutcome.Right), LadderStanding.closing(Run(feedback = TurnFeedback.Correct), ::booked).outcomes)
        val almost = Run(feedback = TurnFeedback.Almost("x", AlmostReason.Typo))
        assertEquals(listOf(AnswerOutcome.Almost), LadderStanding.closing(almost, ::booked).outcomes)
        val revealed = Run(feedback = TurnFeedback.Revealed)
        assertSame(revealed, LadderStanding.closing(revealed, ::booked))
    }

    @Test
    fun theSummaryIsNothingUntouchedAndARecordOnlyWhereOneIsKeptAndBeaten() {
        assertNull(LadderStanding.summary(Run(), standingRecord = 0))
        val answered = Run(core = DrillRunCore().book(correct = true, clean = true, solves = null))
        assertFalse(LadderStanding.summary(answered, standingRecord = null)!!.newRecord)
        assertFalse(LadderStanding.summary(answered, standingRecord = 1)!!.newRecord)
        assertTrue(LadderStanding.summary(answered, standingRecord = 0)!!.newRecord)
    }
}
