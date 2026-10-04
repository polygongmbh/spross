package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase

/** The settled / learning / not-yet-introduced split, box-wide and per area. */
class StatisticsBucketsTests {
    private val now = Box.day1

    @Test
    fun learningIsTheActiveCardsThatHaveNotSettledYet() {
        var state = Box.state(
            listOf(
                Box.word(1, area = "kitchen"), Box.word(2, area = "kitchen"),
                Box.word(3, area = "kitchen"), Box.word(4, area = "kitchen"),
            ),
        )
        val future = Box.plusDays(now, 5.0)
        state = Box.inject(state, Box.sched("w01", stability = 35.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(
            state,
            Box.sched("w02", phase = CardPhase.Learning, stability = 1.0, dueMillis = future, lastReviewMillis = now),
        )

        val stats = BoxEngine.statistics(state, now, Box.TZ)
        assertEquals(1, stats.learningCount) // w02: active, not settled
        val kitchen = stats.areas.single()
        assertEquals(1, kitchen.learning)
        assertEquals(2, kitchen.notIntroduced) // w03, w04 never scheduled
        assertEquals(4, kitchen.progressTotal)
        assertEquals(kitchen.total, kitchen.settled + kitchen.learning + kitchen.notIntroduced)
    }

    /**
     * A Growing-stage card counts toward [AreaStatistics.learning] exactly like a Fresh
     * one — the bar is a two-way split (jade vs. everything else active), never the
     * badge's finer four-way grain.
     */
    @Test
    fun aGrowingCardCountsTowardLearningJustLikeAFreshOne() {
        var state = Box.state((1..4).map { Box.word(it, area = "kitchen") })
        val future = Box.plusDays(now, 5.0)
        // Settled (≥ SETTLED_STABILITY).
        state = Box.inject(state, Box.sched("w01", stability = 35.0, dueMillis = future, lastReviewMillis = now))
        // Fresh: in Review, short of the growing bar.
        state = Box.inject(state, Box.sched("w02", stability = 3.0, dueMillis = future, lastReviewMillis = now))
        // Growing: past the growing bar, short of settled — same bucket as Fresh here.
        state = Box.inject(state, Box.sched("w03", stability = 9.0, dueMillis = future, lastReviewMillis = now))
        // Settled, well past the bar.
        state = Box.inject(state, Box.sched("w04", stability = 99.0, dueMillis = future, lastReviewMillis = now))

        val kitchen = BoxEngine.statistics(state, now, Box.TZ).areas.single()
        assertEquals(4, kitchen.active)
        assertEquals(2, kitchen.settled) // w01 and the settled w04
        assertEquals(2, kitchen.learning) // w02 (Fresh) and w03 (Growing) alike
        assertEquals(kitchen.active, kitchen.settled + kitchen.learning)
    }

    /** Packed-but-unintroduced cards get their own bucket — the bar's clay segment. */
    @Test
    fun queuedCountsCardsPackedButNotYetIntroduced() {
        var state = Box.state((1..3).map { Box.word(it, area = "kitchen") } + Box.word(4, area = "office"))
        state = BoxEngine.enqueue(state, listOf("w01", "w04"))

        val stats = BoxEngine.statistics(state, now, Box.TZ)
        val kitchen = stats.areas.single { it.name == "kitchen" }
        assertEquals(1, kitchen.queued) // w01 only — w02/w03 were never packed
        assertEquals(0, kitchen.active)
        assertEquals(1, stats.areas.single { it.name == "office" }.queued)
    }

    @Test
    fun aStaleTotalCannotOverflowTheBuckets() {
        // The join shrank under a statistics value still holding the old schedules.
        val area = AreaStatistics(
            name = "kitchen", total = 1, active = 5, settled = 3, queued = 0,
            phrasesLocked = 0, phrasesUnlocked = 0,
        )
        assertEquals(2, area.learning)
        assertEquals(0, area.notIntroduced) // never negative
        assertEquals(5, area.progressTotal) // the introduced cards still fit
    }

    @Test
    fun anAreaWithNothingInItStillHasADenominator() {
        val area = AreaStatistics(
            name = "empty", total = 0, active = 0, settled = 0, queued = 0,
            phrasesLocked = 0, phrasesUnlocked = 0,
        )
        assertEquals(0, area.learning)
        assertEquals(0, area.notIntroduced)
        assertEquals(1, area.progressTotal)
    }

    /**
     * Mature backs the jade area-complete mark: every ACTIVE card settled, and at
     * least one. Whether every card in the area has even been packed yet is a separate
     * question a screen answers off its own pack/unpack emptiness, not off this field.
     */
    @Test
    fun matureRequiresEveryActiveCardSettledAndAtLeastOne() {
        var state = Box.state((1..2).map { Box.word(it, area = "kitchen") })
        val future = Box.plusDays(now, 5.0)
        state = Box.inject(state, Box.sched("w01", stability = 35.0, dueMillis = future, lastReviewMillis = now))
        // w02 active but only Fresh — not every active card has settled yet.
        state = Box.inject(state, Box.sched("w02", stability = 3.0, dueMillis = future, lastReviewMillis = now))
        assertFalse(BoxEngine.statistics(state, now, Box.TZ).areas.single().mature)

        state = Box.inject(state, Box.sched("w02", stability = 40.0, dueMillis = future, lastReviewMillis = now))
        assertTrue(BoxEngine.statistics(state, now, Box.TZ).areas.single().mature)

        // An area with nothing active at all has nothing to call mature.
        val empty = AreaStatistics(
            name = "empty", total = 0, active = 0, settled = 0, queued = 0,
            phrasesLocked = 0, phrasesUnlocked = 0,
        )
        assertFalse(empty.mature)
    }

    @Test
    fun learningNeverGoesNegative() {
        val stats = BoxStatistics(
            activeCount = 2, settledCount = 5, dueCount = 0, suspendedCount = 0,
            streak = 0, streakHealth = StreakHealth.None, longestStreak = 0, areas = emptyList(),
        )
        assertEquals(0, stats.learningCount)
    }
}
