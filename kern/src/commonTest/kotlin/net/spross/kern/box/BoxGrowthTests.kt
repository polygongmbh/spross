package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating
import net.spross.kern.session.SessionComposer.NEW_CARDS_PER_ROUND

/** Growth: what a round may introduce, and queue — everything in cards. */
class BoxGrowthTests {
    private val now = Box.day1

    @Test
    fun dayOneOffersARoundsWorthOfNewWords() {
        val state = Box.state((1..10).map { Box.word(it) })
        val plan = Box.candidates(state)
        assertTrue(plan.unlockedPhrases.isEmpty())
        assertEquals((1..NEW_CARDS_PER_ROUND).map { "w0$it" }, plan.newCards)
    }

    @Test
    fun theRestIsDeferredNotWithdrawn() {
        var state = Box.state((1..20).map { Box.word(it, teaches = listOf("s$it")) })
        val plan = Box.candidates(state)
        assertEquals(NEW_CARDS_PER_ROUND, plan.newCards.size)

        for (id in plan.newCards) {
            state = Box.answered(state, id, Rating.Good, now)
        }
        assertEquals(NEW_CARDS_PER_ROUND, state.scheduling.size)
        assertEquals(NEW_CARDS_PER_ROUND, BoxEngine.statistics(state, now, Box.TZ).activeCount)
        // The next round picks up where this one stopped.
        assertEquals("w08", Box.candidates(state).newCards.first())
    }

    /**
     * The heart of the intake change: a box full of words that keep going wrong is still
     * offered a full round of new material. Shakiness is a difficulty signal, and it does
     * not predict retention (`docs/growth-evidence.md`).
     */
    @Test
    fun shakyWordsNoLongerNarrowTheOffer() {
        var state = Box.state((1..30).map { Box.word(it) })
        val past = Box.plusDays(now, -1.0)
        val future = Box.plusDays(now, 5.0)
        for (n in 1..18) {
            val id = "w" + n.toString().padStart(2, '0')
            state = Box.inject(
                state,
                Box.sched(id, phase = CardPhase.Relearning, dueMillis = future, lastReviewMillis = past),
            )
        }
        assertEquals(NEW_CARDS_PER_ROUND, Box.candidates(state).newCards.size)
    }

    /**
     * A box far behind still grows. At `desiredRetention` 0.8 a sitting pushes far more cards
     * out on longer intervals than the round's few new ones bring in, so the backlog is not a
     * hole growth digs deeper — the reserve is what keeps a busy box from stalling entirely
     * (`docs/growth-evidence.md`).
     */
    @Test
    fun aDeepBacklogStillOffersNewWords() {
        var state = Box.state((1..70).map { Box.word(it) })
        for (n in 1..60) {
            val id = "w" + n.toString().padStart(2, '0')
            state = Box.inject(
                state,
                Box.sched(id, dueMillis = now - n * 60_000L, lastReviewMillis = Box.plusDays(now, -1.0)),
            )
        }
        assertEquals(60, Box.dueIds(state, now).size)
        assertTrue(Box.candidates(state).newCards.isNotEmpty())
    }

    @Test
    fun candidateSelectionIsPureAndDeterministic() {
        val state = Box.state((1..10).map { Box.word(it) })
        val first = Box.candidates(state)
        repeat(5) { assertEquals(first, Box.candidates(state)) }
        assertTrue(state.scheduling.isEmpty())
    }

    @Test
    fun queuedLeadWithinTheRoundAndPhrasePullsComponentsFirst() {
        var state = Box.state((1..10).map { Box.word(it) })
        state = BoxEngine.queue(state, listOf("w07"))
        assertEquals(
            listOf("w07", "w01", "w02", "w03", "w04", "w05", "w06"),
            Box.candidates(state).newCards,
        )

        var withPhrase = Box.state(
            (1..6).map { Box.word(it) } + Box.phrase("p1", components = listOf("w05", "w06")),
        )
        withPhrase = BoxEngine.queue(withPhrase, listOf("p1"))
        assertEquals(listOf("w05", "w06", "p1"), withPhrase.queued)
        // Locked phrase never enters, even queued; its components lead — most recently
        // pulled in first, since a single queue call queues them in one breath — then
        // automatic growth fills the rest of the round.
        assertEquals(
            listOf("w06", "w05", "w01", "w02", "w03", "w04"),
            Box.candidates(withPhrase).newCards,
        )
    }

