package net.spross.kern.box

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import net.spross.kern.fsrs.FsrsScheduler
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.Rating

/**
 * What the learner actually did today, in cards — the day's own report.
 * Every count is read live from the review logs, so the numbers hold mid-session.
 */
data class TodayReport(
    /** Answer events today, retries included — the three kinds ([TallyPartKind]) added up. */
    val answers: Int,
    /** Today's first answers ([TallyPartKind.Introduced]). */
    val introduced: Int,
    /** Today's answers that carried a card across the settled bar ([TallyPartKind.Settled]). */
    val settled: Int,
    /** Answers rated Again today. */
    val missed: Int,
    /** The retention the box is scheduling for ([net.spross.kern.model.BoxConfig]). */
    val expectedRecall: Double,
) {
    /** Today's other answers ([TallyPartKind.Reviewed]). */
    val reviewed: Int get() = answers - introduced - settled

    /**
     * Whether the day was WORKED — the difference between a day finished and a day merely clear.
     * Nothing is due in either, but only one of them was earned,
     * and a surface must never claim a finish the learner never made.
     */
    val worked: Boolean get() = answers > 0

    /**
     * The day's answers spelled out, as a round's are ([tallyParts]) —
     * empty on a day that was not [worked]: an unworked day has a state, not a tally.
     */
    fun tallyParts(): List<TallyPart> = tallyParts(introduced, reviewed, settled)

    /**
     * Share of today's answers the learner got.
     * Null below [MIN_ANSWERS_FOR_RECALL] — a handful of answers says nothing
     * about how a day is going, and a number built on three of them invites
     * exactly the over-reading it cannot support.
     */
    val recall: Double?
        get() = if (answers >= MIN_ANSWERS_FOR_RECALL) 1.0 - missed.toDouble() / answers else null

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

/** What a day with nothing left to do says about the next one. */
enum class TomorrowNote {
    /** Words are packed and waiting; the round they arrive in is the answer. */
    Packed,

    /** Nothing comes back tomorrow — the day ahead is open ground. */
    Empty,

    /** Cards fall due inside tomorrow; the count is the caller's own. */
    Due,
}

/**
 * Which of the three the done day leaves the learner with.
 *
 * A pack outranks the due count: packing was the learner's own move,
 * a finished day composes nothing, and so the next round is where those words turn up —
 * said as a fact about that round, never as something waiting to be answered.
 * [tomorrowDue] is what [BoxEngine.dueCount] reports at [endOfTomorrow],
 * so the horizon is the engine's rather than a second local-midnight derivation.
 */
fun tomorrowNote(hasPackedWords: Boolean, tomorrowDue: Int): TomorrowNote = when {
    hasPackedWords -> TomorrowNote.Packed
    tomorrowDue == 0 -> TomorrowNote.Empty
    else -> TomorrowNote.Due
}

internal fun todayReport(state: BoxState, nowEpochMillis: Long, tzId: String): TodayReport {
    val zone = zoneOf(tzId)
    val start = localDate(nowEpochMillis, tzId).atStartOfDayIn(zone)
    val end = localDate(nowEpochMillis, tzId).plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
    val scheduler = FsrsScheduler(state.config.fsrsParameters())

    var answers = 0
    var missed = 0
    var introduced = 0
    var settled = 0
    // why: raw schedules rather than the join — an answer really happened, and switching
    // the known language must not un-happen a day's work.
    for (sched in state.scheduling.values) {
        if (sched.log.none { it.date >= start && it.date < end }) continue
        // A crossing reads a stability no log entry records, so the card's log is replayed
        // up to each of today's answers, through the same step that recorded it.
        var before = CardScheduling(cardId = sched.cardId)
        for (entry in sched.log) {
            val after = before.answered(entry.rating, entry.date, scheduler)
            if (entry.date >= start && entry.date < end) {
                answers += 1
                if (entry.rating == Rating.Again) missed += 1
                when (tallyKind(before, after)) {
                    TallyPartKind.Introduced -> introduced += 1
                    TallyPartKind.Settled -> settled += 1
                    TallyPartKind.Reviewed -> {}
                }
            }
            before = after
        }
    }
    return TodayReport(
        answers = answers,
        introduced = introduced,
        settled = settled,
        missed = missed,
        expectedRecall = state.config.desiredRetention,
    )
}
