package net.spross.kern.fsrs

import kotlin.math.roundToLong
import net.spross.kern.model.CardPhase
import net.spross.kern.model.MemoryState
import net.spross.kern.model.Rating

/** Scheduler-visible unit state: phase, step position within (re)learning, memory. */
data class SchedulerState(
    val phase: CardPhase = CardPhase.New,
    /** Step index while in Learning/Relearning; null otherwise. */
    val stepIndex: Int? = null,
    val memory: MemoryState? = null,
)

/** Outcome of one review: the next state plus the scheduled interval. */
data class SchedulerOutcome(
    val phase: CardPhase,
    val stepIndex: Int?,
    val memory: MemoryState,
    /** Whole-day interval; 0 while the unit sits in (re)learning steps. */
    val intervalDays: Int,
    /**
     * Seconds until due: the step delay while in (re)learning, else the
     * graduated interval quantized to `intervalGranularitySeconds` — which
     * equals `intervalDays * 86_400` only at the reference day granularity.
     */
    val intervalSeconds: Long,
)

/**
 * FSRS-6 learning/relearning-steps state machine over [Fsrs].
 *
 * Learning follows the reference machine: Again → step 0; Hard → hold with the
 * first-steps blend; Good → advance or graduate past the last step; Easy →
 * graduate. Relearning is a product-owned growing backoff instead (ruling
 * 2026-09-01, supersedes the 2026-08-07 leech ruling): Again climbs the ladder
 * rather than resetting, so repeated fails get spaced further apart instead of
 * repeating the same short wait; a single Good or Easy graduates immediately,
 * from wherever the ladder sits. Review + Again always opens Relearning at the
 * ladder's first entry. Graduation interval = I(desiredRetention, S′).
 *
 * Learning semantics follow py-fsrs v6.3.1 (single-outcome machine), with the
 * Hard interval rounded to whole minutes as ts-fsrs v5.4.1 pins it (6 m for
 * [1m, 10m]; ×1.5 rounded for a single step) — Relearning keeps the same Hard
 * blend, but its Again/Good behavior diverges as above.
 */
class FsrsScheduler(val parameters: FsrsParameters = FsrsParameters()) {

    val algorithm: Fsrs = Fsrs(parameters)

    /**
     * Apply one answer. `elapsedDays` is fractional days since the previous
     * review (ignored on the first review); the caller derives due as
     * now + [SchedulerOutcome.intervalSeconds].
     */
    fun review(state: SchedulerState, elapsedDays: Double, rating: Rating): SchedulerOutcome {
        val memory = algorithm.nextMemory(state.memory, elapsedDays, rating)
        return when (state.phase) {
            CardPhase.New ->
                learningStepOutcome(parameters.learningStepsSeconds, 0, rating, memory)
            CardPhase.Learning ->
                learningStepOutcome(parameters.learningStepsSeconds, state.stepIndex ?: 0, rating, memory)
            CardPhase.Relearning ->
                relearningStepOutcome(parameters.relearningStepsSeconds, state.stepIndex ?: 0, rating, memory)
            CardPhase.Review -> reviewOutcome(rating, memory)
        }
    }

    private fun reviewOutcome(rating: Rating, memory: MemoryState): SchedulerOutcome {
        val steps = parameters.relearningStepsSeconds
        // why: with no relearning steps configured, a lapsed card stays in Review
        // and reschedules from its post-lapse stability (reference behavior).
        return if (rating == Rating.Again && steps.isNotEmpty()) {
            stepped(CardPhase.Relearning, 0, steps[0], memory)
        } else {
            graduate(memory)
        }
    }

    private fun learningStepOutcome(
        steps: List<Long>,
        step: Int,
        rating: Rating,
        memory: MemoryState,
    ): SchedulerOutcome {
        // Past-the-end steps (config shrank) graduate on success; Again still restarts.
        if (steps.isEmpty() || (step >= steps.size && rating != Rating.Again)) {
            return graduate(memory)
        }
        return when (rating) {
            Rating.Again -> stepped(CardPhase.Learning, 0, steps[0], memory)
            Rating.Hard -> stepped(CardPhase.Learning, step, hardStepSeconds(steps), memory)
            Rating.Good ->
                if (step + 1 >= steps.size) graduate(memory)
                else stepped(CardPhase.Learning, step + 1, steps[step + 1], memory)
            Rating.Easy -> graduate(memory)
        }
    }

    private fun relearningStepOutcome(
        steps: List<Long>,
        step: Int,
        rating: Rating,
        memory: MemoryState,
    ): SchedulerOutcome {
        if (steps.isEmpty()) return graduate(memory)
        return when (rating) {
            // Climbs the ladder instead of resetting — repeated fails get more room
            // before their next try, capped at the ladder's last entry.
            Rating.Again -> {
                val next = (step + 1).coerceAtMost(steps.size - 1)
                stepped(CardPhase.Relearning, next, steps[next], memory)
            }
            Rating.Hard -> stepped(CardPhase.Relearning, step, hardStepSeconds(steps), memory)
            // A single Good/Easy graduates from wherever the ladder sits — it only
            // spaces out repeated fails, it is not a run of successes to climb back.
            Rating.Good, Rating.Easy -> graduate(memory)
        }
    }

    // why: both references blend the FIRST steps for Hard regardless of position;
    // ts-fsrs additionally rounds to whole minutes ((1m+10m)/2 → 6m, 1m×1.5 → 2m)
    // — the value the contract pins, so py-fsrs's exact 5.5m loses here.
    private fun hardStepSeconds(steps: List<Long>): Long {
        val minutes =
            if (steps.size == 1) steps[0] / 60.0 * 1.5
            else (steps[0] / 60.0 + steps[1] / 60.0) / 2.0
        return minutes.roundToLong() * 60L
    }

    private fun stepped(
        phase: CardPhase,
        stepIndex: Int,
        seconds: Long,
        memory: MemoryState,
    ): SchedulerOutcome = SchedulerOutcome(
        phase = phase,
        stepIndex = stepIndex,
        memory = memory,
        intervalDays = 0,
        intervalSeconds = seconds,
    )

    // why: the model asks for a fractional interval; whole-day rounding is the
    // reference bucket convention, not part of FSRS. Granularity and floor are
    // parameters so the product can schedule continuously while the golden
    // vectors keep their day multiples.
    private fun graduate(memory: MemoryState): SchedulerOutcome {
        val granularity = parameters.intervalGranularitySeconds
        val rawSeconds = algorithm.intervalRawDays(memory.stability) * 86_400.0
        val maxSeconds = parameters.maximumIntervalDays * 86_400L
        val seconds = ((rawSeconds / granularity).roundToLong() * granularity)
            .coerceAtLeast(parameters.minimumIntervalSeconds)
            .coerceAtMost(maxSeconds)
        return SchedulerOutcome(
            phase = CardPhase.Review,
            stepIndex = null,
            memory = memory,
            intervalDays = algorithm.intervalDays(memory.stability),
            intervalSeconds = seconds,
        )
    }
}
