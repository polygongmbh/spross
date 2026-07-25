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
    /** Seconds until due: the step delay, or `intervalDays * 86_400`. */
    val intervalSeconds: Long,
)

/**
 * FSRS-6 learning/relearning-steps state machine over [Fsrs]:
 * Again → step 0; Hard → hold with the first-steps blend; Good → advance or
 * graduate past the last step; Easy → graduate; Review + Again → Relearning
 * (lapse). Graduation interval = I(desiredRetention, S′).
 *
 * Semantics follow py-fsrs v6.3.1 (single-outcome machine), with the Hard
 * interval rounded to whole minutes as ts-fsrs v5.4.1 pins it (6 m for
 * [1m, 10m]; ×1.5 rounded for a single step).
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
                stepOutcome(parameters.learningStepsSeconds, CardPhase.Learning, 0, rating, memory)
            CardPhase.Learning ->
                stepOutcome(
                    parameters.learningStepsSeconds, CardPhase.Learning,
                    state.stepIndex ?: 0, rating, memory,
                )
            CardPhase.Relearning ->
                stepOutcome(
                    parameters.relearningStepsSeconds, CardPhase.Relearning,
                    state.stepIndex ?: 0, rating, memory,
                )
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

    private fun stepOutcome(
        steps: List<Long>,
        phase: CardPhase,
        step: Int,
        rating: Rating,
        memory: MemoryState,
    ): SchedulerOutcome {
        // Past-the-end steps (config shrank) graduate on success; Again still restarts.
        if (steps.isEmpty() || (step >= steps.size && rating != Rating.Again)) {
            return graduate(memory)
        }
        return when (rating) {
            Rating.Again -> stepped(phase, 0, steps[0], memory)
            Rating.Hard -> stepped(phase, step, hardStepSeconds(steps), memory)
            Rating.Good ->
                if (step + 1 >= steps.size) graduate(memory)
                else stepped(phase, step + 1, steps[step + 1], memory)
            Rating.Easy -> graduate(memory)
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

    private fun graduate(memory: MemoryState): SchedulerOutcome {
        val days = algorithm.intervalDays(memory.stability)
        return SchedulerOutcome(
            phase = CardPhase.Review,
            stepIndex = null,
            memory = memory,
            intervalDays = days,
            intervalSeconds = days * 86_400L,
        )
    }
}
