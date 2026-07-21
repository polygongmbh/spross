package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Role

/** Exposure ranking: unit tiers, card dedupe, limit after dedup. */
class ExposureTests {
    private val now = Box.day1

    @Test
    fun tierOrderRelearningQueuedLearningWeakReviewUpcoming() {
        var state = Box.state((1..6).map { Box.word(it) })
        state = Box.inject(state, Box.sched("w04", phase = CardPhase.Relearning, stability = 5.0, dueMillis = now, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w03", phase = CardPhase.Learning, stability = 5.0, dueMillis = now, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w01", stability = 2.0, dueMillis = now, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w02", stability = 20.0, dueMillis = now, lastReviewMillis = now))
        state = state.copy(enqueued = listOf("w05")) // queued new; w06 stays unscheduled

        val ids = BoxEngine.exposureCards(state, now, limit = 10).map { it.id }
        assertEquals(listOf("w04", "w05", "w03", "w01", "w02", "w06"), ids)
    }

    @Test
    fun enqueuedAndRelearningAppearWithoutReviewCards() {
        var state = Box.state((1..3).map { Box.word(it) })
        state = Box.inject(state, Box.sched("w01", phase = CardPhase.Relearning, stability = 3.0, dueMillis = now, lastReviewMillis = now))
        state = state.copy(enqueued = listOf("w02"))

        val ids = BoxEngine.exposureCards(state, now, limit = 10).map { it.id }
        assertEquals(listOf("w01", "w02"), ids.take(2))
        assertTrue("w03" in ids) // unscheduled card still previews as upcoming
    }

    @Test
    fun suspendedUnitsAreExcluded() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.inject(state, Box.sched("w01", stability = 4.0, dueMillis = now, lastReviewMillis = now, suspended = true))
        state = Box.inject(state, Box.sched("w02", stability = 4.0, dueMillis = now, lastReviewMillis = now))

        val ids = BoxEngine.exposureCards(state, now, limit = 10).map { it.id }
        assertFalse("w01" in ids)
        assertTrue("w02" in ids)
    }

    @Test
    fun limitCapsTheResult() {
        val state = Box.state((1..10).map { Box.word(it) })
        assertEquals(3, BoxEngine.exposureCards(state, now, limit = 3).size)
    }

    // Units dedupe by card keeping the lowest (tier, order, key); the limit applies
    // AFTER dedup, so a card with several units cannot crowd others out.
    @Test
    fun dedupesByCardAndAppliesLimitAfterDedup() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.inject(state, Box.sched("w01", stability = 2.0, dueMillis = now, lastReviewMillis = now))
        state = Box.inject(
            state,
            Box.sched("w01", role = Role.Recognize, form = "t1", phase = CardPhase.Relearning, stability = 5.0, dueMillis = now, lastReviewMillis = now),
        )
        state = Box.inject(state, Box.sched("w02", stability = 1.0, dueMillis = now, lastReviewMillis = now))

        // w01 wins via its relearning unit and appears once; w02 still fits at limit 2.
        assertEquals(listOf("w01", "w02"), BoxEngine.exposureCards(state, now, limit = 2).map { it.id })
        assertEquals(listOf("w01"), BoxEngine.exposureCards(state, now, limit = 1).map { it.id })
    }
}
