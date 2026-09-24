package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.session.TurnFeedback

/** A run against the clock: scored by the Sprosse each clean answer stood on, ended by [NumbersIntent.TimeUp]. */
class TimedRunTest {

    private val timed = NumbersMode(listOf(NumbersExercise.Counting), "de", setOf(DrillModifier.Timed))

    private fun at(level: Int) = NumbersRun.openAt(timed, mapOf(NumbersExercise.Counting to level), Random(5))

    private fun NumbersRunState.send(intent: NumbersIntent) = NumbersRun.reduce(this, intent, null, Random(9)).state

    private fun NumbersRunState.answered() =
        send(NumbersIntent.Submit(currentTask.display)).send(NumbersIntent.ConfirmPending)

    private fun NumbersRunState.missed() = send(NumbersIntent.Reveal).send(NumbersIntent.ConfirmPending)

    @Test
    fun aCleanAnswerScoresTheSprosseItWasGivenOn() {
        assertEquals(3, at(3).answered().score)
        assertEquals(0, at(3).missed().score, "a miss scores nothing")
        assertEquals(2, at(3).missed().currentLevel, "and drops the run a Sprosse, so what follows is worth less")
        val slipped = at(3).copy(feedback = TurnFeedback.Correct, hintUsed = true).send(NumbersIntent.ConfirmPending)
        assertEquals(0, slipped.score, "an almost scores nothing")
    }

    @Test
    fun timeUpEndsOnlyATimedRun() {
        assertTrue(at(1).send(NumbersIntent.TimeUp).finished)
        val plain = NumbersRun.open(NumbersMode(NumbersExercise.Counting, "de"), Random(5))
        assertFalse(plain.send(NumbersIntent.TimeUp).finished)
    }

    /** The clock can run out on an accepted answer; the close books it exactly as the ✕ would. */
    @Test
    fun theCloseBooksWhatWasPendingWhenTheTimeRanOut() {
        val pending = at(4).send(NumbersIntent.Submit(at(4).currentTask.display)).send(NumbersIntent.TimeUp)
        val closed = NumbersRun.close(pending, standingRecord = 3, standingProgress = emptyMap())
        assertEquals(4, closed.summary?.timed?.score)
        assertEquals(1, closed.summary?.done)
    }

    @Test
    fun theRecordATimedRunChasesIsItsScore() {
        val run = at(5).answered()
        val summary = NumbersRun.close(run, standingRecord = 4, standingProgress = emptyMap()).summary!!
        assertTrue(summary.newRecord)
        assertEquals(5, summary.recordFigure)
        assertFalse(NumbersRun.close(run, standingRecord = 5, standingProgress = emptyMap()).summary!!.newRecord)
        assertNull(summary.timed?.challenge)
    }

    @Test
    fun aTimedRunOffersNeitherTheLookUpNorAnEarlyFinish() {
        val twoMisses = at(1).missed().send(NumbersIntent.Reveal)
        assertFalse(twoMisses.offersLookUp)
        assertFalse(twoMisses.offersFinish)
    }
}
