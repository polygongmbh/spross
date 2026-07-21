package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating

/** Phrase unlock fast path — gate on component `id|produce` schedules, by key. */
class PhraseUnlockTests {
    private val now = Box.day1

    private fun seeded(): BoxState = Box.state(
        listOf(
            Box.word(1), Box.word(2), Box.word(3),
            Box.phrase("p1", components = listOf("w01", "w02")),
        ),
    )

    @Test
    fun endToEndUnlockAtStabilityThresholdWithinBudget() {
        var state = seeded()

        // Locked while components are unscheduled: the phrase is never proposed.
        val plan1 = Box.candidates(state)
        assertTrue(plan1.unlockedPhrases.isEmpty())
        assertEquals(listOf("w01", "w02", "w03").map(Box::produce), plan1.newUnits)

        // Easy graduates straight to Review with stability 8.2956 ≥ 2.0.
        state = Box.answered(state, Box.produce("w01"), Rating.Easy, now)

        // One stable component is not enough — ALL must be stable.
        assertTrue(Box.candidates(state).unlockedPhrases.isEmpty())

        state = Box.answered(state, Box.produce("w02"), Rating.Easy, now)
        val plan3 = Box.candidates(state)
        assertEquals(listOf(Box.produce("p1")), plan3.unlockedPhrases)
        // The unlocked phrase consumes the concept budget ahead of seed-order growth
        // (recognize backfill of the graduated components follows).
        assertEquals(
            listOf(Box.recognize("w01", "t1"), Box.recognize("w02", "t2"), Box.produce("w03")),
            plan3.newUnits,
        )
    }

    @Test
    fun suspendedComponentBlocksUnlock() {
        var state = seeded()
        state = Box.answered(state, Box.produce("w01"), Rating.Easy, now)
        state = Box.answered(state, Box.produce("w02"), Rating.Easy, now)
        state = BoxEngine.setSuspended(state, Box.produce("w01"), true)
        assertTrue(Box.candidates(state).unlockedPhrases.isEmpty())

        // Reviving the component restores eligibility.
        state = BoxEngine.setSuspended(state, Box.produce("w01"), false)
        assertEquals(listOf(Box.produce("p1")), Box.candidates(state).unlockedPhrases)
    }

    @Test
    fun componentBelowUnlockStabilityKeepsPhraseLocked() {
        var state = seeded()
        val future = Box.plusDays(now, 5.0)
        state = Box.inject(state, Box.sched("w01", stability = 10.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w02", stability = 1.9, dueMillis = future, lastReviewMillis = now))
        assertTrue(Box.candidates(state).unlockedPhrases.isEmpty())

        // FSRS-6 recalibrated threshold: 2.0 unlocks.
        state = Box.inject(state, Box.sched("w02", stability = 2.0, dueMillis = future, lastReviewMillis = now))
        assertEquals(listOf(Box.produce("p1")), Box.candidates(state).unlockedPhrases)
    }

    @Test
    fun componentInLearningPhaseKeepsPhraseLocked() {
        var state = seeded()
        val future = Box.plusDays(now, 5.0)
        state = Box.inject(state, Box.sched("w01", stability = 10.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(
            state,
            Box.sched("w02", phase = CardPhase.Learning, stability = 10.0, dueMillis = future, lastReviewMillis = now),
        )
        assertTrue(Box.candidates(state).unlockedPhrases.isEmpty())
    }

    @Test
    fun zeroComponentPhrasesFollowSeedOrderNeverFastPath() {
        val state = Box.state(
            listOf(
                Box.word(1), Box.word(2),
                Box.phrase("p-empty", components = emptyList(), seedIndex = 3),
            ),
        )
        val plan = Box.candidates(state)
        assertTrue(plan.unlockedPhrases.isEmpty())
        assertEquals(
            listOf(Box.produce("w01"), Box.produce("w02"), Box.produce("p-empty")),
            plan.newUnits,
        )
    }
}
