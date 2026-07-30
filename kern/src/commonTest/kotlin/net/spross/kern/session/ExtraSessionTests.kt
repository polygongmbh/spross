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

    /** Introduce [count] cards (Easy → straight to Review) at staggered times. */
    private fun boxWithActive(count: Int, total: Int): BoxState {
        var state = Box.state((1..total).map { Box.word(it) })
        var t = day0
        for (n in 1..count) {
            val id = "w" + n.toString().padStart(2, '0')
            state = BoxEngine.enqueue(state, listOf(id))
            state = Box.answered(state, id, Rating.Easy, t)
            t = Box.plusSeconds(t, 60)
        }
        return state
    }

    @Test
    fun extraRoundNeverEmptyWhileBoxHasActiveCards() {
        val state = boxWithActive(8, total = 8)
        // Well before anything is due: the daily compose has no reviews, but the
        // extra round pulls review-ahead cards by soonest due.
        val later = Box.plusSeconds(day0, 3_600)
        val extra = SessionComposer.composeExtraSession(state, later)
        assertFalse(extra.isEmpty)
        // Nothing is due, so every card is named for what it is: pulled forward.
        assertTrue(extra.reviews.isEmpty())
        assertEquals(8, extra.ahead.size)
        // No automatic seed growth — untouched seed words stay out of an extra round.
        assertTrue(extra.newCards.isEmpty())
        assertTrue(extra.unlockedPhrases.isEmpty())
    }

    @Test
    fun enqueuedRespectTheNewWordBudgetInBothRoundTypes() {
        var state = Box.state((1..6).map { Box.word(it) }, Box.config(maxUnsettled = 2))
        state = BoxEngine.enqueue(state, listOf("w03", "w04", "w05"))
        val t = Box.plusSeconds(day0, 600)

        // Both round types surface enqueued cards, but only up to the new-word budget.
        val plan = SessionComposer.composeSession(state, t)
        assertEquals(listOf("w03", "w04"), plan.newCards)
        val extra = SessionComposer.composeExtraSession(state, t)
        assertEquals(listOf("w03", "w04"), extra.newCards)

        // Answering introduces them and dequeues; the rest of the pack waits its turn.
        var after = Box.answered(state, "w03", Rating.Good, Box.plusSeconds(t, 100))
        after = Box.answered(after, "w04", Rating.Good, Box.plusSeconds(t, 200))
        assertEquals(listOf("w05"), after.enqueued)
        // The pack leads; the trickle brings a seed-order word along for variety.
        assertEquals(listOf("w05", "w01"), SessionComposer.composeSession(after, t).newCards)
    }

    @Test
    fun endlessGivesDueAndNewButNeverPullsAhead() {
        var state = Box.state((1..5).map { Box.word(it) }, Box.config(maxUnsettled = 3))
        state = BoxEngine.enqueue(state, listOf("w01"))
        // w01 missed → one 2-minute step, then FSRS.
        state = Box.answered(state, "w01", Rating.Again, day0)

        // 1 min in: w01 is NOT due yet, so endless must not re-show it …
        val soon = SessionComposer.composeEndless(state, Box.plusSeconds(day0, 60))
        assertFalse(soon.reviews.contains("w01"))
        // … it just keeps introducing new cards while the budget has room (3 − 1 = 2).
        assertEquals(listOf("w02", "w03"), soon.newCards)

        // Once w01's step is genuinely due, it comes back as a review.
        val later = SessionComposer.composeEndless(state, Box.plusSeconds(day0, 130))
        assertEquals(listOf("w01"), later.reviews)
    }
}
