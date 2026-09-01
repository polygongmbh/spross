package net.spross.kern.fsrs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating

/**
 * This port's OWN (re)learning-step machine — one ladder shared by Learning and
 * Relearning (product ruling 2026-09-01; see [FsrsScheduler]'s KDoc). Again is the
 * only rating that stays on the ladder; Hard, Good and Easy graduate from wherever it
 * sits. Every number here is self-computed against that machine, including in the
 * trajectories seeded from upstream's fixtures ([firstRepeatStatesAndIntervals],
 * [flagshipIvlHistory], [retrievabilityAtDueAfterFirstRating]) — those started as
 * ts-fsrs/py-fsrs vectors and are kept for their inputs, not their expectations.
 * The memory-state numbers upstream still pins are [FsrsGoldenVectorTest]'s.
 */
class FsrsStepLadderTest {

    private val grades = listOf(Rating.Again, Rating.Hard, Rating.Good, Rating.Easy)

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

    // The four grades on a brand-new card (ts-fsrs FSRS-6.test.ts "first repeat"'s
    // setup): only Again opens the ladder, the rest go straight to Review, so the
    // scheduled days follow from S0 rather than from upstream's step walk.
    @Test
    fun firstRepeatStatesAndIntervals() {
        val scheduler = FsrsScheduler()
        val outcomes = grades.map { scheduler.review(SchedulerState(), 0.0, it) }
        assertEquals(listOf(0, 1, 2, 8), outcomes.map { it.intervalDays })
        assertEquals(
            listOf(CardPhase.Learning, CardPhase.Review, CardPhase.Review, CardPhase.Review),
            outcomes.map { it.phase },
        )
        assertEquals(listOf(0, null, null, null), outcomes.map { it.stepIndex })
    }

    // A long trajectory reviewed exactly at due — ratings from ts-fsrs FSRS-6.test.ts
    // "ivl_history" / py-fsrs test_review_card, intervals this machine's own: the first
    // Good graduates straight from New instead of walking a second Learning step, and
    // every following review compounds that departure.
    @Test
    fun flagshipIvlHistory() {
        val scheduler = FsrsScheduler()
        val ratings = listOf(
            Rating.Good, Rating.Good, Rating.Good, Rating.Good, Rating.Good, Rating.Good,
            Rating.Again, Rating.Again,
            Rating.Good, Rating.Good, Rating.Good, Rating.Good, Rating.Good,
        )
        var state = SchedulerState()
        var nowSeconds = 0L
        var lastSeconds = 0L
        val history = mutableListOf<Int>()
        for (rating in ratings) {
            val elapsedDays = (nowSeconds - lastSeconds) / 86_400.0
            val outcome = scheduler.review(state, elapsedDays, rating)
            history += outcome.intervalDays
            state = SchedulerState(outcome.phase, outcome.stepIndex, outcome.memory)
            lastSeconds = nowSeconds
            nowSeconds += outcome.intervalSeconds
        }
        assertEquals(listOf(2, 11, 46, 163, 497, 1346, 0, 0, 3, 5, 9, 16, 26), history)
    }

    // R at the due day each grade schedules (ts-fsrs FSRS-6.test.ts "get retrievability"'s
    // question, this machine's due days): [Fsrs.retrievability] is upstream's formula,
    // but the day it is asked about comes from the graduation above.
    @Test
    fun retrievabilityAtDueAfterFirstRating() {
        val scheduler = FsrsScheduler()
        val expected = listOf(1.0, 0.9166697187203525, 0.9094932559773545, 0.9024733)
        for (i in grades.indices) {
            val outcome = scheduler.review(SchedulerState(), 0.0, grades[i])
            val r = scheduler.algorithm.retrievability(
                outcome.intervalDays.toDouble(),
                outcome.memory.stability,
            )
            assertEquals(expected[i], r, 1e-6)
        }
    }

    // Contract invariant: no outcome ever leaves New phase or a null memory behind.
    @Test
    fun reviewAlwaysLeavesNewPhase() {
        val scheduler = FsrsScheduler()
        for (grade in grades) {
            val outcome = scheduler.review(SchedulerState(), 0.0, grade)
            assertEquals(true, outcome.phase != CardPhase.New)
            if (outcome.phase == CardPhase.Review) assertNull(outcome.stepIndex)
        }
    }
}
