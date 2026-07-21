package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.box.AnswerStatus
import net.spross.kern.box.Box
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.model.Rating

/** Extra round and endless refill: user agency without unrequested growth. */
class ExtraSessionTests {
    private val day0 = Box.day1

    /** Introduce [count] concepts (produce, Easy → straight to Review) at staggered times. */
    private fun boxWithActive(count: Int, total: Int): BoxState {
        var state = Box.state((1..total).map { Box.word(it) })
        var t = day0
        for (n in 1..count) {
            val id = "w" + n.toString().padStart(2, '0')
            state = BoxEngine.enqueue(state, listOf(id))
            state = Box.answered(state, Box.produce(id), Rating.Easy, t)
            t = Box.plusSeconds(t, 60)
        }
        return state
    }

    @Test
    fun extraRoundNeverEmptyWhileBoxHasActiveUnits() {
        val state = boxWithActive(8, total = 8)
        // Well before anything is due: the daily compose has no reviews, but the
        // extra round pulls review-ahead units by soonest due.
        val later = Box.plusSeconds(day0, 3_600)
        val extra = SessionComposer.composeExtraSession(state, later)
        assertFalse(extra.isEmpty)
        assertEquals(8, extra.reviews.size)
        // No automatic seed growth — the introducible recognize backfill and the
        // untouched seed words stay out of an extra round.
        assertTrue(extra.newUnits.isEmpty())
        assertTrue(extra.unlockedPhrases.isEmpty())
    }

    @Test
    fun enqueuedRespectPoolBudgetInBothRoundTypes() {
        var state = Box.state((1..6).map { Box.word(it) }, Box.config(maxLearning = 2))
        state = BoxEngine.enqueue(state, listOf("w03", "w04", "w05"))
        val t = Box.plusSeconds(day0, 600)

        // Both round types surface enqueued produce units, but only up to the pool budget.
        val plan = SessionComposer.composeSession(state, t)
        assertEquals(listOf(Box.produce("w03"), Box.produce("w04")), plan.newUnits)
        val extra = SessionComposer.composeExtraSession(state, t)
        assertEquals(listOf(Box.produce("w03"), Box.produce("w04")), extra.newUnits)

        // Answering introduces within the budget; the filled pool then defers the rest.
        var after = Box.answered(state, Box.produce("w03"), Rating.Good, Box.plusSeconds(t, 100))
        after = Box.answered(after, Box.produce("w04"), Rating.Good, Box.plusSeconds(t, 200))
        assertEquals(listOf("w05"), after.enqueued)
        val blocked = BoxEngine.answer(after, Box.produce("w05"), Rating.Good, Box.plusSeconds(t, 300), Box.TZ)
        assertEquals(AnswerStatus.DroppedPoolFull, blocked.status)
        assertNull(blocked.state.scheduling[Box.produce("w05")])
        assertEquals(listOf("w05"), blocked.state.enqueued)
    }

    @Test
    fun nonEnqueuedIntroductionNoOpsWhenPoolFull() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)), Box.config(maxLearning = 1))
        state = Box.answered(state, Box.produce("w01"), Rating.Good, day0) // pool of 1 now full
        val blocked = BoxEngine.answer(state, Box.produce("w02"), Rating.Good, Box.plusSeconds(day0, 60), Box.TZ)
        assertEquals(AnswerStatus.DroppedPoolFull, blocked.status)
        assertNull(blocked.state.scheduling[Box.produce("w02")])
    }

    @Test
    fun endlessGivesDueAndNewButNeverPullsAhead() {
        var state = Box.state((1..5).map { Box.word(it) }, Box.config(maxLearning = 3))
        state = BoxEngine.enqueue(state, listOf("w01"))
        // w01 → learning; its next step is due in 10 min (not now).
        state = Box.answered(state, Box.produce("w01"), Rating.Good, day0)

        // 1 min in: w01 is NOT due yet, so endless must not re-show it …
        val soon = SessionComposer.composeEndless(state, Box.plusSeconds(day0, 60))
        assertFalse(soon.reviews.contains(Box.produce("w01")))
        // … it just keeps introducing new units while the pool has room (3 − 1 = 2).
        assertEquals(listOf(Box.produce("w02"), Box.produce("w03")), soon.newUnits)

        // Once w01's step is genuinely due, it comes back as a review.
        val later = SessionComposer.composeEndless(state, Box.plusSeconds(day0, 700))
        assertEquals(listOf(Box.produce("w01")), later.reviews)
    }
}
