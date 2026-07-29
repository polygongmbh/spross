package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating

/** Growth: new-word budget, health gate, enqueue — everything denominated in cards. */
class BoxGrowthTests {
    private val now = Box.day1

    @Test
    fun dayOneBootstrapSpendsTheWholeBudget() {
        val state = Box.state((1..10).map { Box.word(it) }, Box.config(maxUnsettled = 5))
        val plan = Box.candidates(state)
        assertTrue(plan.unlockedPhrases.isEmpty())
        assertEquals((1..5).map { "w0$it" }, plan.newCards)
    }

    // The cap counts what has NOT settled, so a day of words you already knew
    // costs the box nothing and the way stays open — the point of measuring
    // load rather than headcount.
    @Test
    fun dayOneIntroducesUpToTheUnsettledCap() {
        var state = Box.state(
            (1..20).map { Box.word(it, synonyms = listOf("s$it")) },
            Box.config(maxUnsettled = 8),
        )
        val plan = Box.candidates(state)
        assertEquals((1..8).map { "w0$it" }, plan.newCards)

        for (id in plan.newCards) {
            state = Box.answered(state, id, Rating.Good, now)
        }
        assertEquals(8, state.newIntroduced["2026-07-01"])
        assertEquals(8, BoxEngine.statistics(state, now, Box.TZ).activeCount)
    }

    @Test
    fun settledWordsDoNotConsumeTheBudget() {
        var state = Box.state((1..12).map { Box.word(it) }, Box.config(maxUnsettled = 4))
        val past = Box.plusDays(now, -10.0)
        val future = Box.plusDays(now, 5.0)
        for (n in 1..6) {
            state = Box.inject(
                state,
                Box.sched("w0$n", stability = 9.0, dueMillis = future, lastReviewMillis = past),
            )
        }
        assertEquals(0, Growth.unsettledLoad(state))
        assertEquals(4, Growth.newBudget(state))
    }

    // Relearning cards are unsettled by definition, so a box full of them throttles
    // growth on its own — what the old relearning-share sub-gate approximated.
    @Test
    fun unsettledWordsCloseGrowthDownToTheTrickle() {
        var state = Box.state((1..12).map { Box.word(it) }, Box.config(maxUnsettled = 8))
        val past = Box.plusDays(now, -1.0)
        val future = Box.plusDays(now, 5.0)
        for (n in 1..3) {
            state = Box.inject(
                state,
                Box.sched("w0$n", phase = CardPhase.Relearning, dueMillis = future, lastReviewMillis = past),
            )
        }
        assertEquals(3, Growth.unsettledLoad(state))
        assertEquals(5, Growth.newBudget(state))

        for (n in 4..8) {
            state = Box.inject(
                state,
                Box.sched("w0$n", phase = CardPhase.Relearning, dueMillis = future, lastReviewMillis = past),
            )
        }
        // At the cap growth never stops dead: a trickle keeps every session varied.
        assertEquals(Growth.TRICKLE_CARDS, Growth.newBudget(state))
        assertEquals(Growth.TRICKLE_CARDS, Box.candidates(state).newCards.size)
    }

    @Test
    fun backlogGateBlocksOnProjectedPostSessionBacklog() {
        var state = Box.state((1..70).map { Box.word(it) })
        for (n in 1..61) {
            val id = "w" + n.toString().padStart(2, '0')
            state = Box.inject(
                state,
                Box.sched(id, dueMillis = now - n * 60_000L, lastReviewMillis = Box.plusDays(now, -1.0)),
            )
        }
        // 61 due cards − 30 sessionCap = 31 ≥ dueSoftCap 30 → closed.
        assertEquals(61, BoxEngine.dueNow(state, now).size)
        assertTrue(Box.candidates(state).newCards.isEmpty())
    }

    @Test
    fun budgetTracksLoadAcrossRecomposition() {
        var state = Box.state((1..10).map { Box.word(it) }, Box.config(maxUnsettled = 5))
        val plan = Box.candidates(state)
        assertEquals(5, plan.newCards.size)

        // Words answered on sight settle at once and cost the budget nothing.
        for (id in plan.newCards.take(3)) {
            state = Box.answered(state, id, Rating.Good, now)
        }
        assertEquals(0, Growth.unsettledLoad(state))
        assertEquals(5, Growth.newBudget(state))

        // The ones that miss are what loads the box.
        for (id in plan.newCards.drop(3)) {
            state = Box.answered(state, id, Rating.Again, now)
        }
        assertEquals(2, Growth.unsettledLoad(state))
        assertEquals(3, Growth.newBudget(state))
    }

    @Test
    fun budgetRecoversAsWordsSettle() {
        var state = Box.state((1..14).map { Box.word(it) }, Box.config(maxUnsettled = 10))
        for (n in 1..9) {
            state = Box.answered(state, "w0$n", Rating.Again, now)
        }
        // 9 words still on their way in → down to the trickle.
        assertEquals(9, Growth.unsettledLoad(state))
        assertEquals(Growth.TRICKLE_CARDS, Growth.newBudget(state))

        // Three of them stabilise over the following days; the budget opens back up.
        for (n in 1..3) {
            state = Box.inject(
                state,
                Box.sched("w0$n", stability = 9.0, dueMillis = Box.plusDays(now, 5.0), lastReviewMillis = now),
            )
        }
        assertEquals(6, Growth.unsettledLoad(state))
        assertEquals(4, Growth.newBudget(state))
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
    fun enqueuedLeadWithinBudgetAndPhrasePullsComponentsFirst() {
        var state = Box.state((1..10).map { Box.word(it) }, Box.config(maxUnsettled = 5))
        state = BoxEngine.enqueue(state, listOf("w07"))
        assertEquals(
            listOf("w07", "w01", "w02", "w03", "w04"),
            Box.candidates(state).newCards,
        )

        var withPhrase = Box.state(
            (1..6).map { Box.word(it) } + Box.phrase("p1", components = listOf("w05", "w06")),
            Box.config(maxUnsettled = 5),
        )
        withPhrase = BoxEngine.enqueue(withPhrase, listOf("p1"))
        assertEquals(listOf("w05", "w06", "p1"), withPhrase.enqueued)
        // Locked phrase never enters, even enqueued; components lead, then automatic
        // growth fills the rest of the budget.
        assertEquals(
            listOf("w05", "w06", "w01", "w02", "w03"),
            Box.candidates(withPhrase).newCards,
        )
    }

    @Test
    fun enqueuedRespectLoadThrottlePackDripsIn() {
        var state = Box.state((1..10).map { Box.word(it) }, Box.config(maxUnsettled = 2))
        state = BoxEngine.enqueue(state, listOf("w06", "w07", "w08"))
        assertEquals(listOf("w06", "w07"), Box.candidates(state).newCards)

        state = Box.answered(state, "w06", Rating.Good, now)
        state = Box.answered(state, "w07", Rating.Good, now)
        // At the cap the pack keeps dripping at the trickle rate, w08 first.
        assertEquals(listOf("w08"), state.enqueued)
        assertEquals("w08", Box.candidates(state).newCards.first())
    }

    @Test
    fun enqueueSkipsUnknownScheduledAndDuplicates() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.answered(state, "w01", Rating.Good, now)
        state = BoxEngine.enqueue(state, listOf("w01", "zzz", "w02", "w02"))
        assertEquals(listOf("w02"), state.enqueued)
    }
}
