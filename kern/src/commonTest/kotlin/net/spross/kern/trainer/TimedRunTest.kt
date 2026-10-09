package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A challenge against the clock: scored by the Sprosse each clean answer stood on, ended by [NumbersIntent.TimeUp]. */
class TimedRunTest {

    private val challenge = requireNotNull(
        NumbersChallenge.create(NumbersMode(NumbersExercise.Counting, "de"), Random(5)),
    )

    /** The run standing on script question [index]. */
    private fun at(index: Int) = challenge.open().copy(current = challenge.tasks[index].drawn, index = index)
        .let { it.copy(sprossen = it.sprossen + (NumbersExercise.Counting to challenge.tasks[index].sprosse)) }

    private fun NumbersRunState.send(intent: NumbersIntent) = NumbersRun.reduce(this, intent, null, Random(9)).state

    private fun NumbersRunState.answered() =
        send(NumbersIntent.Submit(currentTask.display)).send(NumbersIntent.ConfirmPending)

    private fun NumbersRunState.missed() = send(NumbersIntent.Reveal).send(NumbersIntent.ConfirmPending)

    @Test
    fun aCleanAnswerScoresTheSprosseItWasAskedAt() {
        val late = at(6)
        assertEquals(challenge.tasks[6].sprosse, late.answered().score)
        assertEquals(0, late.missed().score, "a miss scores nothing")
    }

    @Test
    fun aCleanAnswerEarnsASecondPerNonZeroDigitOfTheAskedNumber() {
        val run = at(4)
        val digits = run.currentTask.prompt.count { it in '1'..'9' }
        assertEquals(digits, run.answered().earnedSeconds)
        assertEquals(0, run.missed().earnedSeconds, "a miss earns nothing")
        val task = NumbersTask(NumbersReading.Cardinal, "de", prompt = "1050", accepted = listOf("x"), display = "x")
        assertEquals(2, TimedRun.bonusSeconds(task, correct = true, clean = true), "zeros earn nothing")
        assertEquals(0, TimedRun.bonusSeconds(task, correct = true, clean = false), "an almost earns nothing")
    }

    @Test
    fun timeUpEndsOnlyAChallenge() {
        assertTrue(at(0).send(NumbersIntent.TimeUp).finished)
        val plain = NumbersRun.open(NumbersMode(NumbersExercise.Counting, "de"), 0, emptyMap(), Random(5))
        assertFalse(plain.send(NumbersIntent.TimeUp).finished)
    }

    @Test
    fun aChallengeOffersNeitherTheLookUpNorAnEarlyFinish() {
        val twoMisses = at(0).missed().send(NumbersIntent.Reveal)
        assertFalse(twoMisses.offersLookUp)
        assertFalse(twoMisses.offersFinish)
    }
}
