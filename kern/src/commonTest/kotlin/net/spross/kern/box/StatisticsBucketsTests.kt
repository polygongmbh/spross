package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase

/** The all-settled / all-growing / not-yet-introduced split, box-wide and per area. */
class StatisticsBucketsTests {
    private val now = Box.day1

    @Test
    fun allGrowingIsTheActiveCardsThatHaveNotSettledYet() {
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
        assertEquals(1, stats.allGrowingCount) // w02: active, not settled
        val kitchen = stats.areas.single()
        assertEquals(1, kitchen.allGrowing)
        assertEquals(2, kitchen.notIntroduced) // w03, w04 never scheduled
        assertEquals(4, kitchen.progressTotal)
        assertEquals(kitchen.total, kitchen.allSettled + kitchen.allGrowing + kitchen.notIntroduced)
    }

    /** Each active card counts on its own stage; the two halves of the split are sums of them. */
    @Test
    fun eachActiveCardCountsOnItsOwnStage() {
        var state = Box.state((1..5).map { Box.word(it, area = "kitchen") })
        val future = Box.plusDays(now, 5.0)
        state = Box.inject(state, Box.sched("w01", stability = 3.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w02", stability = 9.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(
            state,
            Box.sched("w03", phase = CardPhase.Relearning, stability = 4.0, dueMillis = future, lastReviewMillis = now),
        )
        state = Box.inject(state, Box.sched("w04", stability = 35.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w05", stability = MATURED_STABILITY, dueMillis = future, lastReviewMillis = now))

        val kitchen = BoxEngine.statistics(state, now, Box.TZ).areas.single()
        assertEquals(StageCounts(fresh = 1, growing = 1, lapsed = 1, settled = 1, matured = 1), kitchen.stages)
        assertEquals(3, kitchen.allGrowing)
        assertEquals(2, kitchen.allSettled)
    }

    /** Queued-but-unintroduced cards get their own bucket — the bar's clay segment. */
    @Test
    fun queuedCountsCardsQueuedButNotYetIntroduced() {
        var state = Box.state((1..3).map { Box.word(it, area = "kitchen") } + Box.word(4, area = "office"))
        state = BoxEngine.queue(state, listOf("w01", "w04"))

        val stats = BoxEngine.statistics(state, now, Box.TZ)
        val kitchen = stats.areas.single { it.name == "kitchen" }
        assertEquals(1, kitchen.queued) // w01 only — w02/w03 were never queued
        assertEquals(0, kitchen.active)
        assertEquals(1, stats.areas.single { it.name == "office" }.queued)
    }

    @Test
    fun aStaleTotalCannotOverflowTheBuckets() {
        // The join shrank under a statistics value still holding the old schedules.
        val area = AreaStatistics(
            name = "kitchen", total = 1, stages = StageCounts(fresh = 2, settled = 3),
        )
        assertEquals(2, area.allGrowing)
        assertEquals(0, area.notIntroduced) // never negative
        assertEquals(5, area.progressTotal) // the introduced cards still fit
    }

    @Test
    fun anAreaWithNothingInItStillHasADenominator() {
        val area = AreaStatistics(
            name = "empty", total = 0, stages = StageCounts(),
        )
        assertEquals(0, area.allGrowing)
        assertEquals(0, area.notIntroduced)
        assertEquals(1, area.progressTotal)
    }

    /**
     * fullySettled backs the jade area-complete mark: every ACTIVE card settled, and at
     * least one. Whether every card in the area has even been queued yet is a separate
     * question a screen answers off its own queue/unqueue emptiness, not off this field.
     */
    @Test
    fun fullySettledRequiresEveryActiveCardSettledAndAtLeastOne() {
        var state = Box.state((1..2).map { Box.word(it, area = "kitchen") })
        val future = Box.plusDays(now, 5.0)
        state = Box.inject(state, Box.sched("w01", stability = 35.0, dueMillis = future, lastReviewMillis = now))
        // w02 active but only Fresh — not every active card has settled yet.
        state = Box.inject(state, Box.sched("w02", stability = 3.0, dueMillis = future, lastReviewMillis = now))
        assertFalse(BoxEngine.statistics(state, now, Box.TZ).areas.single().fullySettled)

        state = Box.inject(state, Box.sched("w02", stability = 40.0, dueMillis = future, lastReviewMillis = now))
        assertTrue(BoxEngine.statistics(state, now, Box.TZ).areas.single().fullySettled)

        // An area with nothing active at all has nothing to call fully settled.
        val empty = AreaStatistics(
            name = "empty", total = 0, stages = StageCounts(),
        )
        assertFalse(empty.fullySettled)
    }
}
