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
    ): BoxState {
        val card = state.cards[cardId] ?: return state
        val now = stampOf(nowEpochMillis)
        val scheduler = FsrsScheduler(state.config.fsrsParameters())
        val existing = state.scheduling[cardId]
        val introducing = existing?.memory == null
        val base = existing ?: CardScheduling(cardId = card.id)
        val sched = base.answered(rating, now, scheduler)
        return state.copy(
            scheduling = state.scheduling + (card.id to sched),
            queued = if (introducing) state.queued.filter { it != card.id } else state.queued,
        )
    }
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
    val last = log.lastOrNull()?.date
    val elapsedDays =
        if (introducing || last == null) 0.0 else max(0.0, (at - last).toDouble(DurationUnit.DAYS))
    val outcome = scheduler.review(SchedulerState(phase, stepIndex, memory), elapsedDays, rating)
    return copy(
        phase = outcome.phase,
        stepIndex = outcome.stepIndex,
        memory = outcome.memory,
        due = at + outcome.intervalSeconds.seconds,
        log = log + ReviewLogEntry(date = at, rating = rating),
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
    val base = CardScheduling(cardId = cardId, suspended = suspended)
    return log.fold(base) { sched, entry -> sched.answered(entry.rating, entry.date, scheduler) }
        .copy(due = due)
}
