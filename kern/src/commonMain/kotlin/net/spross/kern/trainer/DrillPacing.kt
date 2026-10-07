package net.spross.kern.trainer

import net.spross.kern.session.AnswerOutcome

/** Why an endless run stopped to ask whether to go on ([DrillPacing]). */
enum class DrillPauseReason {
    /** The run did something new: a Sprosse the store did not hold, or the record beaten ([DrillPacing]). */
    Improved,

    /** Most of the last few answers missed. */
    Struggling,

    /** A stretch's worth of answers since the run opened or last went on. */
    Count,
    ;

    /** Whether the pause earns the celebration: every one but the pause that suggests a stop. */
    val celebrated: Boolean get() = this != Struggling

    /** The glyph the pause stands under. */
    val emoji: String
        get() = when (this) {
            Improved -> "🎉"
            Struggling -> "☕️"
            Count -> "💪"
        }
}

/** A climb a pause reports: from the Sprosse the run opened on to the higher one it reached. */
data class SprosseClimb(val from: Int, val to: Int)

/**
 * Where an endless run stands against its next natural stop.
 *
 * A drill has no plan to finish, so kern gives it one: after a booked answer the run pauses at
 * the FIRST of three moments —
 * [STRETCH] answers since the run opened or last went on;
 * after [IMPROVED_AFTER], something new since the last pause —
 * a Sprosse the store did not hold, or a standing answer-streak record beaten;
 * after [STRUGGLING_AFTER], [STRUGGLING_MISSES] misses among the last [STRUGGLING_WINDOW] answers.
 * Where two fall on one answer, improving is named over struggling, and either over the count.
 *
 * A Sprosse the store did not hold is one cleared for the first time:
 * for the scrambles and the letters one climbed off that the stored mask lacks,
 * for the atlas and the calendar one answered out that the run's direction's mask lacks,
 * for the slot drill each one climbed off at or above the Sprosse its exercise had been climbed to.
 * The atlas's and the calendar's reached Sprosse is not new —
 * so the calendar's assembled Sprossen, which never answer out, improve only through the record.
 *
 * A pause asks, it does not end: going on ([DrillRunCore.resumed]) keeps the run whole —
 * the prompts asked, the ladder, the answer streak — and starts the next stretch.
 * A run that ran out of questions ends instead, and a timed run ends on its clock, so neither pauses.
 */
data class DrillPacing(
    /** The Sprosse the run opened on; null where it climbs several ladders at once. */
    val openedOn: Int? = null,
    /** The highest Sprosse it has stood on since; null exactly where [openedOn] is. */
    val reached: Int? = null,
    /** The answer-streak record standing when the run opened; 0 where the drill keeps none, or none stood yet. */
    val standingRecord: Int = 0,
    /** How many Sprossen this run cleared that the store did not hold. */
    val newSprossen: Int = 0,
    /** The run's best answer streak beat a record that stood. */
    val newRecord: Boolean = false,
    /** [DrillRunCore.done] when the current stretch began. */
    val stretchFrom: Int = 0,
    /** What the run had gained when the current stretch began — improving asks for more than this. */
    val gainedBefore: Int = 0,
    /** Why the run waits on the learner; null while it runs on. */
    val pause: DrillPauseReason? = null,
) {
    internal val gained: Int get() = newSprossen + if (newRecord) 1 else 0

    /** What the pause reports of the ladder: the climb, only where the run stood higher than it opened. */
    val climbed: SprosseClimb?
        get() {
            val from = openedOn ?: return null
            val to = reached ?: return null
            return SprosseClimb(from, to).takeIf { to > from }
        }

    /**
     * The pacing after an answer [core] has just booked: the figures brought up to date, and the
     * pause due now, if one is. [sprosse] is the Sprosse the run now stands on, null where it climbs
     * several; [newSprossen] how many it has cleared that the store did not hold; [endless] false
     * where the run is over or ends on its clock.
     */
    internal fun after(core: DrillRunCore, sprosse: Int?, newSprossen: Int, endless: Boolean): DrillPacing {
        val moved = copy(
            reached = sprosse?.let { maxOf(reached ?: it, it) } ?: reached,
            newSprossen = newSprossen,
            newRecord = standingRecord > 0 && core.bestAnswerStreak > standingRecord,
        )
        val stretch = core.done - stretchFrom
        val misses = core.outcomes.takeLast(STRUGGLING_WINDOW).count { it == AnswerOutcome.Wrong }
        val due = when {
            !endless -> null
            stretch >= IMPROVED_AFTER && moved.gained > gainedBefore -> DrillPauseReason.Improved
            stretch >= STRUGGLING_AFTER && misses >= STRUGGLING_MISSES -> DrillPauseReason.Struggling
            stretch >= STRETCH -> DrillPauseReason.Count
            else -> null
        }
        return moved.copy(pause = due)
    }

    companion object {
        const val STRETCH: Int = 20
        const val IMPROVED_AFTER: Int = 10
        const val STRUGGLING_AFTER: Int = 6
        const val STRUGGLING_WINDOW: Int = 5
        const val STRUGGLING_MISSES: Int = 3

        /** A run's pacing as it opens on [sprosse] against [standingRecord]. */
        fun opening(sprosse: Int?, standingRecord: Int): DrillPacing =
            DrillPacing(openedOn = sprosse, reached = sprosse, standingRecord = standingRecord)
    }
}
