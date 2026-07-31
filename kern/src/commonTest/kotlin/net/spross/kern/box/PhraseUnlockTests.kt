package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating

/** Phrase unlock fast path — gate on component schedules, raw by card id. */
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
        assertEquals(listOf("w01", "w02", "w03"), plan1.newCards)

        // Easy graduates straight to Review with stability 8.2956 ≥ 6.0 (consolidated bar).
        state = Box.answered(state, "w01", Rating.Easy, now)

        // One stable component is not enough — ALL must be stable.
        assertTrue(Box.candidates(state).unlockedPhrases.isEmpty())

        state = Box.answered(state, "w02", Rating.Easy, now)
        val plan3 = Box.candidates(state)
        assertEquals(listOf("p1"), plan3.unlockedPhrases)
        // The unlocked phrase consumes the new-word budget ahead of seed-order growth.
        assertEquals(listOf("w03"), plan3.newCards)
    }

    @Test
    fun suspendedComponentBlocksUnlock() {
        var state = seeded()
        state = Box.answered(state, "w01", Rating.Easy, now)
        state = Box.answered(state, "w02", Rating.Easy, now)
        state = BoxEngine.setSuspended(state, "w01", true)
        assertTrue(Box.candidates(state).unlockedPhrases.isEmpty())

        // Reviving the component restores eligibility.
        state = BoxEngine.setSuspended(state, "w01", false)
        assertEquals(listOf("p1"), Box.candidates(state).unlockedPhrases)
    }

    @Test
    fun componentBelowUnlockStabilityKeepsPhraseLocked() {
        var state = seeded()
        val future = Box.plusDays(now, 5.0)
        state = Box.inject(state, Box.sched("w01", stability = 10.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w02", stability = 5.9, dueMillis = future, lastReviewMillis = now))
        assertTrue(Box.candidates(state).unlockedPhrases.isEmpty())

        // Phrase unlock uses the stricter consolidated bar: 6.0 unlocks.
        state = Box.inject(state, Box.sched("w02", stability = 6.0, dueMillis = future, lastReviewMillis = now))
        assertEquals(listOf("p1"), Box.candidates(state).unlockedPhrases)
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
        assertEquals(listOf("w01", "w02", "p-empty"), plan.newCards)
    }
}
