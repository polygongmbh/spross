package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.model.CardPhase

/** The consolidated / learning / not-yet-introduced split, box-wide and per area. */
class StatisticsBucketsTests {
    private val now = Box.day1

    @Test
    fun learningIsTheActiveCardsThatHaveNotConsolidatedYet() {
        var state = Box.state(
            listOf(
                Box.word(1, area = "kitchen"), Box.word(2, area = "kitchen"),
                Box.word(3, area = "kitchen"), Box.word(4, area = "kitchen"),
            ),
        )
        val future = Box.plusDays(now, 5.0)
        state = Box.inject(state, Box.sched("w01", stability = 7.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(
            state,
            Box.sched("w02", phase = CardPhase.Learning, stability = 1.0, dueMillis = future, lastReviewMillis = now),
        )

        val stats = BoxEngine.statistics(state, now, Box.TZ)
        assertEquals(1, stats.learningCount) // w02: active, not consolidated
        val kitchen = stats.areas.single()
        assertEquals(1, kitchen.learning)
        assertEquals(2, kitchen.notIntroduced) // w03, w04 never scheduled
        assertEquals(4, kitchen.progressTotal)
        assertEquals(kitchen.total, kitchen.consolidated + kitchen.learning + kitchen.notIntroduced)
    }

    /**
     * Settling is its own count off [GrowthStage.Fresh], never the leftover of another:
     * a matured card is consolidated too, so deriving one bucket from the other would
     * put w04 in both.
     */
    @Test
    fun settlingIsTheReviewCardsStillShortOfTheBar() {
        var state = Box.state((1..4).map { Box.word(it, area = "kitchen") })
        val future = Box.plusDays(now, 5.0)
        // Consolidated (≥ 6.0), still short of matured.
        state = Box.inject(state, Box.sched("w01", stability = 7.0, dueMillis = future, lastReviewMillis = now))
        // In Review, under the bar — the settling bucket itself.
        state = Box.inject(state, Box.sched("w02", stability = 3.0, dueMillis = future, lastReviewMillis = now))
        // Still walking the steps: neither consolidated nor settling.
        state = Box.inject(
            state,
            Box.sched("w03", phase = CardPhase.Learning, stability = 1.0, dueMillis = future, lastReviewMillis = now),
        )
        // Matured: consolidated, and pointedly NOT settling.
        state = Box.inject(state, Box.sched("w04", stability = 99.0, dueMillis = future, lastReviewMillis = now))

        val kitchen = BoxEngine.statistics(state, now, Box.TZ).areas.single()
        assertEquals(4, kitchen.active)
        assertEquals(2, kitchen.consolidated) // w01 and the matured w04
        assertEquals(1, kitchen.settling) // w02 alone
        assertEquals(2, kitchen.learning) // unchanged formula: active - consolidated, so w02 + w03
        assertEquals(kitchen.active, kitchen.consolidated + kitchen.learning)
    }

    @Test
    fun aStaleTotalCannotOverflowTheBuckets() {
        // The join shrank under a statistics value still holding the old schedules.
        val area = AreaStatistics(
            name = "kitchen", total = 1, active = 5, consolidated = 3, settling = 0,
            phrasesLocked = 0, phrasesUnlocked = 0,
        )
        assertEquals(2, area.learning)
        assertEquals(0, area.notIntroduced) // never negative
        assertEquals(5, area.progressTotal) // the introduced cards still fit
    }

    @Test
    fun anAreaWithNothingInItStillHasADenominator() {
        val area = AreaStatistics(
            name = "empty", total = 0, active = 0, consolidated = 0, settling = 0,
            phrasesLocked = 0, phrasesUnlocked = 0,
        )
        assertEquals(0, area.learning)
        assertEquals(0, area.notIntroduced)
        assertEquals(1, area.progressTotal)
    }

    @Test
    fun learningNeverGoesNegative() {
        val stats = BoxStatistics(
            activeCount = 2, consolidatedCount = 5, dueCount = 0, suspendedCount = 0,
            streak = 0, streakHealth = StreakHealth.None, longestStreak = 0, areas = emptyList(),
        )
        assertEquals(0, stats.learningCount)
    }
}
