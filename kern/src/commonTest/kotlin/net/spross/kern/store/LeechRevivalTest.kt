package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.Box

/** revivingLeechSuspensions: the leech-era auto-suspend migration (delete at 7.0+). */
class LeechRevivalTest {
    private val now = Box.day1

    @Test
    fun revivesASuspendedCardWithTwoOrMoreLapses() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = now, lastReviewMillis = now, lapses = 2, suspended = true),
        )

        val revived = state.revivingLeechSuspensions()

        assertFalse(revived.scheduling.getValue("w01").suspended)
    }

    @Test
    fun leavesAHandSuspendedCardWithFewerThanTwoLapsesAlone() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = now, lastReviewMillis = now, lapses = 0, suspended = true),
        )

        val revived = state.revivingLeechSuspensions()

        assertTrue(revived.scheduling.getValue("w01").suspended)
    }

    @Test
    fun leavesAnActiveCardWithLapsesAlone() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = now, lastReviewMillis = now, lapses = 5, suspended = false),
        )

        val revived = state.revivingLeechSuspensions()

        assertFalse(revived.scheduling.getValue("w01").suspended)
        assertEquals(state, revived)
    }

    @Test
    fun revivingChangesNothingButTheFlag() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(
            state,
            Box.sched("w01", stability = 3.5, dueMillis = now, lastReviewMillis = now, lapses = 2, suspended = true),
        )

        val revived = state.revivingLeechSuspensions().scheduling.getValue("w01")
        val original = state.scheduling.getValue("w01")

        assertEquals(original.copy(suspended = false), revived)
    }
}
