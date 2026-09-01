package net.spross.kern.fsrs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating

/**
 * Learning/relearning-steps state machine vectors.
 *
 * Minute tables from ts-fsrs v5.4.1 strategies/learning-steps.test.ts
 * (commit bfc0a1960dfde4b4627ae4f4c8757b9211314963); machine transitions from
 * py-fsrs v6.3.1 tests/test_basic.py (commit 3abe686e9c058d3f3c00bbeb92e68b71211b2b31).
 *
 * Three reference divergences are resolved per the contract:
 * - Hard interval rounds to whole minutes (ts): [1m,10m] -> 6m where py pins 5.5m.
 * - Again on a past-the-end learning step restarts at step 0 (py): ts would graduate.
 * - Relearning is a growing backoff ladder, not the reference machine (product ruling
 *   2026-09-01): Again climbs the ladder instead of resetting, Good/Easy graduate from
 *   any step. Learning keeps the reference machine unchanged.
 */
class FsrsLearningStepsTest {

    private fun scheduler(
        learning: List<Long> = listOf(60L, 600L),
        relearning: List<Long> = listOf(600L),
    ) = FsrsScheduler(
        FsrsParameters(learningStepsSeconds = learning, relearningStepsSeconds = relearning),
    )

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

    // ts learning-steps.test.ts `learning_steps = ['1m', '10m']`:
    // Again 1m/step0, Hard 6m ((1+10)/2 rounded), Good 10m/step1 — New and Learning@0.
    @Test
    fun learningTwoStepsMinuteTable() {
        val s = scheduler()
        for (phase in listOf(CardPhase.New, CardPhase.Learning)) {
            val again = outcome(s, phase, 0, Rating.Again)
            assertEquals(60L, again.intervalSeconds)
            assertEquals(0, again.stepIndex)
            assertEquals(CardPhase.Learning, again.phase)

            val hard = outcome(s, phase, 0, Rating.Hard)
            assertEquals(360L, hard.intervalSeconds)
            assertEquals(0, hard.stepIndex)
            assertEquals(CardPhase.Learning, hard.phase)

            val good = outcome(s, phase, 0, Rating.Good)
            assertEquals(600L, good.intervalSeconds)
            assertEquals(1, good.stepIndex)
            assertEquals(CardPhase.Learning, good.phase)
        }
    }

    // ts learning-steps.test.ts `learning_steps = ['1m', '10m']` at step 1:
    // Again 1m/step0, Hard 6m/step1; Good has no row -> graduates.
    @Test
    fun learningTwoStepsAtStepOne() {
        val s = scheduler()
        val again = outcome(s, CardPhase.Learning, 1, Rating.Again)
        assertEquals(60L, again.intervalSeconds)
        assertEquals(0, again.stepIndex)

        val hard = outcome(s, CardPhase.Learning, 1, Rating.Hard)
        assertEquals(360L, hard.intervalSeconds)
        assertEquals(1, hard.stepIndex)

        val good = outcome(s, CardPhase.Learning, 1, Rating.Good)
        assertEquals(CardPhase.Review, good.phase)
        assertNull(good.stepIndex)
        assertTrue(good.intervalDays >= 1)
    }

    // ts learning-steps.test.ts `learning_steps = ['1m']`:
    // Again 1m/step0, Hard 2m (Math.round(1 × 1.5)).
    @Test
    fun learningSingleStepHardTimesOnePointFive() {
        val s = scheduler(learning = listOf(60L))
        val again = outcome(s, CardPhase.New, null, Rating.Again)
        assertEquals(60L, again.intervalSeconds)
        assertEquals(0, again.stepIndex)

        val hard = outcome(s, CardPhase.New, null, Rating.Hard)
        assertEquals(120L, hard.intervalSeconds)
        assertEquals(0, hard.stepIndex)

        val good = outcome(s, CardPhase.New, null, Rating.Good)
        assertEquals(CardPhase.Review, good.phase)
        assertTrue(good.intervalDays >= 1)
    }

    // ts relearning-steps `['10m']`: Review+Again -> 10m/step0; Relearning@0:
    // Again 10m/step0, Hard 15m (10 × 1.5); Good graduates (single step).
    @Test
    fun relearningSingleStepMinuteTable() {
        val s = scheduler()
        val lapse = outcome(s, CardPhase.Review, null, Rating.Again, elapsedDays = 2.0)
        assertEquals(CardPhase.Relearning, lapse.phase)
        assertEquals(0, lapse.stepIndex)
        assertEquals(600L, lapse.intervalSeconds)
        assertEquals(0, lapse.intervalDays)

        val again = outcome(s, CardPhase.Relearning, 0, Rating.Again)
        assertEquals(600L, again.intervalSeconds)
        assertEquals(0, again.stepIndex)
        assertEquals(CardPhase.Relearning, again.phase)

        val hard = outcome(s, CardPhase.Relearning, 0, Rating.Hard)
        assertEquals(900L, hard.intervalSeconds)
        assertEquals(0, hard.stepIndex)

        val good = outcome(s, CardPhase.Relearning, 0, Rating.Good)
        assertEquals(CardPhase.Review, good.phase)
        assertTrue(good.intervalDays >= 1)
    }

