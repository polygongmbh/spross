package net.spross.kern.box

import kotlin.math.max
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant
import net.spross.kern.fsrs.FsrsParameters
import net.spross.kern.fsrs.FsrsScheduler
import net.spross.kern.fsrs.SchedulerOutcome
import net.spross.kern.fsrs.SchedulerState
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.CardPhase
import net.spross.kern.model.ExerciseUnit
import net.spross.kern.model.ExerciseUnits
import net.spross.kern.model.Rating
import net.spross.kern.model.ReviewLogEntry
import net.spross.kern.model.Role
import net.spross.kern.model.UnitKey
import net.spross.kern.model.UnitScheduling

internal const val LEECH_LAPSE_THRESHOLD = 8

/** Product FSRS parameters: default weights, box retention/interval/steps. */
internal fun BoxConfig.fsrsParameters(): FsrsParameters = FsrsParameters(
    desiredRetention = desiredRetention,
    maximumIntervalDays = maximumIntervalDays,
    learningStepsSeconds = learningStepsSeconds,
    relearningStepsSeconds = relearningStepsSeconds,
)

// Answering: every answer event is an FSRS review; introduction = first answer of a UNIT.

internal object Answering {

    fun answer(
        state: BoxState,
        unitKey: String,
        rating: Rating,
        nowEpochMillis: Long,
        tzId: String,
    ): AnswerOutcome {
        val parsed = UnitKey.parse(unitKey) ?: return AnswerOutcome(state, AnswerStatus.StaleUnit)
        val card = state.cards[parsed.cardId] ?: return AnswerOutcome(state, AnswerStatus.StaleUnit)
        val unit = ExerciseUnits.of(card).firstOrNull { it.key == unitKey }
            ?: return AnswerOutcome(state, AnswerStatus.StaleUnit)
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        val scheduler = FsrsScheduler(state.config.fsrsParameters())
        val existing = state.scheduling[unitKey]
        return if (existing?.memory != null) {
            val next = reviewed(existing, rating, scheduler, now)
            AnswerOutcome(
                state.copy(scheduling = state.scheduling + (unitKey to next)),
                AnswerStatus.Applied,
            )
        } else {
            introduce(state, unit, parsed, existing, rating, scheduler, now, nowEpochMillis, tzId)
        }
    }

    // why: eligibility and the concept budget are re-checked at answer time (plans
    // outlive phase changes and may straddle midnight) — composition-only enforcement
    // would let recognize units free-ride in before their produce unit graduates.
    // Units of concepts already in flight ride free — they don't grow the pool.
    private fun introduce(
        state: BoxState,
        unit: ExerciseUnit,
        parsed: UnitKey,
        existing: UnitScheduling?,
        rating: Rating,
        scheduler: FsrsScheduler,
        now: Instant,
        nowEpochMillis: Long,
        tzId: String,
    ): AnswerOutcome {
        if (!Growth.isIntroducible(state, unit)) {
            return AnswerOutcome(state, AnswerStatus.DroppedIneligible)
        }
        val inFlight = parsed.cardId in Inventory.conceptsInFlight(state)
        if (!inFlight && Growth.learningPoolBudget(state) <= 0) {
            return AnswerOutcome(state, AnswerStatus.DroppedPoolFull)
        }
        val outcome = scheduler.review(SchedulerState(), 0.0, rating)
        val base = existing ?: UnitScheduling(
            cardId = parsed.cardId, role = parsed.role, form = parsed.form, addedAt = now,
        )
        val sched = applied(base, outcome, rating, now, elapsedDays = 0.0)
        var next = state.copy(scheduling = state.scheduling + (sched.key to sched))
        if (parsed.role == Role.Produce) {
            // A concept counts introduced (and dequeues) at its PRODUCE introduction only.
            val day = dayKey(nowEpochMillis, tzId)
            val introducedToday = (next.newIntroduced[day] ?: 0) + 1
            next = next.copy(
                newIntroduced = next.newIntroduced + (day to introducedToday),
                enqueued = next.enqueued.filter { it != parsed.cardId },
            )
        }
        return AnswerOutcome(next, AnswerStatus.Applied)
    }

    private fun reviewed(
        existing: UnitScheduling,
        rating: Rating,
        scheduler: FsrsScheduler,
        now: Instant,
    ): UnitScheduling {
        // why: elapsed comes from the last answer, never from `due` — overdue reviews
        // must credit the real elapsed time.
        val last = existing.log.lastOrNull()?.date ?: existing.addedAt
        val elapsedDays = max(0.0, (now - last).toDouble(DurationUnit.DAYS))
        val outcome = scheduler.review(
            SchedulerState(existing.phase, existing.stepIndex, existing.memory),
            elapsedDays,
            rating,
        )
        val lapsed = existing.phase == CardPhase.Review && rating == Rating.Again
        val lapses = existing.lapses + if (lapsed) 1 else 0
        val suspended = existing.suspended || (lapsed && lapses >= LEECH_LAPSE_THRESHOLD)
        return applied(existing, outcome, rating, now, elapsedDays)
            .copy(lapses = lapses, suspended = suspended)
    }

    private fun applied(
        base: UnitScheduling,
        outcome: SchedulerOutcome,
        rating: Rating,
        now: Instant,
        elapsedDays: Double,
    ): UnitScheduling = base.copy(
        phase = outcome.phase,
        stepIndex = outcome.stepIndex,
        memory = outcome.memory,
        due = now + outcome.intervalSeconds.seconds,
        log = base.log + ReviewLogEntry(date = now, rating = rating, elapsedDays = elapsedDays),
    )
}