    @Test
    fun aQueuedBatchDripsInARoundAtATime() {
        var state = Box.state((1..12).map { Box.word(it) })
        // One queue call, one batch: it still introduces in the order it was queued in.
        state = BoxEngine.queue(state, (1..10).map { "w" + it.toString().padStart(2, '0') })
        assertEquals(
            (1..NEW_CARDS_PER_ROUND).map { "w0$it" },
            Box.candidates(state).newCards,
        )

        for (id in Box.candidates(state).newCards) {
            state = Box.answered(state, id, Rating.Good, now)
        }
        // What the round could not take is still queued — the raw queue is stored back to
        // front (`BoxEngine.queue`), so it still reads out front-first, w08 next.
        assertEquals(listOf("w10", "w09", "w08"), state.queued)
        assertEquals("w08", Box.candidates(state).newCards.first())
    }

    /**
     * RULE: a batch queued later leads a batch queued earlier, but each batch's own words
     * still come out in the order they were given — a category queued whole still teaches
     * front to back.
     * WHY: the point of most-recently-queued-first is "what I just asked for," not "the last
     * word of what I just asked for." `BoxEngine.queue` stores a batch back to front so
     * that reading it back to front (`Growth.queuedEligible`) restores its own order.
     */
    @Test
    fun aLaterBatchLeadsButEachBatchsOwnOrderSurvives() {
        var state = Box.state((1..20).map { Box.word(it) })
        state = BoxEngine.queue(state, listOf("w01", "w02", "w03"))
        state = BoxEngine.queue(state, listOf("w10", "w11", "w12"))

        assertEquals(
            listOf("w10", "w11", "w12", "w01", "w02", "w03"),
            Growth.queuedEligible(state),
        )
    }

    @Test
    fun queueSkipsUnknownScheduledAndDuplicates() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.answered(state, "w01", Rating.Good, now)
        state = BoxEngine.queue(state, listOf("w01", "zzz", "w02", "w02"))
        assertEquals(listOf("w02"), state.queued)
    }

    @Test
    fun unqueueTakesAQueuedWordBackOut() {
        var state = Box.state((1..3).map { Box.word(it) })
        state = BoxEngine.queue(state, listOf("w01", "w02"))

        state = BoxEngine.unqueue(state, "w01")
        assertEquals(listOf("w02"), state.queued)

        // Unknown to the queue, or already scheduled: both a no-op.
        assertEquals(state, BoxEngine.unqueue(state, "w03"))
        state = Box.answered(state, "w02", Rating.Good, now)
        assertEquals(state, BoxEngine.unqueue(state, "w02"))
    }

    @Test
    fun unqueuingAPhraseLeavesItsPulledInComponentsQueued() {
        var state = Box.state(
            (1..2).map { Box.word(it) } + Box.phrase("p1", components = listOf("w01", "w02")),
        )
        state = BoxEngine.queue(state, listOf("p1"))
        assertEquals(listOf("w01", "w02", "p1"), state.queued)

        state = BoxEngine.unqueue(state, "p1")
        assertEquals(listOf("w01", "w02"), state.queued)
    }

    @Test
    fun unqueueAreaTakesOutOnlyThatAreasQueuedCards() {
        var state = Box.state(
            (1..3).map { Box.word(it, area = "kitchen") } + Box.word(4, area = "office"),
        )
        state = BoxEngine.queue(state, listOf("w01", "w03", "w04"))

        state = BoxEngine.unqueueArea(state, "kitchen")
        assertEquals(listOf("w04"), state.queued)

        // Nothing left there, or an area that was never queued: both a no-op.
        assertEquals(state, BoxEngine.unqueueArea(state, "kitchen"))
        assertEquals(state, BoxEngine.unqueueArea(state, "bath"))
    }
}