    // Relearning ladder `['10m', '20m']`: Again climbs it (10m -> 20m, held at the last
    // entry once there); Hard still holds the reference blend; Good/Easy graduate
    // immediately from wherever the ladder sits (product ruling 2026-09-01 — growing
    // backoff on repeated lapses, not a required run of successes).
    @Test
    fun relearningTwoStepsGrowsOnAgainGraduatesOnGood() {
        val s = scheduler(relearning = listOf(600L, 1200L))

        val again0 = outcome(s, CardPhase.Relearning, 0, Rating.Again)
        assertEquals(1200L, again0.intervalSeconds)
        assertEquals(1, again0.stepIndex)
        assertEquals(CardPhase.Relearning, again0.phase)

        val again1 = outcome(s, CardPhase.Relearning, 1, Rating.Again)
        assertEquals(1200L, again1.intervalSeconds) // already at the last step, holds
        assertEquals(1, again1.stepIndex)

        val hard0 = outcome(s, CardPhase.Relearning, 0, Rating.Hard)
        assertEquals(900L, hard0.intervalSeconds)
        assertEquals(0, hard0.stepIndex)

        val hard1 = outcome(s, CardPhase.Relearning, 1, Rating.Hard)
        assertEquals(900L, hard1.intervalSeconds)
        assertEquals(1, hard1.stepIndex)

        val good0 = outcome(s, CardPhase.Relearning, 0, Rating.Good)
        assertEquals(CardPhase.Review, good0.phase)
        assertNull(good0.stepIndex)
        assertTrue(good0.intervalDays >= 1)

        val good1 = outcome(s, CardPhase.Relearning, 1, Rating.Good)
        assertEquals(CardPhase.Review, good1.phase)
        assertNull(good1.stepIndex)
    }

    // py test_good_learning_steps / test_easy_learning_steps: Good advances then
    // graduates over a day out; Easy graduates immediately.
    @Test
    fun goodChainGraduatesEasySkips() {
        val s = scheduler()
        val first = s.review(SchedulerState(), 0.0, Rating.Good)
        assertEquals(CardPhase.Learning, first.phase)
        assertEquals(1, first.stepIndex)
        assertEquals(600L, first.intervalSeconds)

        val second = s.review(
            SchedulerState(first.phase, first.stepIndex, first.memory),
            600.0 / 86_400.0,
            Rating.Good,
        )
        assertEquals(CardPhase.Review, second.phase)
        assertNull(second.stepIndex)
        assertTrue(second.intervalSeconds >= 86_400L)

        val easy = s.review(SchedulerState(), 0.0, Rating.Easy)
        assertEquals(CardPhase.Review, easy.phase)
        assertTrue(easy.intervalSeconds >= 86_400L)
    }

    // py test_relearning: lapse -> 10m, Again again -> 10m/step0, Good -> Review >= 1d.
    @Test
    fun relearningRoundTrip() {
        val s = scheduler()
        var state = SchedulerState()
        for (rating in listOf(Rating.Good, Rating.Good)) {
            val o = s.review(state, if (state.memory == null) 0.0 else 600.0 / 86_400.0, rating)
            state = SchedulerState(o.phase, o.stepIndex, o.memory)
        }
        var o = s.review(state, 2.0, Rating.Good)
        state = SchedulerState(o.phase, o.stepIndex, o.memory)
        assertEquals(CardPhase.Review, state.phase)

        o = s.review(state, o.intervalDays.toDouble(), Rating.Again)
        assertEquals(CardPhase.Relearning, o.phase)
        assertEquals(0, o.stepIndex)
        assertEquals(600L, o.intervalSeconds)
        state = SchedulerState(o.phase, o.stepIndex, o.memory)

        o = s.review(state, 600.0 / 86_400.0, Rating.Again)
        assertEquals(CardPhase.Relearning, o.phase)
        assertEquals(0, o.stepIndex)
        assertEquals(600L, o.intervalSeconds)
        state = SchedulerState(o.phase, o.stepIndex, o.memory)

        o = s.review(state, 600.0 / 86_400.0, Rating.Good)
        assertEquals(CardPhase.Review, o.phase)
        assertNull(o.stepIndex)
        assertTrue(o.intervalSeconds >= 86_400L)
    }

    // py test_no_learning_steps: empty learning steps send even Again to Review.
    @Test
    fun noLearningStepsGraduateImmediately() {
        val s = scheduler(learning = emptyList())
        val o = s.review(SchedulerState(), 0.0, Rating.Again)
        assertEquals(CardPhase.Review, o.phase)
        assertTrue(o.intervalDays >= 1)
    }

    // py test_no_relearning_steps: a lapse with empty relearning steps stays in Review.
    @Test
    fun noRelearningStepsKeepLapseInReview() {
        val s = scheduler(relearning = emptyList())
        val memory = s.algorithm.nextMemory(null, 0.0, Rating.Good)
        val o = s.review(SchedulerState(CardPhase.Review, null, memory), 2.0, Rating.Again)
        assertEquals(CardPhase.Review, o.phase)
        assertNull(o.stepIndex)
        assertTrue(o.intervalDays >= 1)
    }

    // Past-the-end step after a config shrink (py edge semantics):
    // success graduates, Again restarts at step 0.
    @Test
    fun pastEndStepGraduatesOnSuccessRestartsOnAgain() {
        val s = scheduler(learning = listOf(60L))
        val good = outcome(s, CardPhase.Learning, 5, Rating.Good)
        assertEquals(CardPhase.Review, good.phase)

        val again = outcome(s, CardPhase.Learning, 5, Rating.Again)
        assertEquals(CardPhase.Learning, again.phase)
        assertEquals(0, again.stepIndex)
        assertEquals(60L, again.intervalSeconds)
    }
}
