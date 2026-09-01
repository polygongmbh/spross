package net.spross.kern.fsrs

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.model.MemoryState
import net.spross.kern.model.Rating

/**
 * Golden vectors copied VERBATIM from the pinned FSRS-6 references:
 * - ts-fsrs v5.4.1, github.com/open-spaced-repetition/ts-fsrs,
 *   commit bfc0a1960dfde4b4627ae4f4c8757b9211314963 (MIT)
 * - py-fsrs v6.3.1, github.com/open-spaced-repetition/py-fsrs,
 *   commit 3abe686e9c058d3f3c00bbeb92e68b71211b2b31 (MIT)
 * Full pin record: src/jvmTest/resources/fsrs6-provenance.json.
 *
 * All vectors are fuzz-off, use the default 21-weight vector, and review exactly
 * at due — where both references' whole-day elapsed conventions agree with this
 * port's fractional-days convention.
 *
 * MEMORY-STATE pins only: stability, difficulty, retrievability and the interval
 * formula, which [Fsrs] runs unmodified against the reference. The phase/step/interval
 * TRAJECTORY a card takes is this port's own (product ruling 2026-09-01) and is pinned
 * by [FsrsStepLadderTest] instead — nothing self-computed belongs in this file.
 */
class FsrsGoldenVectorTest {

    private val grades = listOf(Rating.Again, Rating.Hard, Rating.Good, Rating.Easy)

    // ts-fsrs FSRS-6.test.ts "first repeat": initial S/D after each of the four grades.
    // The states those outcomes carry are [FsrsStepLadderTest]'s, not upstream's.
    @Test
    fun firstRepeatInitialStates() {
        val scheduler = FsrsScheduler()
        val outcomes = grades.map { scheduler.review(SchedulerState(), 0.0, it) }

        val expectedStability = listOf(0.212, 1.2931, 2.3065, 8.2956)
        val expectedDifficulty = listOf(6.4133, 5.11217071, 2.11810397, 1.0)
        for (i in grades.indices) {
            assertEquals(expectedStability[i], outcomes[i].memory.stability, 1e-9)
            assertEquals(expectedDifficulty[i], outcomes[i].memory.difficulty, 1e-6)
        }
    }

    // ts-fsrs FSRS-6.test.ts "memory state[short-term]" == py-fsrs test_memo_state,
    // cross-referenced by both to fsrs-rs inference.rs L836-841:
    // ratings [A,G,G,G,G,G] at day offsets [0,0,1,3,8,21] -> S 53.62691, D 6.3574867.
    @Test
    fun memoryStateEndpointViaAlgorithm() {
        val fsrs = Fsrs()
        val ratings = listOf(
            Rating.Again, Rating.Good, Rating.Good, Rating.Good, Rating.Good, Rating.Good,
        )
        val elapsed = listOf(0.0, 0.0, 1.0, 3.0, 8.0, 21.0)
        var memory: MemoryState? = null
        for (i in ratings.indices) {
            memory = fsrs.nextMemory(memory, elapsed[i], ratings[i])
        }
        assertEquals(53.62691, memory!!.stability, 1e-4)
        assertEquals(6.3574867, memory.difficulty, 1e-4)
    }

    // Same endpoint driven through the steps state machine (py-fsrs drives its
    // scheduler with explicit review datetimes at those offsets).
    @Test
    fun memoryStateEndpointViaScheduler() {
        val scheduler = FsrsScheduler()
        val ratings = listOf(
            Rating.Again, Rating.Good, Rating.Good, Rating.Good, Rating.Good, Rating.Good,
        )
        val elapsed = listOf(0.0, 0.0, 1.0, 3.0, 8.0, 21.0)
        var state = SchedulerState()
        for (i in ratings.indices) {
            val outcome = scheduler.review(state, elapsed[i], ratings[i])
            state = SchedulerState(outcome.phase, outcome.stepIndex, outcome.memory)
        }
        val endpoint = state.memory!!
        assertEquals(53.62691, endpoint.stability, 1e-4)
        assertEquals(6.3574867, endpoint.difficulty, 1e-4)
    }

    // ts-fsrs algorithm.test.ts "forgetting_curve" (cross-ref fsrs-rs pre_training.rs):
    // default w (decay 0.1542), S = 1, t = [0,1,2,3].
    @Test
    fun forgettingCurve() {
        val fsrs = Fsrs()
        val expected = listOf(1.0, 0.9, 0.84588465, 0.8093881)
        for (t in 0..3) {
            assertEquals(expected[t], fsrs.retrievability(t.toDouble(), 1.0), 1e-8)
        }
    }

