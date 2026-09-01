package net.spross.kern.trainer

/** What a dates question asks — the ladder's rungs, in ladder order. */
enum class DateTaskKind { Weekday, Month, DayOfMonth, DayAndMonth, FullDate, FullDateWithYear }

/**
 * One dates question: what stands on the card, and every reading the answer language
 * accepts for it. Built whole by [DateDrill] — the bare-name rungs from the authored
 * calendar, the assembled rungs by filling the answer side's pattern with the drawn parts.
 */
data class DateDrillTask(
    val kind: DateTaskKind,
    /** The drawn identity inside [kind] — indexes and digits, never a rendering. */
    val id: String,
    /** A prompt-side name on the bare rungs; the date in the prompt side's digits above them. */
    val promptText: String,
    /** Every reading that grades correct, canonical first. */
    val accepted: List<String>,
    /** The canonical answer, for the reveal. */
    val display: String,
)

/** One draw: the task and the rung it was found on — a null task ends the run. */
data class DateDrillDraw(val task: DateDrillTask?, val level: Int)

/** One calendar name as the reference table shows it — both sides, and the date-only forms. */
data class DateReferenceRow(
    val source: String,
    val target: String,
    /** The answer side's other taught lexemes (de `Sonnabend`) — spelling variants stay out. */
    val synonyms: List<String>,
    /** The answer side's short form — weekdays only. */
    val abbr: String?,
    /** What the name becomes inside a date, where that differs (uk `березня`). */
    val dateForm: String?,
)

/** The reference table's two halves, weekdays before months. */
data class DateReferenceGroup(val kind: DateTaskKind, val rows: List<DateReferenceRow>)
