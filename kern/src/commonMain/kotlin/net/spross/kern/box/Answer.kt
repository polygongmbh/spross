package net.spross.kern.box

import kotlin.math.max
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant
import net.spross.kern.fsrs.FsrsParameters
import net.spross.kern.fsrs.FsrsScheduler
import net.spross.kern.fsrs.SchedulerState
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.Rating
import net.spross.kern.model.ReviewLogEntry

/** Product FSRS parameters: default weights, box retention/interval/steps. */
internal fun BoxConfig.fsrsParameters(): FsrsParameters = FsrsParameters(
    desiredRetention = desiredRetention,
    maximumIntervalDays = maximumIntervalDays,
    stepsSeconds = stepsSeconds,
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
    ): BoxState {
        val card = state.cards[cardId] ?: return state
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        val scheduler = FsrsScheduler(state.config.fsrsParameters())
        val existing = state.scheduling[cardId]
        val introducing = existing?.memory == null
        val base = existing ?: CardScheduling(cardId = card.id, addedAt = now)
        val sched = base.answered(rating, now, scheduler)
        val day = dayKey(nowEpochMillis, tzId)
        return state.copy(
            scheduling = state.scheduling + (card.id to sched),
            newIntroduced =
                if (introducing) state.newIntroduced + (day to (state.newIntroduced[day] ?: 0) + 1)
                else state.newIntroduced,
            // Crossing the fully-grown bar on the very answer that introduces a card is
            // rare — no graduating rating reaches it alone — but the check stays generic
            // rather than assuming introduction can never be the crossing day.
            consolidatedCrossed = state.consolidatedCrossed.bookIf(
                !Statistics.isConsolidated(state, base) && Statistics.isConsolidated(state, sched),
                day,
            ),
            enqueued = if (introducing) state.enqueued.filter { it != card.id } else state.enqueued,
        )
    }

    /** One more on [day] when [happened], else the map untouched. */
    private fun Map<String, Int>.bookIf(happened: Boolean, day: String): Map<String, Int> =
        if (happened) this + (day to (this[day] ?: 0) + 1) else this
}

/**
 * One answer applied to a schedule — the ONE step a live answer and a replayed log share,
 * so a box rebuilt from its own log cannot drift from the box that recorded it.
 *
 * A schedule with no memory yet is being INTRODUCED: the scheduler starts fresh, no time
 * has elapsed, and the Again that may come with it is not a lapse.
 */
internal fun CardScheduling.answered(
    rating: Rating,
    at: Instant,
    scheduler: FsrsScheduler,
): CardScheduling {
    val introducing = memory == null
    // why: elapsed comes from the last answer, never from `due` — overdue reviews
    // must credit the real elapsed time.
    val last = log.lastOrNull()?.date ?: addedAt
    val elapsedDays = if (introducing) 0.0 else max(0.0, (at - last).toDouble(DurationUnit.DAYS))
    val outcome = scheduler.review(SchedulerState(phase, stepIndex, memory), elapsedDays, rating)
    return copy(
        phase = outcome.phase,
        stepIndex = outcome.stepIndex,
        memory = outcome.memory,
        due = at + outcome.intervalSeconds.seconds,
        // why: counts any Again past introduction, not just review-phase ones — feeds
        // drill and listening scoring (LetterDrill, ListeningPool) even though it no
        // longer drives suspension; a lapse grows the wait before its next try instead
        // (FsrsScheduler.stepOutcome).
        lapses = lapses + if (rating == Rating.Again && !introducing) 1 else 0,
        log = log + ReviewLogEntry(date = at, rating = rating, elapsedDays = elapsedDays),
    )
}

/**
 * The schedule a log implies: every answer applied in the order it was RECORDED — a watch
 * answer can arrive dated before the one already logged — and then the stored [due], which
 * the log cannot give back.
 */
internal fun replayed(
    cardId: String,
    due: Instant,
    log: List<ReviewLogEntry>,
    suspended: Boolean,
    scheduler: FsrsScheduler,
): CardScheduling {
    require(log.isNotEmpty()) { "replaying $cardId needs at least one answer" }
    val base = CardScheduling(cardId = cardId, addedAt = log.first().date, suspended = suspended)
    return log.fold(base) { sched, entry -> sched.answered(entry.rating, entry.date, scheduler) }
        .copy(due = due)
}
