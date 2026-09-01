package net.spross.kern.fsrs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating

/**
 * (Re)learning-step ladder vectors — one machine shared by Learning and Relearning
 * (product ruling 2026-09-01; see [FsrsScheduler]'s KDoc for why this diverges from
 * the py-fsrs/ts-fsrs reference step machine). The Hard blend is the one piece still
 * borrowed from the reference: ts-fsrs v5.4.1's whole-minute rounding
 * (commit bfc0a1960dfde4b4627ae4f4c8757b9211314963) — 6m for [1m,10m], ×1.5 for a
 * single step.
 */
class FsrsStepLadderTest {

    private fun scheduler(steps: List<Long> = listOf(60L, 600L)) =
        FsrsScheduler(FsrsParameters(stepsSeconds = steps))

    private fun outcome(
        scheduler: FsrsScheduler,
        phase: CardPhase,
        step: Int?,
        rating: Rating,
        elapsedDays: Double = 0.0,
    ): SchedulerOutcome {
        val memory =
            if (phase == CardPhase.New) null
            else scheduler.algorithm.nextMemory(null, 0.0, Rating.Good)
        return scheduler.review(SchedulerState(phase, step, memory), elapsedDays, rating)
    }

    // Again climbs the ladder instead of resetting, capped at the last entry; a single
    // Good/Easy graduates immediately from wherever it sits. Learning and Relearning
    // share this machine — only how each phase is ENTERED differs (below).
    @Test
    fun ladderGrowsOnAgainGraduatesOnGoodInEitherPhase() {
        for (phase in listOf(CardPhase.Learning, CardPhase.Relearning)) {
            val s = scheduler(listOf(600L, 1200L))

            val again0 = outcome(s, phase, 0, Rating.Again)
            assertEquals(1200L, again0.intervalSeconds)
            assertEquals(1, again0.stepIndex)
            assertEquals(phase, again0.phase)

            val again1 = outcome(s, phase, 1, Rating.Again)
            assertEquals(1200L, again1.intervalSeconds) // already at the last step, holds
            assertEquals(1, again1.stepIndex)

            val good0 = outcome(s, phase, 0, Rating.Good)
            assertEquals(CardPhase.Review, good0.phase)
            assertNull(good0.stepIndex)
            assertTrue(good0.intervalDays >= 1)

            val easy1 = outcome(s, phase, 1, Rating.Easy)
            assertEquals(CardPhase.Review, easy1.phase)
            assertNull(easy1.stepIndex)
        }
    }

    // ts learning-steps.test.ts minute table (['1m','10m']): Hard blends the first two
    // entries regardless of position, rounded to whole minutes.
    @Test
    fun hardHoldsAtTheFirstStepsBlend() {
        val s = scheduler(listOf(60L, 600L))
        for (phase in listOf(CardPhase.Learning, CardPhase.Relearning)) {
            val hard0 = outcome(s, phase, 0, Rating.Hard)
            assertEquals(360L, hard0.intervalSeconds) // (1+10)/2 = 5.5m -> rounds to 6m
            assertEquals(0, hard0.stepIndex)

            val hard1 = outcome(s, phase, 1, Rating.Hard)
            assertEquals(360L, hard1.intervalSeconds) // position-independent blend
            assertEquals(1, hard1.stepIndex)
        }
    }

    // ts learning-steps.test.ts `['1m']`: Hard = round(1m x 1.5) = 2m for a single step.
    @Test
    fun hardTimesOnePointFiveForASingleStep() {
        val s = scheduler(listOf(60L))
        val hard = outcome(s, CardPhase.Learning, 0, Rating.Hard)
        assertEquals(120L, hard.intervalSeconds)
    }

    // A single-entry ladder cannot grow: Again always lands back at its one step.
    @Test
    fun singleStepLadderHoldsOnRepeatedAgain() {
        val s = scheduler(listOf(600L))
        for (phase in listOf(CardPhase.Learning, CardPhase.Relearning)) {
            val again = outcome(s, phase, 0, Rating.Again)
            assertEquals(600L, again.intervalSeconds)
            assertEquals(0, again.stepIndex)
        }
    }

    // Past-the-end step index (config shrank while a card sat mid-ladder): Again is
    // coerced back onto the last valid entry rather than indexing out of bounds.
    @Test
    fun pastEndStepCoercesToTheLastEntryOnAgain() {
        val s = scheduler(listOf(60L))
        val again = outcome(s, CardPhase.Learning, 5, Rating.Again)
        assertEquals(0, again.stepIndex)
        assertEquals(60L, again.intervalSeconds)
    }

    // No steps configured: even Again skips straight to Review.
    @Test
    fun emptyLadderGraduatesEvenOnAgain() {
        val s = scheduler(emptyList())
        val o = s.review(SchedulerState(), 0.0, Rating.Again)
        assertEquals(CardPhase.Review, o.phase)
        assertTrue(o.intervalDays >= 1)
    }

    // Entering Learning: a brand-new card's first Again opens the ladder at step 0.
    @Test
    fun newCardEntersLearningAtStepZero() {
        val s = scheduler()
        val o = s.review(SchedulerState(), 0.0, Rating.Again)
        assertEquals(CardPhase.Learning, o.phase)
        assertEquals(0, o.stepIndex)
        assertEquals(60L, o.intervalSeconds)
    }

    // Entering Relearning: a lapse from Review opens the ladder at step 0, regardless
    // of how far the card's stability had come.
    @Test
    fun lapseFromReviewEntersRelearningAtStepZero() {
        val s = scheduler()
        val memory = s.algorithm.nextMemory(null, 0.0, Rating.Good)
        val o = s.review(SchedulerState(CardPhase.Review, null, memory), 17.0, Rating.Again)
        assertEquals(CardPhase.Relearning, o.phase)
        assertEquals(0, o.stepIndex)
        assertEquals(60L, o.intervalSeconds)
    }

    // A lapse with no ladder configured stays in Review, rescheduling from its
    // post-lapse stability instead (reference behavior).
    @Test
    fun lapseWithEmptyLadderStaysInReview() {
        val s = scheduler(emptyList())
        val memory = s.algorithm.nextMemory(null, 0.0, Rating.Good)
        val o = s.review(SchedulerState(CardPhase.Review, null, memory), 2.0, Rating.Again)
        assertEquals(CardPhase.Review, o.phase)
        assertNull(o.stepIndex)
        assertTrue(o.intervalDays >= 1)
    }
}