    // ts-fsrs algorithm.test.ts "next_ivl": S = 1.0, desired retention 0.1..1.0,
    // unbounded maximum interval. Cross-ref fsrs-rs inference.rs; the 0.1 entry is
    // the f64 value 3116769 (fsrs-rs's f32 arrives at 3116766) — pinned for Double.
    @Test
    fun nextIntervalRetentionTable() {
        val fsrs = Fsrs(FsrsParameters(maximumIntervalDays = Int.MAX_VALUE))
        val expected = listOf(3116769, 34793, 2508, 387, 90, 27, 9, 3, 1, 1)
        val actual = (1..10).map { fsrs.intervalDays(1.0, it / 10.0) }
        assertEquals(expected, actual)
    }

    // ts-fsrs algorithm.test.ts "change Params": interval_modifier(0.9) == 1 exactly,
    // so I(0.9, S) = round(S); plus the round-then-clamp bounds.
    @Test
    fun intervalIdentityAtDefaultRetention() {
        val fsrs = Fsrs()
        assertEquals(1, fsrs.intervalDays(1.0))
        assertEquals(2, fsrs.intervalDays(2.3065))
        assertEquals(8, fsrs.intervalDays(8.2956))
        assertEquals(499, fsrs.intervalDays(498.5))
        assertEquals(1, fsrs.intervalDays(0.001))
        assertEquals(36500, fsrs.intervalDays(36500.0))
        val capped = Fsrs(FsrsParameters(maximumIntervalDays = 365))
        assertEquals(365, capped.intervalDays(737.47))
    }

    // ts-fsrs algorithm.test.ts "next_ds" -> next_difficulty(d = 5.0, G = A/H/G/E).
    @Test
    fun nextDifficultyUnitVector() {
        val fsrs = Fsrs()
        val expected = listOf(8.34176237, 6.66599536, 4.99022837, 3.31446137)
        for (i in grades.indices) {
            assertEquals(expected[i], fsrs.nextDifficulty(5.0, grades[i]), 1e-6)
        }
    }

    // ts-fsrs algorithm.test.ts "next_ds" -> next_recall_stability
    // (S = 5, D = [1,2,3,4], R = [0.9,0.8,0.7,0.6], G = A/H/G/E).
    @Test
    fun nextRecallStabilityUnitVector() {
        val fsrs = Fsrs()
        val d = listOf(1.0, 2.0, 3.0, 4.0)
        val r = listOf(0.9, 0.8, 0.7, 0.6)
        val expected = listOf(25.60252118, 28.22657096, 58.65599107, 127.2266925)
        for (i in grades.indices) {
            assertEquals(expected[i], fsrs.recallStability(d[i], 5.0, r[i], grades[i]), 1e-6)
        }
    }

    // ts-fsrs algorithm.test.ts "next_ds" -> next_forget_stability (same inputs).
    @Test
    fun nextForgetStabilityUnitVector() {
        val fsrs = Fsrs()
        val d = listOf(1.0, 2.0, 3.0, 4.0)
        val r = listOf(0.9, 0.8, 0.7, 0.6)
        val expected = listOf(1.05253961, 1.18943295, 1.36808387, 1.58498896)
        for (i in grades.indices) {
            assertEquals(expected[i], fsrs.forgetStability(d[i], 5.0, r[i]), 1e-6)
        }
    }

    // ts-fsrs algorithm.test.ts "next_ds" -> next_short_term_stability(S = 5, G = A/H/G/E);
    // the two 5s pin the sinc >= 1 mask for Hard/Good (raw sinc < 1 there).
    @Test
    fun nextShortTermStabilityUnitVector() {
        val fsrs = Fsrs()
        val expected = listOf(1.596818, 5.0, 5.0, 8.12960956)
        for (i in grades.indices) {
            assertEquals(expected[i], fsrs.shortTermStability(5.0, grades[i]), 1e-6)
        }
    }

    // py-fsrs test_repeated_correct_reviews: ten same-instant Easy reviews drive
    // difficulty to the 1.0 floor.
    @Test
    fun repeatedEasyReachesDifficultyFloor() {
        val fsrs = Fsrs()
        var memory: MemoryState? = null
        repeat(10) { memory = fsrs.nextMemory(memory, 0.0, Rating.Easy) }
        assertEquals(1.0, memory!!.difficulty, 0.0)
    }
}
