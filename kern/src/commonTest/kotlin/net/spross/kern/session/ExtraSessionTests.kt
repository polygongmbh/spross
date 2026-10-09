package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxEngine
import net.spross.kern.model.Rating

/**
 * Rounds the learner asks for — the extra round off a finished day, and each endless refill.
 * Both are [SessionComposer.composeRound]: user agency decides WHETHER a round opens, never
 * what goes in it, so neither comes back all first sights or all cards dragged forward.
 */
class ExtraSessionTests {
    private val day0 = Box.day1

    @Test
    fun queuedLeadInEveryRoundAndUnqueueOnAnswer() {
        var state = Box.state((1..10).map { Box.word(it) })
        state = BoxEngine.queue(state, listOf("w03", "w04", "w05"))
        val t = Box.plusSeconds(day0, 600)

        // The queue comes first, in the order it was queued, then the round fills out in
        // seed order — one rule, so the day's round and an asked-for one agree.
        val expected = listOf("w03", "w04", "w05", "w01", "w02", "w06", "w07")
        assertEquals(expected, SessionComposer.composeSession(state, t, Box.TZ).newCards)
        assertEquals(expected, SessionComposer.composeRound(state, t, Box.TZ).newCards)

        // Answering introduces them and unqueues.
        var after = Box.answered(state, "w03", Rating.Good, Box.plusSeconds(t, 100))
        after = Box.answered(after, "w04", Rating.Good, Box.plusSeconds(t, 200))
        assertEquals(listOf("w05"), after.queued)
        assertEquals("w05", SessionComposer.composeSession(after, t, Box.TZ).newCards.first())
    }

    @Test
    fun aCardOnItsLearningStepIsNotPulledBackBeforeItIsDue() {
        var state = Box.state((1..5).map { Box.word(it) })
        state = BoxEngine.queue(state, listOf("w01"))
        // w01 missed → the ladder's first 10-minute step, then FSRS.
        state = Box.answered(state, "w01", Rating.Again, day0)

        // 1 min in, w01 is NOT due — it may be pulled forward like any other scheduled card,
        // but never counted as due work.
        val soon = SessionComposer.composeRound(state, Box.plusSeconds(day0, 60), Box.TZ)
        assertFalse(soon.reviews.contains("w01"))
        assertEquals(listOf("w02", "w03", "w04", "w05"), soon.newCards)

        // Once its step is genuinely due, it comes back as a review.
        val later = SessionComposer.composeRound(state, Box.plusSeconds(day0, Box.steps[0]), Box.TZ)
        assertEquals(listOf("w01"), later.reviews)
    }
}
