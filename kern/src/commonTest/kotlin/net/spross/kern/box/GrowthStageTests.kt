package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase

/** The growth ladder: which Sprosse a card stands on, and what outranks what. */
class GrowthStageTests {
    private val now = Box.day1
    private val future = Box.plusDays(now, 5.0)

    private fun stages(state: BoxState, nowMillis: Long = now): Map<String, GrowthStage> =
        BoxEngine.growth(state, nowMillis, Box.TZ).associate { it.cardId to it.stage }

    @Test
    fun everySprosseIsReachable() {
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
                "w03" to GrowthStage.Fresh,
                "w04" to GrowthStage.Fresh,
                // Past the retired settled bar of 2.0, still short of GROWING_STABILITY
                // (6.0): a word this far in is Fresh, and still gets its support.
                "w05" to GrowthStage.Fresh,
                "w06" to GrowthStage.Growing,
                "w07" to GrowthStage.Settled,
                "w08" to GrowthStage.Lapsed,
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
            Box.sched("w02", stability = SETTLED_STABILITY, dueMillis = future, lastReviewMillis = now),
        )

        val stages = stages(state)
        assertEquals(GrowthStage.Growing, stages["w01"])
        assertEquals(GrowthStage.Settled, stages["w02"])
    }

    /**
     * The stability bars outrank the FSRS phase: a lapse that kept the growing bar is still
     * an arrived word, and only one that fell under it reads lapsed.
     */
    @Test
    fun aRelearningCardReadsLapsedOnlyUnderTheGrowingBar() {
        var state = Box.state((1..2).map { Box.word(it) })
        state = Box.inject(
            state,
            Box.sched("w01", phase = CardPhase.Relearning, stability = 9.0, dueMillis = future, lastReviewMillis = now),
        )
        state = Box.inject(
            state,
            Box.sched("w02", phase = CardPhase.Relearning, stability = 3.0, dueMillis = future, lastReviewMillis = now),
        )

        assertEquals(GrowthStage.Growing, stages(state)["w01"])
        assertTrue(BoxEngine.hasArrived(state, "w01"))
        assertEquals(GrowthStage.Lapsed, stages(state)["w02"])
    }

    /** How long a word keeps: nothing for an untouched word, whole days, never under one. */
    @Test
    fun aWordKeepsWholeDaysAndAnUntouchedOneSaysNothing() {
        fun lasts(stability: Double) = CardGrowth("w01", GrowthStage.Fresh, stability, false).lastsDays
        assertNull(lasts(0.0))
        assertEquals(1, lasts(0.2))
        assertEquals(10, lasts(9.6))
    }

    @Test
    fun suspensionOutranksEveryBar() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        // A leech (suspended) and a hand-suspended settled card
        // both stand outside the ladder, not on the Sprosse their stability bought.
        state = Box.inject(
            state,
            Box.sched(
                "w01", phase = CardPhase.Relearning, stability = 0.2,
                dueMillis = future, lastReviewMillis = now, suspended = true,
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
    fun oneCardAskedByNameAnswersAsTheWholeBoxWould() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.inject(state, Box.sched("w01", stability = 12.5, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w99", dueMillis = future, lastReviewMillis = now))

        val whole = BoxEngine.growth(state, now, Box.TZ).associateBy { it.cardId }
        assertEquals(whole.getValue("w01"), BoxEngine.cardGrowth(state, "w01", now, Box.TZ))
        assertEquals(whole.getValue("w02"), BoxEngine.cardGrowth(state, "w02", now, Box.TZ))
        // The join does not carry it, so it has no standing to report.
        assertNull(BoxEngine.cardGrowth(state, "w99", now, Box.TZ))
    }

    @Test
    fun theBoxIsReportedInSeedOrder() {
        val state = Box.state(listOf(Box.word(3), Box.word(1), Box.word(2)))

        assertEquals(listOf("w01", "w02", "w03"), BoxEngine.growth(state, now, Box.TZ).map { it.cardId })
    }
}
