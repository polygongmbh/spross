package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Due ordering: overdue days first, de-correlated inside the day so cards
 * introduced together stop traveling as a block the learner can answer from
 * sequence rather than from memory.
 */
class DueOrderTests {
    private val now = Box.day1

    /** Cards `w01..wNN`, each due at [firstDue] plus a few seconds, all on one day. */
    private fun secondsApart(count: Int, firstDue: Long): BoxState {
        var state = Box.state((1..count).map { Box.word(it) })
        for (n in 1..count) {
            state = Box.inject(
                state,
                Box.sched(
                    "w" + n.toString().padStart(2, '0'),
                    dueMillis = Box.plusSeconds(firstDue, (n - 1) * 7L),
                    lastReviewMillis = Box.plusDays(firstDue, -10.0),
                ),
            )
        }
        return state
    }

    @Test
    fun cardsDueSecondsApartComeBackOutOfSeedOrder() {
        val state = secondsApart(6, Box.plusSeconds(now, -3600))
        val seedOrder = (1..6).map { "w0$it" }
        val order = BoxEngine.dueNow(state, now)
        assertEquals(listOf("w06", "w01", "w04", "w05", "w03", "w02"), order)
        assertNotEquals(seedOrder, order)
    }

    @Test
    fun anEarlierDueDayAlwaysComesFirst() {
        var state = Box.state((1..6).map { Box.word(it) })
        // w04..w06 due yesterday, w01..w03 due today — seed order fights day order.
        for (n in 1..6) {
            val day = if (n <= 3) now else Box.plusDays(now, -1.0)
            state = Box.inject(
                state,
                Box.sched(
                    "w0$n",
                    dueMillis = Box.plusSeconds(day, -3600),
                    lastReviewMillis = Box.plusDays(now, -10.0),
                ),
            )
        }
        val order = BoxEngine.dueNow(state, now)
        assertEquals(setOf("w04", "w05", "w06"), order.take(3).toSet())
        assertEquals(setOf("w01", "w02", "w03"), order.drop(3).toSet())
    }

    /**
     * A shaky word leads a settled one however far behind the settled one has fallen:
     * delay costs almost nothing at high stability and plenty at low, so a capped sitting
     * is spent where a review still changes the outcome.
     */
    @Test
    fun anUnconsolidatedWordLeadsAWholeDayOfSettledOnes() {
        var state = Box.state((1..4).map { Box.word(it) })
        // w01..w03 settled and three days overdue; w04 shaky and due only now.
        for (n in 1..3) {
            state = Box.inject(
                state,
                Box.sched(
                    "w0$n",
                    stability = 30.0,
                    dueMillis = Box.plusDays(now, -3.0),
                    lastReviewMillis = Box.plusDays(now, -40.0),
                ),
            )
        }
        state = Box.inject(
            state,
            Box.sched(
                "w04",
                stability = 1.5,
                dueMillis = now,
                lastReviewMillis = Box.plusDays(now, -2.0),
            ),
        )
        assertEquals("w04", BoxEngine.dueNow(state, now).first())
    }

    @Test
    fun theSameStateOrdersIdenticallyEveryTime() {
        val state = secondsApart(6, Box.plusSeconds(now, -3600))
        assertEquals(BoxEngine.dueNow(state, now), BoxEngine.dueNow(state, now))
    }

    @Test
    fun theOrderWithinADayDiffersFromDayToDay() {
        val today = BoxEngine.dueNow(secondsApart(3, Box.plusSeconds(now, -3600)), now)
        val twoDaysAgo = BoxEngine.dueNow(
            secondsApart(3, Box.plusSeconds(Box.plusDays(now, -2.0), -3600)),
            now,
        )
        assertEquals(listOf("w01", "w03", "w02"), today)
        assertEquals(listOf("w02", "w03", "w01"), twoDaysAgo)
    }
}
