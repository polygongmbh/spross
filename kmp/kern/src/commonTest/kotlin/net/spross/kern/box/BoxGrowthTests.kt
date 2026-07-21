package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating

/** Growth: concept budget, health gate (units), enqueue, eligibility lag. */
class BoxGrowthTests {
    private val now = Box.day1

    @Test
    fun dayOneBootstrapFillsThePool() {
        val state = Box.state((1..10).map { Box.word(it) }, Box.config(maxLearning = 5))
        val plan = Box.candidates(state)
        assertTrue(plan.unlockedPhrases.isEmpty())
        assertEquals((1..5).map { Box.produce("w0$it") }, plan.newUnits)
    }

    // Denomination: the pool is counted in CONCEPTS — day one introduces 8 concepts
    // under the default config even though every card carries extra recognize units.
    @Test
    fun dayOneIntroducesEightConcepts() {
        var state = Box.state((1..20).map { Box.word(it, synonyms = listOf("s$it")) })
        val plan = Box.candidates(state)
        assertEquals((1..8).map { Box.produce("w0$it") }, plan.newUnits)

        for (key in plan.newUnits) {
            state = Box.answered(state, key, Rating.Good, now)
        }
        assertEquals(8, state.newIntroduced["2026-07-01"])
        assertEquals(8, BoxEngine.statistics(state, now, Box.TZ).activeCount)
        val ninth = BoxEngine.answer(state, Box.produce("w09"), Rating.Good, now, Box.TZ)
        assertEquals(AnswerStatus.DroppedPoolFull, ninth.status)
    }

    @Test
    fun relearningShareGateBlocksAtTwentyPercentOfActiveUnits() {
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
        // 2 of 10 active units relearning = 20% → gate closed
        val blocked = Box.candidates(state)
        assertTrue(blocked.newUnits.isEmpty())
        assertTrue(blocked.unlockedPhrases.isEmpty())
        assertEquals(0, BoxEngine.statistics(state, now, Box.TZ).newSlotsAvailable)

        // Drop to 1 of 10 (10%) → gate open; recognize backfill of graduated
        // produce units leads new material (lower seed index).
        val healed = Box.inject(state, Box.sched("w10", dueMillis = future, lastReviewMillis = past))
        val plan = Box.candidates(healed)
        assertEquals((1..8).map { Box.recognize("w0$it", "t$it") }, plan.newUnits)
    }

    @Test
    fun relearningSubGatePassesBelowTenActiveUnits() {
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
        // 2 of 5 relearning = 40%, but < 10 active units → still introduces.
        val plan = Box.candidates(state)
        assertEquals(
            (3..5).map { Box.recognize("w0$it", "t$it") } + (6..8).map { Box.produce("w0$it") },
            plan.newUnits,
        )
    }

    @Test
    fun backlogGateBlocksOnProjectedPostSessionBacklog() {
        var state = Box.state((1..70).map { Box.word(it) }, Box.config(dueSoftCap = 30))
        for (n in 1..61) {
            val id = "w" + n.toString().padStart(2, '0')
            state = Box.inject(
                state,
                Box.sched(id, dueMillis = now - n * 60_000L, lastReviewMillis = Box.plusDays(now, -1.0)),
            )
        }
        // 61 due units − 30 sessionCap = 31 ≥ dueSoftCap 30 → closed.
        assertEquals(61, BoxEngine.dueNow(state, now).size)
        assertTrue(Box.candidates(state).newUnits.isEmpty())
    }

    @Test
    fun budgetTracksLearningLoadAcrossRecomposition() {
        var state = Box.state((1..10).map { Box.word(it) }, Box.config(maxLearning = 5))
        val plan = Box.candidates(state)
        assertEquals(5, plan.newUnits.size)

        for (key in plan.newUnits.take(3)) {
            state = Box.answered(state, key, Rating.Good, now)
        }
        assertEquals(listOf(Box.produce("w04"), Box.produce("w05")), Box.candidates(state).newUnits)

        for (key in Box.candidates(state).newUnits) {
            state = Box.answered(state, key, Rating.Good, now)
        }
        assertTrue(Box.candidates(state).newUnits.isEmpty()) // pool full: 5 concepts in flight
    }

