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

    @Test
    fun aStaleTotalCannotOverflowTheBuckets() {
        // The join shrank under a statistics value still holding the old schedules.
        val area = AreaStatistics(
            name = "kitchen", total = 1, active = 5, consolidated = 3,
            phrasesLocked = 0, phrasesUnlocked = 0,
        )
        assertEquals(2, area.learning)
        assertEquals(0, area.notIntroduced) // never negative
        assertEquals(5, area.progressTotal) // the introduced cards still fit
    }

    @Test
    fun anAreaWithNothingInItStillHasADenominator() {
        val area = AreaStatistics(
            name = "empty", total = 0, active = 0, consolidated = 0,
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
