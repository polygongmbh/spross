package net.spross.kern.fsrs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.fsrsParameters
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.CardPhase
import net.spross.kern.model.MemoryState
import net.spross.kern.model.Rating

/**
 * Behavioral expectations under the PRODUCT configuration ([BoxConfig.product],
 * mapped by [fsrsParameters]). Self-computed, NOT reference vectors — they pin
 * contract choices, not upstream numerics.
 */
class FsrsBehavioralTest {

    private val productParameters = BoxConfig.product().fsrsParameters()

    // The one place the shipped ladder is spelled out: everything else derives it from
    // [BoxConfig], so this is the tripwire that catches a change to the numbers.
    @Test
    fun theProductShipsTheTenMinuteToOneWeekLadder() {
        assertEquals(listOf(600L, 86_400L, 259_200L, 604_800L), productParameters.stepsSeconds)
        assertEquals(0.8, productParameters.desiredRetention)
        assertEquals(365, productParameters.maximumIntervalDays)
    }

    // At retention 0.8 the interval modifier is ~3.32, so S = 5 schedules 17 days.
    @Test
    fun retentionPointEightSchedulesRoughlyThreePointThreeTimesStability() {
        val fsrs = Fsrs(productParameters)
        val interval = fsrs.intervalDays(5.0)
        assertEquals(17, interval)
        val ratio = interval / 5.0
        assertTrue(ratio > 3.2 && ratio < 3.5, "expected ~3.3x stability, got $ratio")
    }

    // Product ladder opens at [10m]: a lapsed review card comes back past the end of
    // a session, not inside it — the breadth ruling, unchanged by the step length.
    @Test
    fun productRelearningStepReturnsLapsedCardAfterTheSession() {
        val scheduler = FsrsScheduler(productParameters)
        val memory = MemoryState(stability = 5.0, difficulty = 5.0)
        val state = SchedulerState(CardPhase.Review, null, memory)

        val lapse = scheduler.review(state, 17.0, Rating.Again)
        assertEquals(CardPhase.Relearning, lapse.phase)
        assertEquals(0, lapse.stepIndex)
        assertEquals(productParameters.stepsSeconds[0], lapse.intervalSeconds)
        assertEquals(0, lapse.intervalDays)

        // The retry ten minutes later graduates back to Review.
        val retry = scheduler.review(
            SchedulerState(lapse.phase, lapse.stepIndex, lapse.memory),
            productParameters.stepsSeconds[0] / 86_400.0,
            Rating.Good,
        )
        assertEquals(CardPhase.Review, retry.phase)
        assertTrue(retry.intervalDays >= 1)
        assertTrue(retry.intervalSeconds >= 86_400L)
    }

    // Product maximum interval 365 caps every schedule regardless of stability.
    @Test
    fun productMaximumIntervalCapsAtOneYear() {
        val fsrs = Fsrs(productParameters)
        assertEquals(365, fsrs.intervalDays(400.0))
        assertEquals(365, fsrs.intervalDays(36500.0))
    }

    // The bar sits in the gap between the two first answers that pass: FSRS-6
    // S0(Good) = 2.3065 stays UNDER it while S0(Easy) = 8.2956 clears it. That gap is
    // what the one landed bar is calibrated for — a first Good is as easily an emoji
    // recognized as a word recalled, so the word keeps its support into the next
    // review, and only an Easy (earned by a fast Knew, never picked) lands on sight.
    @Test
    fun theLandedBarSeparatesAGoodFirstAnswerFromAnEasyOne() {
        val fsrs = Fsrs(productParameters)
        assertTrue(fsrs.nextMemory(null, 0.0, Rating.Good).stability < 6.0)
        assertTrue(fsrs.nextMemory(null, 0.0, Rating.Easy).stability >= 6.0)
    }
}
