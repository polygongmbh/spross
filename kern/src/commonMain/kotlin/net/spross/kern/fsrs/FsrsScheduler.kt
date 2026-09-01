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
 * FSRS-6 (re)learning-steps state machine over [Fsrs]: ONE growing-backoff
 * ladder ([FsrsParameters.stepsSeconds]) shared by Learning and Relearning
 * (product ruling 2026-09-01, supersedes the 2026-08-07 leech ruling and the
 * earlier per-phase split into two arrays) — a new word and a lapsed word
 * wait on the same cadence. Again climbs the ladder rather than resetting to
 * step 0, so repeated fails get spaced further apart instead of repeating the
 * same short wait; a single Good or Easy graduates immediately, from wherever
 * the ladder sits, rather than requiring a run of successes through the
 * remaining steps. Hard holds at the first-steps blend. Review + Again always
 * opens Relearning at the ladder's first entry. Graduation interval =
 * I(desiredRetention, S′).
 *
 * The Hard interval rounds to whole minutes as ts-fsrs v5.4.1 pins it (6 m for
 * [1m, 10m]; ×1.5 rounded for a single step) — the one piece of the reference
 * step machine this keeps; Again/Good/Easy diverge from py-fsrs/ts-fsrs as
 * above. [Fsrs.nextMemory]'s stability/difficulty math is unaffected and
 * still matches the reference exactly.
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
            // Step -1: New has never stood on the ladder, so its first Again must land
            // on step 0 (a same-day retry) rather than climb past it — a word appears
            // at most twice on its first day, not once.
            CardPhase.New ->
                stepOutcome(parameters.stepsSeconds, CardPhase.Learning, -1, rating, memory)
            CardPhase.Learning ->
                stepOutcome(parameters.stepsSeconds, CardPhase.Learning, state.stepIndex ?: 0, rating, memory)
            CardPhase.Relearning ->
                stepOutcome(parameters.stepsSeconds, CardPhase.Relearning, state.stepIndex ?: 0, rating, memory)
            CardPhase.Review -> reviewOutcome(rating, memory)
        }
    }

    private fun reviewOutcome(rating: Rating, memory: MemoryState): SchedulerOutcome {
        val steps = parameters.stepsSeconds
        // why: with no relearning steps configured, a lapsed card stays in Review
        // and reschedules from its post-lapse stability (reference behavior).
        return if (rating == Rating.Again && steps.isNotEmpty()) {
            stepped(CardPhase.Relearning, 0, steps[0], memory)
        } else {
            graduate(memory)
        }
    }

    // One growing-backoff ladder, shared by Learning and Relearning: Again climbs it
    // instead of resetting, capped at the last entry; a single Good/Easy graduates
    // immediately from wherever it sits — it only spaces out repeated fails, it is
    // not a run of successes to climb back. `step` can arrive as -1 (New's first
    // answer, never yet on the ladder) — coerced up so Hard never stores a negative
    // stepIndex, and Again's climb naturally lands a first-ever fail on step 0.
    private fun stepOutcome(
        steps: List<Long>,
        phase: CardPhase,
        step: Int,
        rating: Rating,
        memory: MemoryState,
    ): SchedulerOutcome {
        if (steps.isEmpty()) return graduate(memory)
        return when (rating) {
            Rating.Again -> {
                val next = (step + 1).coerceIn(0, steps.size - 1)
                stepped(phase, next, steps[next], memory)
            }
            Rating.Hard -> stepped(phase, step.coerceAtLeast(0), hardStepSeconds(steps), memory)
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