    @Test
    fun poolRefillsOnGraduationAndBackfillLeads() {
        var state = Box.state((1..10).map { Box.word(it) }, Box.config(maxLearning = 3))
        for (n in 1..3) {
            state = Box.answered(state, Box.produce("w0$n"), Rating.Good, now)
        }
        assertTrue(Box.candidates(state).newUnits.isEmpty())

        // Graduating w01 frees a slot; its recognize unit backfills ahead of w04.
        val step = Box.plusSeconds(now, 700)
        state = Box.answered(state, Box.produce("w01"), Rating.Good, step)
        assertEquals(listOf(Box.recognize("w01", "t1")), Box.candidates(state, nowMillis = step).newUnits)
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
            listOf("w07", "w01", "w02", "w03", "w04").map(Box::produce),
            Box.candidates(state).newUnits,
        )

        var withPhrase = Box.state(
            (1..6).map { Box.word(it) } + Box.phrase("p1", components = listOf("w05", "w06")),
            Box.config(maxLearning = 5),
        )
        withPhrase = BoxEngine.enqueue(withPhrase, listOf("p1"))
        assertEquals(listOf("w05", "w06", "p1"), withPhrase.enqueued)
        // Locked phrase never enters, even enqueued; components lead, then automatic
        // growth fills the rest of the concept budget.
        assertEquals(
            listOf("w05", "w06", "w01", "w02", "w03").map(Box::produce),
            Box.candidates(withPhrase).newUnits,
        )
    }

    @Test
    fun enqueuedRespectLoadThrottlePackDripsIn() {
        var state = Box.state((1..10).map { Box.word(it) }, Box.config(maxLearning = 2))
        state = BoxEngine.enqueue(state, listOf("w06", "w07", "w08"))
        val plan = Box.candidates(state)
        assertEquals(listOf(Box.produce("w06"), Box.produce("w07")), plan.newUnits)

        state = Box.answered(state, Box.produce("w06"), Rating.Good, now)
        state = Box.answered(state, Box.produce("w07"), Rating.Good, now)
        assertTrue(Box.candidates(state).newUnits.isEmpty())
        assertEquals(listOf("w08"), state.enqueued) // still waiting for a slot
    }

    @Test
    fun enqueueSkipsUnknownScheduledAndDuplicates() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.answered(state, Box.produce("w01"), Rating.Good, now)
        state = BoxEngine.enqueue(state, listOf("w01", "zzz", "w02", "w02"))
        assertEquals(listOf("w02"), state.enqueued)
    }

    // Eligibility lag: recognize units become introducible only once their card's
    // produce unit is in Review — then units of the same concept share one pool slot.
    @Test
    fun recognizeEligibilityLag() {
        var state = Box.state(listOf(Box.word(1, synonyms = listOf("s1")), Box.word(2)))
        assertEquals(listOf(Box.produce("w01"), Box.produce("w02")), Box.candidates(state).newUnits)

        state = Box.answered(state, Box.produce("w01"), Rating.Good, now)
        assertEquals(listOf(Box.produce("w02")), Box.candidates(state).newUnits)

        val step = Box.plusSeconds(now, 700)
        state = Box.answered(state, Box.produce("w01"), Rating.Good, step) // graduates
        assertEquals(
            listOf(Box.recognize("w01", "t1"), Box.recognize("w01", "s1"), Box.produce("w02")),
            Box.candidates(state, nowMillis = step).newUnits,
        )
    }

    @Test
    fun suspendedProduceBlocksRecognizeBackfill() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = Box.plusDays(now, 5.0), lastReviewMillis = now, suspended = true),
        )
        assertEquals(listOf(Box.produce("w02")), Box.candidates(state).newUnits)
    }
}
