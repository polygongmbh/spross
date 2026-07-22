package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating

/** Growth: pool budget, health gate, enqueue — everything denominated in cards. */
class BoxGrowthTests {
    private val now = Box.day1

    @Test
    fun dayOneBootstrapFillsThePool() {
        val state = Box.state((1..10).map { Box.word(it) }, Box.config(maxLearning = 5))
        val plan = Box.candidates(state)
        assertTrue(plan.unlockedPhrases.isEmpty())
        assertEquals((1..5).map { "w0$it" }, plan.newCards)
    }

    // v1 calibration restored: day one introduces 8 cards under the default config
    // (synonyms rotate presentations, they never multiply schedules).
    @Test
    fun dayOneIntroducesEightCards() {
        var state = Box.state((1..20).map { Box.word(it, synonyms = listOf("s$it")) })
        val plan = Box.candidates(state)
        assertEquals((1..8).map { "w0$it" }, plan.newCards)

        for (id in plan.newCards) {
            state = Box.answered(state, id, Rating.Good, now)
        }
        assertEquals(8, state.newIntroduced["2026-07-01"])
        assertEquals(8, BoxEngine.statistics(state, now, Box.TZ).activeCount)
        val ninth = BoxEngine.answer(state, "w09", Rating.Good, now, Box.TZ)
        assertEquals(AnswerStatus.DroppedPoolFull, ninth.status)
    }

    @Test
    fun relearningShareGateBlocksAtTwentyPercentOfActiveCards() {
        var state = Box.state((1..12).map { Box.word(it) })
        val future = Box.plusDays(now, 5.0)
        val past = Box.plusDays(now, -1.0)
        for (n in 1..8) {
            state = Box.inject(state, Box.sched("w0$n", dueMillis = future, lastReviewMillis = past))
        }
        for (n in 9..10) {
            state = Box.inject(
                state,
                Box.sched("w" + n.toString().padStart(2, '0'), phase = CardPhase.Relearning, dueMillis = future, lastReviewMillis = past),
            )
        }
        // 2 of 10 active cards relearning = 20% → gate closed
        val blocked = Box.candidates(state)
        assertTrue(blocked.newCards.isEmpty())
        assertTrue(blocked.unlockedPhrases.isEmpty())
        assertEquals(0, BoxEngine.statistics(state, now, Box.TZ).newSlotsAvailable)

        // Drop to 2 of 11 (18%) → gate open; seed-order growth resumes.
        val healed = Box.inject(state, Box.sched("w11", dueMillis = future, lastReviewMillis = past))
        assertEquals(listOf("w12"), Box.candidates(healed).newCards)
    }

    @Test
    fun relearningSubGatePassesBelowTenActiveCards() {
        var state = Box.state((1..8).map { Box.word(it) })
        val future = Box.plusDays(now, 5.0)
        val past = Box.plusDays(now, -1.0)
        for (n in 1..2) {
            state = Box.inject(
                state,
                Box.sched("w0$n", phase = CardPhase.Relearning, dueMillis = future, lastReviewMillis = past),
            )
        }
        for (n in 3..5) {
            state = Box.inject(state, Box.sched("w0$n", dueMillis = future, lastReviewMillis = past))
        }
        // 2 of 5 relearning = 40%, but < 10 active cards → still introduces.
        assertEquals((6..8).map { "w0$it" }, Box.candidates(state).newCards)
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
    fun budgetTracksLearningLoadAcrossRecomposition() {
        var state = Box.state((1..10).map { Box.word(it) }, Box.config(maxLearning = 5))
        val plan = Box.candidates(state)
        assertEquals(5, plan.newCards.size)

        for (id in plan.newCards.take(3)) {
            state = Box.answered(state, id, Rating.Good, now)
        }
        assertEquals(listOf("w04", "w05"), Box.candidates(state).newCards)

        for (id in Box.candidates(state).newCards) {
            state = Box.answered(state, id, Rating.Good, now)
        }
        assertTrue(Box.candidates(state).newCards.isEmpty()) // pool full: 5 cards in learning
    }

    @Test
    fun poolRefillsOnGraduation() {
        var state = Box.state((1..10).map { Box.word(it) }, Box.config(maxLearning = 3))
        for (n in 1..3) {
            state = Box.answered(state, "w0$n", Rating.Good, now)
        }
        assertTrue(Box.candidates(state).newCards.isEmpty())

        // Graduating w01 frees a slot; the next seed-order card fills it.
        val step = Box.plusSeconds(now, 700)
        state = Box.answered(state, "w01", Rating.Good, step)
        assertEquals(listOf("w04"), Box.candidates(state, nowMillis = step).newCards)
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
        var state = Box.state((1..10).map { Box.word(it) }, Box.config(maxLearning = 5))
        state = BoxEngine.enqueue(state, listOf("w07"))
        assertEquals(
            listOf("w07", "w01", "w02", "w03", "w04"),
            Box.candidates(state).newCards,
        )

        var withPhrase = Box.state(
            (1..6).map { Box.word(it) } + Box.phrase("p1", components = listOf("w05", "w06")),
            Box.config(maxLearning = 5),
        )
        withPhrase = BoxEngine.enqueue(withPhrase, listOf("p1"))
        assertEquals(listOf("w05", "w06", "p1"), withPhrase.enqueued)
        // Locked phrase never enters, even enqueued; components lead, then automatic
        // growth fills the rest of the pool budget.
        assertEquals(
            listOf("w05", "w06", "w01", "w02", "w03"),
            Box.candidates(withPhrase).newCards,
        )
    }

    @Test
    fun enqueuedRespectLoadThrottlePackDripsIn() {
        var state = Box.state((1..10).map { Box.word(it) }, Box.config(maxLearning = 2))
        state = BoxEngine.enqueue(state, listOf("w06", "w07", "w08"))
        assertEquals(listOf("w06", "w07"), Box.candidates(state).newCards)

        state = Box.answered(state, "w06", Rating.Good, now)
        state = Box.answered(state, "w07", Rating.Good, now)
        assertTrue(Box.candidates(state).newCards.isEmpty())
        assertEquals(listOf("w08"), state.enqueued) // still waiting for a slot
    }

    @Test
    fun enqueueSkipsUnknownScheduledAndDuplicates() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.answered(state, "w01", Rating.Good, now)
        state = BoxEngine.enqueue(state, listOf("w01", "zzz", "w02", "w02"))
        assertEquals(listOf("w02"), state.enqueued)
    }
}
