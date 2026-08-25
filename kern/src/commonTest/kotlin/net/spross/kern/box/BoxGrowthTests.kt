package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating
import net.spross.kern.session.SessionComposer.NEW_CARDS_PER_ROUND

/** Growth: what a round may introduce, and enqueue — everything in cards. */
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
        var state = Box.state((1..20).map { Box.word(it, synonyms = listOf("s$it")) })
        val plan = Box.candidates(state)
        assertEquals(NEW_CARDS_PER_ROUND, plan.newCards.size)

        for (id in plan.newCards) {
            state = Box.answered(state, id, Rating.Good, now)
        }
        assertEquals(NEW_CARDS_PER_ROUND, state.newIntroduced["2026-07-01"])
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
        assertEquals(60, BoxEngine.dueNow(state, now).size)
        assertTrue(Box.candidates(state).newCards.isNotEmpty())
    }

    @Test
    fun candidateSelectionIsPureAndDeterministic() {
        val state = Box.state((1..10).map { Box.word(it) })
        val first = Box.candidates(state)
        repeat(5) { assertEquals(first, Box.candidates(state)) }
        assertTrue(state.scheduling.isEmpty())
        assertTrue(state.newIntroduced.isEmpty())
    }

    @Test
    fun enqueuedLeadWithinTheRoundAndPhrasePullsComponentsFirst() {
        var state = Box.state((1..10).map { Box.word(it) })
        state = BoxEngine.enqueue(state, listOf("w07"))
        assertEquals(
            listOf("w07", "w01", "w02", "w03", "w04", "w05", "w06"),
            Box.candidates(state).newCards,
        )

        var withPhrase = Box.state(
            (1..6).map { Box.word(it) } + Box.phrase("p1", components = listOf("w05", "w06")),
        )
        withPhrase = BoxEngine.enqueue(withPhrase, listOf("p1"))
        assertEquals(listOf("w05", "w06", "p1"), withPhrase.enqueued)
        // Locked phrase never enters, even enqueued; components lead, then automatic
        // growth fills the rest of the round.
        assertEquals(
            listOf("w05", "w06", "w01", "w02", "w03", "w04"),
            Box.candidates(withPhrase).newCards,
        )
    }

    @Test
    fun enqueuedPackDripsInARoundAtATime() {
        var state = Box.state((1..12).map { Box.word(it) })
        state = BoxEngine.enqueue(state, (1..10).map { "w" + it.toString().padStart(2, '0') })
        assertEquals(
            (1..NEW_CARDS_PER_ROUND).map { "w0$it" },
            Box.candidates(state).newCards,
        )

        for (id in Box.candidates(state).newCards) {
            state = Box.answered(state, id, Rating.Good, now)
        }
        // What the round could not take is still packed, front first.
        assertEquals(listOf("w08", "w09", "w10"), state.enqueued)
        assertEquals("w08", Box.candidates(state).newCards.first())
    }

    @Test
    fun enqueueSkipsUnknownScheduledAndDuplicates() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.answered(state, "w01", Rating.Good, now)
        state = BoxEngine.enqueue(state, listOf("w01", "zzz", "w02", "w02"))
        assertEquals(listOf("w02"), state.enqueued)
    }

    @Test
    fun dequeueTakesAPackedWordBackOut() {
        var state = Box.state((1..3).map { Box.word(it) })
        state = BoxEngine.enqueue(state, listOf("w01", "w02"))

        state = BoxEngine.dequeue(state, "w01")
        assertEquals(listOf("w02"), state.enqueued)

        // Unknown to the queue, or already scheduled: both a no-op.
        assertEquals(state, BoxEngine.dequeue(state, "w03"))
        state = Box.answered(state, "w02", Rating.Good, now)
        assertEquals(state, BoxEngine.dequeue(state, "w02"))
    }

    @Test
    fun dequeueingAPhraseLeavesItsPulledInComponentsQueued() {
        var state = Box.state(
            (1..2).map { Box.word(it) } + Box.phrase("p1", components = listOf("w01", "w02")),
        )
        state = BoxEngine.enqueue(state, listOf("p1"))
        assertEquals(listOf("w01", "w02", "p1"), state.enqueued)

        state = BoxEngine.dequeue(state, "p1")
        assertEquals(listOf("w01", "w02"), state.enqueued)
    }
}
