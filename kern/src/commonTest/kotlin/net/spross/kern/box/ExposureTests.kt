package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase

/** Exposure ranking: urgency, suspension, limit. */
class ExposureTests {
    private val now = Box.day1

    @Test
    fun queuedFirstThenWeakestMemoryWhateverThePhaseThenUpcoming() {
        var state = Box.state((1..6).map { Box.word(it) })
        state = Box.inject(state, Box.sched("w04", phase = CardPhase.Relearning, stability = 10.0, dueMillis = now, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w03", phase = CardPhase.Learning, stability = 3.0, dueMillis = now, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w01", stability = 2.0, dueMillis = now, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w02", stability = 40.0, dueMillis = now, lastReviewMillis = now))
        state = state.copy(queued = listOf("w05")) // queued; w06 stays unscheduled

        val ids = Exposure.exposureCards(state, limit = 10).map { it.id }
        assertEquals(listOf("w05", "w01", "w03", "w04", "w02", "w06"), ids)
    }

    @Test
    fun suspendedCardsAreExcluded() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.inject(state, Box.sched("w01", stability = 4.0, dueMillis = now, lastReviewMillis = now, suspended = true))
        state = Box.inject(state, Box.sched("w02", stability = 4.0, dueMillis = now, lastReviewMillis = now))

        val ids = Exposure.exposureCards(state, limit = 10).map { it.id }
        assertFalse("w01" in ids)
        assertTrue("w02" in ids)
    }

    @Test
    fun limitCapsTheResult() {
        val state = Box.state((1..10).map { Box.word(it) })
        assertEquals(3, Exposure.exposureCards(state, limit = 3).size)
    }
}
