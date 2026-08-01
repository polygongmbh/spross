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
import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.Rating
import net.spross.kern.model.ReviewLogEntry

internal const val LEECH_LAPSE_THRESHOLD = 8

/** Product FSRS parameters: default weights, box retention/interval/steps. */
internal fun BoxConfig.fsrsParameters(): FsrsParameters = FsrsParameters(
    desiredRetention = desiredRetention,
    maximumIntervalDays = maximumIntervalDays,
    learningStepsSeconds = learningStepsSeconds,
    relearningStepsSeconds = relearningStepsSeconds,
    // why: the product schedules continuously — whole-day rounding is the
    // reference bucket convention, and a day is already the floor.
    intervalGranularitySeconds = 1L,
)

// Answering: every answer event is an FSRS review — production and recognition
// presentations feed the same schedule. Introduction = the card's first answer.

internal object Answering {

    fun answer(
        state: BoxState,
        cardId: String,
        rating: Rating,
        nowEpochMillis: Long,
        tzId: String,
    ): AnswerOutcome {
        val card = state.cards[cardId] ?: return AnswerOutcome(state, AnswerStatus.StaleCard)
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        val scheduler = FsrsScheduler(state.config.fsrsParameters())
        val existing = state.scheduling[cardId]
        return if (existing?.memory != null) {
            val wasConsolidated = Statistics.isConsolidated(state, existing)
            val next = reviewed(existing, rating, scheduler, now)
            AnswerOutcome(
                state.copy(
                    scheduling = state.scheduling + (cardId to next),
                    settledCrossed = state.settledCrossed.bookIf(
                        !wasConsolidated && Statistics.isConsolidated(state, next),
                        dayKey(nowEpochMillis, tzId),
                    ),
                ),
                AnswerStatus.Applied,
            )
        } else {
            introduce(state, card, existing, rating, scheduler, now, nowEpochMillis, tzId)
        }
    }

    // why: eligibility is re-checked at answer time (plans outlive phase changes
    // and may straddle midnight) — composition-only enforcement would let a
    // stale plan introduce a still-locked phrase. There is no budget re-check:
    // intake is bounded per composed round, not per card answered.
    private fun introduce(
        state: BoxState,
        card: Card,
        existing: CardScheduling?,
        rating: Rating,
        scheduler: FsrsScheduler,
        now: Instant,
        nowEpochMillis: Long,
        tzId: String,
    ): AnswerOutcome {
        if (!Growth.isIntroducible(state, card)) {
            return AnswerOutcome(state, AnswerStatus.DroppedIneligible)
        }
        val outcome = scheduler.review(SchedulerState(), 0.0, rating)
        val base = existing ?: CardScheduling(cardId = card.id, addedAt = now)
        val sched = applied(base, outcome, rating, now, elapsedDays = 0.0)
        val day = dayKey(nowEpochMillis, tzId)
        val next = state.copy(
            scheduling = state.scheduling + (card.id to sched),
            newIntroduced = state.newIntroduced + (day to (state.newIntroduced[day] ?: 0) + 1),
            // A word known on sight (Easy) consolidates the moment it arrives —
            // introduced and settled on the same answer, and the day's report says both.
            settledCrossed = state.settledCrossed.bookIf(Statistics.isConsolidated(state, sched), day),
            enqueued = state.enqueued.filter { it != card.id },
        )
        return AnswerOutcome(next, AnswerStatus.Applied)
    }

    /** One more on [day] when [happened], else the map untouched. */
    private fun Map<String, Int>.bookIf(happened: Boolean, day: String): Map<String, Int> =
        if (happened) this + (day to (this[day] ?: 0) + 1) else this

    private fun reviewed(
        existing: CardScheduling,
        rating: Rating,
        scheduler: FsrsScheduler,
        now: Instant,
    ): CardScheduling {
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
        base: CardScheduling,
        outcome: SchedulerOutcome,
        rating: Rating,
        now: Instant,
        elapsedDays: Double,
    ): CardScheduling = base.copy(
        phase = outcome.phase,
        stepIndex = outcome.stepIndex,
        memory = outcome.memory,
        due = now + outcome.intervalSeconds.seconds,
        log = base.log + ReviewLogEntry(date = now, rating = rating, elapsedDays = elapsedDays),
    )
}
