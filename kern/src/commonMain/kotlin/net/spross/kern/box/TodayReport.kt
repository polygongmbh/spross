package net.spross.kern.box

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import net.spross.kern.model.Rating

/**
 * What the learner actually did today, in cards — the day's own report.
 * Reviews and misses are read live from the review logs, so the numbers hold
 * mid-session; introductions and consolidated crossings come from the day counters
 * the engine books at answer time.
 */
data class TodayReport(
    /** Answer events today (retries included — every answer is a review). */
    val reviews: Int,
    /** Words met for the first time today. */
    val introduced: Int,
    /** Words that crossed into consolidated today (see [Statistics.isConsolidated]). */
    val consolidated: Int,
    /**
     * Of today's first meetings, the ones still fresh — met today and not
     * consolidated (yet). Read live from the cards themselves, so a word known
     * on sight leaves it the moment it lands, and one that lapses back returns:
     * [introduced] minus [consolidated] cannot say this, since [consolidated] also counts
     * long-standing words crossing the bar today.
     */
    val stillFresh: Int,
    /** Answers rated Again today. */
    val missed: Int,
    /** The retention the box is scheduling for ([net.spross.kern.model.BoxConfig]). */
    val expectedRecall: Double,
) {
    /**
     * Whether the day was WORKED — the difference between a day finished and a day merely clear.
     * Nothing is due in either, but only one of them was earned,
     * and a surface must never claim a finish the learner never made.
     */
    val worked: Boolean get() = reviews > 0

    /**
     * The day's gain, spelled out — which counts a surface names, in which order.
     * Empty on a day that was not [worked]: an unworked day has a state, not a tally.
     * Reviews lead (the one count every worked day carries),
     * then today's first meetings, then the crossings — the rarest part reads last.
     * The words for each part are the platform's; which parts there are is not.
     */
    fun tallyParts(): List<TallyPart> {
        if (!worked) return emptyList()
        val parts = mutableListOf(TallyPart(TallyPartKind.Reviews, reviews))
        if (introduced > 0) parts += TallyPart(TallyPartKind.Introduced, introduced)
        if (consolidated > 0) parts += TallyPart(TallyPartKind.Consolidated, consolidated)
        return parts
    }

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

/**
 * Which count a spelled-out tally part names.
 * The kinds are the rule; the words, the plurals and the separator between them are the platform's.
 */
enum class TallyPartKind {
    /** Answers given — every answer is a review. */
    Reviews,

    /** Words met for the first time. */
    Introduced,

    /** Words that crossed the consolidated bar ([Statistics.isConsolidated]). */
    Consolidated,
}

/** One part of a tally: which count, and how many. */
data class TallyPart(val kind: TallyPartKind, val count: Int)

/**
 * What one finished ROUND bought, in the order a summary reads it:
 * words started, words that landed, answers given.
 *
 * Non-zero parts only — a round that started nothing has nothing to say about first meetings,
 * and a zero spelled out reads as a failure to reach a target the box never sets.
 * An empty list means the round is over with nothing nameable in it,
 * which is a surface's cue to say so plainly rather than to print three zeros.
 */
fun completionTallyParts(introduced: Int, consolidated: Int, reviews: Int): List<TallyPart> =
    listOf(
        TallyPart(TallyPartKind.Introduced, introduced),
        TallyPart(TallyPartKind.Consolidated, consolidated),
        TallyPart(TallyPartKind.Reviews, reviews),
    ).filter { it.count > 0 }

/** What a day with nothing left to do says about the next one. */
enum class TomorrowNote {
    /** Words are packed and waiting; the round they arrive in is the answer. */
    Packed,

    /** Nothing comes back tomorrow — the day ahead is open ground. */
    Fresh,

    /** Cards fall due inside tomorrow; the count is the caller's own. */
    Due,
}

/**
 * Which of the three the done day leaves the learner with.
 *
 * A pack outranks the due count: packing was the learner's own move,
 * a finished day composes nothing, and so the next round is where those words turn up —
 * said as a fact about that round, never as something waiting to be answered.
 * [tomorrowDue] is what [BoxEngine.dueNow] reports at [endOfTomorrow],
 * so the horizon is the engine's rather than a second local-midnight derivation.
 */
fun tomorrowNote(hasPackedWords: Boolean, tomorrowDue: Int): TomorrowNote = when {
    hasPackedWords -> TomorrowNote.Packed
    tomorrowDue == 0 -> TomorrowNote.Fresh
    else -> TomorrowNote.Due
}

internal fun todayReport(state: BoxState, nowEpochMillis: Long, tzId: String): TodayReport {
    val zone = zoneOf(tzId)
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
    val stillFresh = Inventory.active(state).count {
        it.addedAt >= start && it.addedAt < end && !Statistics.isConsolidated(state, it)
    }
    return TodayReport(
        reviews = reviews,
        introduced = state.newIntroduced[day] ?: 0,
        consolidated = state.consolidatedCrossed[day] ?: 0,
        stillFresh = stillFresh,
        missed = missed,
        expectedRecall = state.config.desiredRetention,
    )
}
