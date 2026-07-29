package net.spross.kern.fsrs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
import net.spross.kern.model.MemoryState
import net.spross.kern.model.Rating

/**
 * Behavioral expectations under the PRODUCT configuration (retention 0.8,
 * maximum interval 365, learning [3m], relearning [10m], continuous intervals).
 * Self-computed, NOT reference vectors — they pin contract choices, not upstream
 * numerics. Keep in step with `model/Config.kt`.
 */
class FsrsBehavioralTest {

    private val productParameters = FsrsParameters(
        desiredRetention = 0.8,
        maximumIntervalDays = 365,
        learningStepsSeconds = listOf(180L),
        relearningStepsSeconds = listOf(600L),
        intervalGranularitySeconds = 1L,
    )

    // At retention 0.8 the interval modifier is ~3.32, so S = 5 schedules 17 days.
    @Test
    fun retentionPointEightSchedulesRoughlyThreePointThreeTimesStability() {
        val fsrs = Fsrs(productParameters)
        val interval = fsrs.intervalDays(5.0)
        assertEquals(17, interval)
        val ratio = interval / 5.0
        assertTrue(ratio > 3.2 && ratio < 3.5, "expected ~3.3x stability, got $ratio")
    }

    // Product relearning [10m]: a lapsed review card comes back past the end of a
    // session, not inside it — the breadth ruling, unchanged by the learning step.
    @Test
    fun productRelearningStepReturnsLapsedCardAfterTheSession() {
        val scheduler = FsrsScheduler(productParameters)
        val memory = MemoryState(stability = 5.0, difficulty = 5.0)
        val state = SchedulerState(CardPhase.Review, null, memory)

        val lapse = scheduler.review(state, 17.0, Rating.Again)
        assertEquals(CardPhase.Relearning, lapse.phase)
        assertEquals(0, lapse.stepIndex)
        assertEquals(600L, lapse.intervalSeconds)
        assertEquals(0, lapse.intervalDays)

        // The retry ten minutes later graduates back to Review.
        val retry = scheduler.review(
            SchedulerState(lapse.phase, lapse.stepIndex, lapse.memory),
            600.0 / 86_400.0,
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

    // FSRS-6 S0(Good) = 2.3065 crosses the settled threshold (2.0) on the first
    // Good, which with a single learning step is also graduation; S0(Hard) = 1.2931
    // does not — so a word you knew settles at once and one you struggled with does not.
    @Test
    fun settledThresholdCrossesOnAGoodFirstAnswer() {
        val fsrs = Fsrs(productParameters)
        assertTrue(fsrs.nextMemory(null, 0.0, Rating.Good).stability >= 2.0)
        assertTrue(fsrs.nextMemory(null, 0.0, Rating.Hard).stability < 2.0)
    }
}
