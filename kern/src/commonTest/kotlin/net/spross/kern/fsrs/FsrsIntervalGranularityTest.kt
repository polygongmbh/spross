package net.spross.kern.fsrs

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
import net.spross.kern.model.MemoryState
import net.spross.kern.model.Rating

/**
 * Graduated intervals are quantized, not day-bucketed by construction: whole
 * days are the REFERENCE convention (what the golden vectors pin), while the
 * product schedules continuously. Pins that the two agree wherever they must.
 */
class FsrsIntervalGranularityTest {

    private val continuous = FsrsParameters(intervalGranularitySeconds = 1L)

    private fun graduated(parameters: FsrsParameters, stability: Double, elapsed: Double) =
        FsrsScheduler(parameters).review(
            SchedulerState(CardPhase.Review, null, MemoryState(stability, difficulty = 5.0)),
            elapsed,
            Rating.Good,
        )

    // At rd = 0.9 the interval IS the stability — the anchor the factor is built for.
    @Test
    fun rawIntervalEqualsStabilityAtNinetyPercent() {
        val fsrs = Fsrs()
        for (stability in listOf(1.0, 2.3065, 8.2956, 498.5)) {
            assertTrue(
                abs(fsrs.intervalRawDays(stability) - stability) < 1e-9,
                "I(0.9, $stability) should equal $stability",
            )
        }
    }

    // Default granularity reproduces the reference exactly: seconds are always the
    // day count times 86_400. This is what keeps FsrsGoldenVectorTest passing.
    @Test
    fun dayGranularityKeepsSecondsAWholeDayMultiple() {
        for (stability in listOf(0.2, 2.3065, 8.2956, 60.0, 400.0)) {
            val outcome = graduated(FsrsParameters(), stability, elapsed = stability)
            assertEquals(outcome.intervalDays * 86_400L, outcome.intervalSeconds)
        }
    }

    // Continuous granularity keeps the fraction the day bucket throws away, and
    // stays within a day of it — same schedule, finer resolution.
    @Test
    fun continuousGranularityKeepsTheFractionalPart() {
        val outcome = graduated(continuous, stability = 8.2956, elapsed = 8.0)
        assertTrue(
            outcome.intervalSeconds % 86_400L != 0L,
            "expected a fractional-day interval, got ${outcome.intervalSeconds} s",
        )
        val bucketed = outcome.intervalDays * 86_400L
        assertTrue(
            abs(outcome.intervalSeconds - bucketed) < 86_400L,
            "continuous ${outcome.intervalSeconds} s should sit within a day of $bucketed s",
        )
    }

    // The floor is a parameter, not a constant: a graduated card never goes
    // sub-day by default, and lowering the floor is all it takes to allow it.
    @Test
    fun minimumIntervalFloorsGraduationAndIsConfigurable() {
        val weak = MemoryState(stability = 0.05, difficulty = 9.0)
        val floored = FsrsScheduler(continuous)
            .review(SchedulerState(CardPhase.Review, null, weak), 0.05, Rating.Hard)
        assertEquals(86_400L, floored.intervalSeconds)

        val subDay = FsrsScheduler(continuous.copy(minimumIntervalSeconds = 60L))
            .review(SchedulerState(CardPhase.Review, null, weak), 0.05, Rating.Hard)
        assertTrue(
            subDay.intervalSeconds in 60L..<86_400L,
            "expected a sub-day interval once the floor allows it, got ${subDay.intervalSeconds} s",
        )
    }

    // The product cap wins over the floor and over the raw interval alike.
    @Test
    fun maximumIntervalCapsTheQuantizedSeconds() {
        val capped = continuous.copy(maximumIntervalDays = 365)
        val outcome = graduated(capped, stability = 4000.0, elapsed = 400.0)
        assertEquals(365 * 86_400L, outcome.intervalSeconds)
    }
}
