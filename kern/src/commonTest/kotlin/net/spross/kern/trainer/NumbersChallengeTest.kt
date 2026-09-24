package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Two learners, one code, the same questions: what travels, what is refused, what is compared. */
class NumbersChallengeTest {

    private val picks = NumbersMode(
        listOf(NumbersExercise.Counting, NumbersExercise.Clock), "es", setOf(DrillModifier.Mix, DrillModifier.Fast),
    )

    private val made = requireNotNull(NumbersChallenge.create(picks, Random(11)))

    private fun ready(code: String, language: String = "es") =
        assertIs<ChallengeReading.Ready>(NumbersChallenge.read(code, language)).challenge

    @Test
    fun aCodeSpellsTheSameRunOnTheOtherPhone() {
        val code = made.code(null)
        assertTrue(Regex("^ES-[0-9A-Z]{4}-[0-9A-Z]{4}$").matches(code), code)
        val received = ready(code)
        assertEquals(made, received)
        assertEquals(made.tasks, received.tasks)
        assertNull(received.opponentScore)
        assertEquals(42, ready(made.code(42)).opponentScore)
    }

    /** Only what the questions depend on travels: the exercises and the direction, never Fast. */
    @Test
    fun aChallengeCarriesWhatTheRunAsksAndHowItIsTurned() {
        assertEquals(listOf(NumbersExercise.Counting, NumbersExercise.Clock), made.exercises)
        assertTrue(made.mix)
        assertFalse(made.reverse)
        assertEquals(setOf(DrillModifier.Timed, DrillModifier.Mix), made.mode.modifiers)
    }

    @Test
    fun aCodeIsReadHoweverItWasTyped() {
        val code = made.code(17)
        assertEquals(made.copy(opponentScore = 17), ready(code.lowercase().replace("-", " ")))
        assertEquals(made, ready(made.code(null).replace('0', 'O').replace('1', 'l')))
    }

    @Test
    fun aMistypedCodeIsRefused() {
        val code = made.code(null)
        val slip = code.last().let { if (it == 'Z') 'Y' else 'Z' }
        assertEquals(ChallengeReading.Unreadable, NumbersChallenge.read(code.dropLast(1) + slip, "es"))
        assertEquals(ChallengeReading.Unreadable, NumbersChallenge.read(code.dropLast(2), "es"))
        assertEquals(ChallengeReading.Unreadable, NumbersChallenge.read("", "es"))
    }

    @Test
    fun aCodeForAnotherLanguageSaysWhichOne() {
        assertEquals(ChallengeReading.OtherLanguage("es"), NumbersChallenge.read(made.code(null), "de"))
    }

    /** The script answers nobody: question k stands at its Sprosse whatever came before it. */
    @Test
    fun theQuestionsDoNotAnswerTheLearner() {
        val open = made.open()
        val step = { state: NumbersRunState, intent: NumbersIntent ->
            NumbersRun.reduce(state, intent, null, Random(1)).state
        }
        val missed = step(step(open, NumbersIntent.Reveal), NumbersIntent.ConfirmPending)
        val twice = step(step(missed, NumbersIntent.Reveal), NumbersIntent.ConfirmPending)
        assertEquals(made.tasks[2].drawn, twice.current)
        assertEquals(made.tasks[2].level, twice.currentLevel)
        val right = step(step(twice, NumbersIntent.Submit(twice.currentTask.display)), NumbersIntent.ConfirmPending)
        assertEquals(made.tasks[2].level, right.score)
    }

    @Test
    fun aChallengeBooksNoLadderAndNoRecordAndMeetsTheOtherScore() {
        val played = made.copy(opponentScore = 1).open()
        val right = NumbersRun.reduce(played, NumbersIntent.Submit(played.currentTask.display), null, Random(1)).state
        val closed = NumbersRun.close(right, standingRecord = 0, standingProgress = emptyMap())
        val summary = closed.summary!!
        assertEquals(emptyMap(), closed.progressBookings)
        assertFalse(summary.newRecord)
        assertEquals(ChallengeVerdict.Tied, summary.timed?.verdict)
        assertEquals(made.code(1), summary.timed?.replyCode)
    }

    @Test
    fun theVerdictComparesTheTwoScores() {
        val against = made.copy(opponentScore = 20)
        assertEquals(ChallengeVerdict.Won, TimedOutcome(21, against).verdict)
        assertEquals(ChallengeVerdict.Lost, TimedOutcome(19, against).verdict)
        assertNull(TimedOutcome(19, made).verdict)
    }
}
