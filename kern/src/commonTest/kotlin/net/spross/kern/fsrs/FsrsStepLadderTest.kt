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
 * the py-fsrs/ts-fsrs reference step machine). Again is the only rating that stays
 * on the ladder; Hard, Good and Easy graduate from wherever it sits.
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

    // Again climbs the ladder instead of resetting, capped at the last entry; every
    // rating that is not Again graduates immediately from wherever it sits. Learning
    // and Relearning share this machine — only how each phase is ENTERED differs (below).
    @Test
    fun ladderGrowsOnAgainGraduatesOnEveryOtherRatingInEitherPhase() {
        for (phase in listOf(CardPhase.Learning, CardPhase.Relearning)) {
            val s = scheduler(listOf(600L, 1200L))

            val again0 = outcome(s, phase, 0, Rating.Again)
            assertEquals(1200L, again0.intervalSeconds)
            assertEquals(1, again0.stepIndex)
            assertEquals(phase, again0.phase)

            val again1 = outcome(s, phase, 1, Rating.Again)
            assertEquals(1200L, again1.intervalSeconds) // already at the last step, holds
            assertEquals(1, again1.stepIndex)

            // Hard graduates like Good and Easy: the ladder spaces out repeated fails,
            // it does not grade flavors of success. From step 1 that is a SHORTER wait
            // than climbing would have given — intended, the word is catching on.
            for (step in 0..1) {
                for (rating in listOf(Rating.Hard, Rating.Good, Rating.Easy)) {
                    val passed = outcome(s, phase, step, rating)
                    assertEquals(CardPhase.Review, passed.phase)
                    assertNull(passed.stepIndex)
                    assertTrue(passed.intervalDays >= 1)
                }
            }
        }
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
