package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase

/** The growth ladder: which rung a card stands on, and what outranks what. */
class GrowthStageTests {
    private val now = Box.day1
    private val future = Box.plusDays(now, 5.0)

    private fun stages(state: BoxState, nowMillis: Long = now): Map<String, GrowthStage> =
        BoxEngine.growth(state, nowMillis, Box.TZ).associate { it.cardId to it.stage }

    @Test
    fun everyRungIsReachable() {
        var state = Box.state((1..8).map { Box.word(it) })
        state = BoxEngine.enqueue(state, listOf("w02"))
        state = Box.inject(
            state,
            Box.sched("w03", phase = CardPhase.Learning, stability = 0.5, dueMillis = future, lastReviewMillis = now),
        )
        state = Box.inject(state, Box.sched("w04", stability = 1.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w05", stability = 3.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w06", stability = 9.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w07", stability = 99.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(
            state,
            Box.sched("w08", phase = CardPhase.Relearning, stability = 4.0, dueMillis = future, lastReviewMillis = now),
        )

        assertEquals(
            mapOf(
                "w01" to GrowthStage.Unscheduled,
                "w02" to GrowthStage.Queued,
                "w03" to GrowthStage.Learning,
                "w04" to GrowthStage.Fresh,
                // Past the retired settled bar of 2.0, still short of the one bar that
                // remains: a word this far in is Fresh, and still gets its support.
                "w05" to GrowthStage.Fresh,
                "w06" to GrowthStage.Consolidated,
                "w07" to GrowthStage.Matured,
                "w08" to GrowthStage.Relearning,
            ),
            stages(state),
        )
    }

    @Test
    fun everyBarIsReachedAtItsOwnValue() {
        // Both bars are `>=`, so a card sitting exactly on one has cleared it.
        var state = Box.state((1..2).map { Box.word(it) })
        state = Box.inject(state, Box.sched("w01", stability = 6.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(
            state,
            Box.sched("w02", stability = MATURED_STABILITY, dueMillis = future, lastReviewMillis = now),
        )

        val stages = stages(state)
        assertEquals(GrowthStage.Consolidated, stages["w01"])
        assertEquals(GrowthStage.Matured, stages["w02"])
    }

    @Test
    fun aLapseReportsRelearningWhateverTheCardHadReached() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(
            state,
            Box.sched(
                "w01", phase = CardPhase.Relearning, stability = 99.0,
                dueMillis = future, lastReviewMillis = now, lapses = 1,
            ),
        )

        assertEquals(GrowthStage.Relearning, stages(state)["w01"])
    }

    @Test
    fun suspensionOutranksEveryBar() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        // A leech (8 lapses, auto-suspended) and a hand-suspended matured card
        // both stand outside the ladder, not on the rung their stability bought.
        state = Box.inject(
            state,
            Box.sched(
                "w01", phase = CardPhase.Relearning, stability = 0.2,
                dueMillis = future, lastReviewMillis = now, lapses = 8, suspended = true,
            ),
        )
        state = Box.inject(
            state,
            Box.sched("w02", stability = 99.0, dueMillis = future, lastReviewMillis = now, suspended = true),
        )

        val stages = stages(state)
        assertEquals(GrowthStage.Suspended, stages["w01"])
        assertEquals(GrowthStage.Suspended, stages["w02"])
    }

    @Test
    fun touchedTodayFollowsTheLastAnswerNotTheDueDate() {
        var state = Box.state(listOf(Box.word(1), Box.word(2), Box.word(3)))
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = future, lastReviewMillis = Box.millis(2026, 7, 1, 0, 0)),
        )
        state = Box.inject(
            state,
            Box.sched("w02", dueMillis = future, lastReviewMillis = Box.millis(2026, 6, 30, 23, 59)),
        )

        val growth = BoxEngine.growth(state, now, Box.TZ).associateBy { it.cardId }
        assertTrue(growth.getValue("w01").touchedToday) // answered just after local midnight
        assertFalse(growth.getValue("w02").touchedToday) // a minute before it
        assertFalse(growth.getValue("w03").touchedToday) // never answered at all
    }

    @Test
    fun stabilityIsReportedRawAndUnscheduledCardsCarryNone() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.inject(state, Box.sched("w01", stability = 12.5, dueMillis = future, lastReviewMillis = now))

        val growth = BoxEngine.growth(state, now, Box.TZ).associateBy { it.cardId }
        assertEquals(12.5, growth.getValue("w01").stability)
        assertEquals(0.0, growth.getValue("w02").stability)
    }

    @Test
    fun aCardTheJoinDoesNotCarryHasNoStandingInTheBox() {
        // A schedule outlives a source switch; the card it belongs to may not join.
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(state, Box.sched("w99", dueMillis = future, lastReviewMillis = now))

        assertEquals(listOf("w01"), BoxEngine.growth(state, now, Box.TZ).map { it.cardId })
    }

    @Test
    fun theBoxIsReportedInSeedOrder() {
        val state = Box.state(listOf(Box.word(3), Box.word(1), Box.word(2)))

        assertEquals(listOf("w01", "w02", "w03"), BoxEngine.growth(state, now, Box.TZ).map { it.cardId })
    }
}
