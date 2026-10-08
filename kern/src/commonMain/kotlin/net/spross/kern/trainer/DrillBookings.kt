package net.spross.kern.trainer

import net.spross.kern.model.Language

/**
 * Everything a closed run leaves behind, keyed where each store files it — the platforms write it and decide nothing.
 * Each close builds its own ([CountryDrillClose.bookings] and its siblings), so which stores a drill writes is said once.
 *
 * The stores keep the higher of a figure and what stands ([sprossen], [answers]) and OR a mask in ([cleared]);
 * [records] holds only a record the run beat.
 * A run never answered stamps no [lastRun] and books no [dayAnswers], though it may still have stood on a Sprosse.
 */
data class DrillBookings(
    /** Progress key ([NumbersMode.PROGRESS_PREFIX] + it) → the Sprosse reached. */
    val sprossen: Map<String, Int>,
    /** Cleared key ([NumbersMode.CLEARED_PREFIX] + it) → the Sprossen to add to its mask. */
    val cleared: Map<String, Set<Int>>,
    /** Record key ([NumbersMode.RECORD_PREFIX] + it) → the figure that beat it. */
    val records: Map<String, Int>,
    /** Answers key ([NumbersMode.ANSWERS_PREFIX] + it) → the answers this run took. */
    val answers: Map<String, Int>,
    /** The key [DrillSuggestion] reads the last run under, to stamp with now. */
    val lastRun: String?,
    /** The answers today's streak counts (`BoxEngine.bookDrillAnswers`). */
    val dayAnswers: Int,
) {
    internal companion object {
        fun of(
            drill: Drill,
            target: Language,
            summary: DrillRunSummary?,
            sprossen: Map<String, Int> = emptyMap(),
            cleared: Map<String, Set<Int>> = emptyMap(),
            records: Map<String, Int> = emptyMap(),
            answers: Map<String, Int> = emptyMap(),
        ) = DrillBookings(
            sprossen = sprossen,
            cleared = cleared,
            records = records,
            answers = answers,
            lastRun = summary?.let { DrillSuggestion.lastRunKey(drill, target) },
            dayAnswers = summary?.done ?: 0,
        )

        /** A typed drill's: its pair's Sprosse and mask, and its record and longest run once answered. */
        fun typed(
            drill: Drill,
            key: String,
            target: Language,
            reverse: Boolean,
            summary: DrillRunSummary?,
            bestSprosse: Int,
            cleared: Set<Int>,
        ) = of(
            drill, target, summary,
            sprossen = mapOf(key to bestSprosse),
            cleared = mapOf(NumbersMode.clearedKey(key, reverse) to cleared),
            records = summary?.takeIf { it.newRecord }?.let { mapOf(key to it.recordFigure) } ?: emptyMap(),
            answers = summary?.let { mapOf(key to it.done) } ?: emptyMap(),
        )

        /** A drill that files its mask alone. */
        fun masked(drill: Drill, key: String, target: Language, summary: DrillRunSummary?, cleared: Set<Int>) =
            of(drill, target, summary, cleared = mapOf(key to cleared))
    }
}
