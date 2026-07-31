package net.spross.kern.box

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import net.spross.kern.model.Rating

/**
 * What the learner actually did today, in cards — the day's own report.
 * Reviews and misses are read live from the review logs, so the numbers hold
 * mid-session; introductions and settled crossings come from the day counters
 * the engine books at answer time.
 */
data class TodayReport(
    /** Answer events today (retries included — every answer is a review). */
    val reviews: Int,
    /** Words met for the first time today. */
    val introduced: Int,
    /** Words that crossed into settled today (see [Statistics.isSettled]). */
    val settled: Int,
    /** Answers rated Again today. */
    val missed: Int,
    /** The retention the box is scheduling for ([net.spross.kern.model.BoxConfig]). */
    val expectedRecall: Double,
) {
    /**
     * Share of today's answers the learner got.
     * Null below [MIN_ANSWERS_FOR_RECALL] — a handful of answers says nothing
     * about how a day is going, and a number built on three of them invites
     * exactly the over-reading it cannot support.
     */
    val recall: Double?
        get() = if (reviews >= MIN_ANSWERS_FOR_RECALL) 1.0 - missed.toDouble() / reviews else null

    /**
     * Today's recall is far enough under what the schedule expects
     * that more reps are unlikely to stick.
     *
     * The rule, not the remedy: what a surface does with it — suggest a break,
     * say nothing, soften the next prompt — is the app's call.
     */
    val recallStrained: Boolean
        get() = (recall ?: return false) < expectedRecall - RECALL_STRAIN_MARGIN

    companion object {
        /** Below this many answers a day has no recall number worth showing. */
        const val MIN_ANSWERS_FOR_RECALL: Int = 10

        /**
         * How far under the scheduled retention counts as strained.
         * FSRS aims at `desiredRetention` over the long run and single days scatter
         * widely around it, so the margin has to clear ordinary variance —
         * this is "today is going badly", not "today missed its target".
         */
        const val RECALL_STRAIN_MARGIN: Double = 0.2
    }
}

internal fun todayReport(state: BoxState, nowEpochMillis: Long, tzId: String): TodayReport {
    val zone = TimeZone.of(tzId)
    val start = localDate(nowEpochMillis, tzId).atStartOfDayIn(zone)
    val end = localDate(nowEpochMillis, tzId).plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
    val day = dayKey(nowEpochMillis, tzId)

    var reviews = 0
    var missed = 0
    for (sched in Inventory.scheduled(state)) {
        for (entry in sched.log) {
            if (entry.date < start || entry.date >= end) continue
            reviews += 1
            if (entry.rating == Rating.Again) missed += 1
        }
    }
    return TodayReport(
        reviews = reviews,
        introduced = state.newIntroduced[day] ?: 0,
        settled = state.settledCrossed[day] ?: 0,
        missed = missed,
        expectedRecall = state.config.desiredRetention,
    )
}
