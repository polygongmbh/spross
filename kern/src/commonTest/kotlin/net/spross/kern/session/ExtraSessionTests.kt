package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.model.Rating

/**
 * Rounds the learner asks for — the extra round off a finished day, and each endless refill.
 * Both are [SessionComposer.composeRound]: user agency decides WHETHER a round opens, never
 * what goes in it. They used to have a composer each, which is the whole reason this file
 * exists — one came back all first sights, the other all cards dragged forward.
 */
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
    fun anAskedForRoundIsNeverEmptyWhileTheBoxHasAnythingLeft() {
        val state = boxWithActive(8, total = 8)
        // Well before anything is due, and the catalog spent: the round is carried entirely by
        // cards pulled forward, soonest due first, rather than coming back as an empty screen.
        val later = Box.plusSeconds(day0, 3_600)
        val round = SessionComposer.composeRound(state, later, Box.TZ)
        assertFalse(round.isEmpty)
        // Nothing is due, so every card is named for what it is: pulled forward.
        assertTrue(round.reviews.isEmpty())
        assertEquals(0, round.freshCount)
        assertEquals(SessionComposer.SESSION_FLOOR_CARDS, round.ahead.size)
    }

    @Test
    fun aRoundReachesForNewWordsOnceTheBoxHasRoomForThem() {
        // Same eight active cards, but catalog left over: the round stops being all recall.
        val state = boxWithActive(8, total = 30)
        val round = SessionComposer.composeRound(state, Box.plusSeconds(day0, 3_600), Box.TZ)
        assertTrue(round.freshCount > 0)
        assertEquals(SessionComposer.SESSION_FLOOR_CARDS, round.cardCount)
    }

    @Test
    fun enqueuedLeadInEveryRoundAndDequeueOnAnswer() {
        var state = Box.state((1..10).map { Box.word(it) })
        state = BoxEngine.enqueue(state, listOf("w03", "w04", "w05"))
        val t = Box.plusSeconds(day0, 600)

        // The pack comes first, then the round fills out in seed order — one rule, so the
        // day's round and an asked-for one agree.
        val expected = listOf("w03", "w04", "w05", "w01", "w02", "w06", "w07")
        assertEquals(expected, SessionComposer.composeSession(state, t, Box.TZ).newCards)
        assertEquals(expected, SessionComposer.composeRound(state, t, Box.TZ).newCards)

        // Answering introduces them and dequeues.
        var after = Box.answered(state, "w03", Rating.Good, Box.plusSeconds(t, 100))
        after = Box.answered(after, "w04", Rating.Good, Box.plusSeconds(t, 200))
        assertEquals(listOf("w05"), after.enqueued)
        assertEquals("w05", SessionComposer.composeSession(after, t, Box.TZ).newCards.first())
    }

    @Test
    fun aCardOnItsLearningStepIsNotPulledBackBeforeItIsDue() {
        var state = Box.state((1..5).map { Box.word(it) })
        state = BoxEngine.enqueue(state, listOf("w01"))
        // w01 missed → one 2-minute step, then FSRS.
        state = Box.answered(state, "w01", Rating.Again, day0)

        // 1 min in, w01 is NOT due — it may be pulled forward like any other scheduled card,
        // but never counted as due work.
        val soon = SessionComposer.composeRound(state, Box.plusSeconds(day0, 60), Box.TZ)
        assertFalse(soon.reviews.contains("w01"))
        assertEquals(listOf("w02", "w03", "w04", "w05"), soon.newCards)

        // Once its step is genuinely due, it comes back as a review.
        val later = SessionComposer.composeRound(state, Box.plusSeconds(day0, 130), Box.TZ)
        assertEquals(listOf("w01"), later.reviews)
    }
}
