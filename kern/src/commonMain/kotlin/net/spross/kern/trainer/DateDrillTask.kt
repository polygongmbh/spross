package net.spross.kern.trainer

import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.catalog.DateEntry
import net.spross.kern.catalog.DateNames
import net.spross.kern.catalog.DatePattern

/** What a dates question asks — the ladder's Sprossen, in ladder order. */
enum class DateTaskKind { Weekday, Month, DayOfMonth, DayAndMonth, FullDate, FullDateWithYear }

/**
 * One dates question: what stands on the card, and every reading the answer language
 * accepts for it. Built whole by [DateDrill] — the bare-name Sprossen from the authored
 * calendar, the assembled Sprossen by filling the answer side's pattern with the drawn parts.
 */
data class DateDrillTask(
    val kind: DateTaskKind,
    /** The drawn identity inside [kind] — indexes and digits, never a rendering. */
    val id: String,
    /** A prompt-side name on the bare Sprossen; the date in the prompt side's digits above them. */
    val promptText: String,
    /** Every reading that grades correct, canonical first. */
    val accepted: List<String>,
    /** The canonical answer, for the reveal. */
    val display: String,
)

/** One draw: the task and the Sprosse it was found on — a null task ends the run. */
data class DateDrillDraw(val task: DateDrillTask?, val level: Int)

/** One calendar name as the reference table shows it — both sides, and the date-only forms. */
data class DateReferenceRow(
    val source: String,
    val target: String,
    /** The target side's other taught lexemes (de `Sonnabend`) — spelling variants stay out. */
    val synonyms: List<String>,
    /** The target side's short form — weekdays only; a reversed run's prompts wear the source's. */
    val abbr: String?,
    /** What the name becomes inside a date, where that differs (uk `березня`). */
    val dateForm: String?,
)

/** The reference table's two halves, weekdays before months. */
data class DateReferenceGroup(val kind: DateTaskKind, val rows: List<DateReferenceRow>)

/**
 * How a drawn question becomes a [DateDrillTask]: the bare names straight off the joined
 * calendar, the assembled Sprossen by filling the answer side's pattern with every reading
 * of every part — the day from [TrainerLanguagePack.dateDay], the year from the pack's
 * year reading. [DateDrill] owns WHAT is drawn; this object owns what it looks like.
 */
internal object DateDrillTasks {

    /** Years an assembled date may draw: both the hundred- and the thousand-counted centuries. */
    val YEARS: IntRange = 1900..2099

    /** The enumerable Sprossen' whole pools; the assembled kinds are drawn, never enumerated. */
    fun pool(content: DateDrillContent, kind: DateTaskKind, reverse: Boolean): List<DateDrillTask> =
        when (kind) {
            DateTaskKind.Weekday -> content.weekdays.map { name(kind, it, reverse) }
            DateTaskKind.Month -> content.months.map { name(kind, it, reverse) }
            DateTaskKind.DayOfMonth -> (1..31).map { day(content, it) }
            else -> emptyList()
        }

    fun name(kind: DateTaskKind, entry: DateEntry, reverse: Boolean): DateDrillTask {
        val answer = entry.answer(reverse)
        return DateDrillTask(
            kind = kind,
            id = entry.index.toString(),
            promptText = entry.prompt(reverse).text,
            accepted = listOf(answer.text) + answer.synonyms + answer.variants,
            display = answer.text,
        )
    }

    fun day(content: DateDrillContent, day: Int): DateDrillTask {
        val readings = Trainer.pack(content.target).dateDay(day)
        return DateDrillTask(
            kind = DateTaskKind.DayOfMonth,
            id = day.toString(),
            promptText = "$day.",
            accepted = readings,
            display = readings.first(),
        )
    }

    fun dayMonth(content: DateDrillContent, day: Int, monthIndex: Int): DateDrillTask {
        val accepted = fill(
            content.patterns.dayMonth,
            listOf(
                "{day}" to Trainer.pack(content.target).dateDay(day),
                "{month}" to dateForms(content.months[monthIndex].target),
            ),
        )
        return DateDrillTask(
            kind = DateTaskKind.DayAndMonth,
            id = "$day.${monthIndex + 1}",
            promptText = numericDayMonth(content.numeric, day, monthIndex + 1),
            accepted = accepted,
            display = accepted.first(),
        )
    }

    fun fullDate(
        content: DateDrillContent,
        weekdayIndex: Int,
        day: Int,
        monthIndex: Int,
    ): DateDrillTask {
        val weekday = content.weekdays[weekdayIndex]
        val accepted = fill(
            content.patterns.date,
            listOf(
                "{weekday}" to dateForms(weekday.target),
                "{day}" to Trainer.pack(content.target).dateDay(day),
                "{month}" to dateForms(content.months[monthIndex].target),
            ),
        )
        return DateDrillTask(
            kind = DateTaskKind.FullDate,
            id = "$weekdayIndex:$day.${monthIndex + 1}",
            promptText = "${abbr(weekday)}, ${numericDayMonth(content.numeric, day, monthIndex + 1)}",
            accepted = accepted,
            display = accepted.first(),
        )
    }

    fun fullDateWithYear(
        content: DateDrillContent,
        day: Int,
        monthIndex: Int,
        year: Int,
    ): DateDrillTask {
        // why: once the year fixes the date the weekday is a fact — computed, never drawn,
        // so the card never states a date that does not exist.
        val weekday = content.weekdays[weekdayIndex(year, monthIndex + 1, day)]
        val pack = Trainer.pack(content.target)
        val reading = pack.year(year.toLong())
        val pattern = requireNotNull(content.patterns.dateWithYear) {
            "no dateWithYear pattern for ${content.target}"
        }
        val accepted = fill(
            pattern,
            listOf(
                "{weekday}" to dateForms(weekday.target),
                "{day}" to pack.dateDay(day),
                "{month}" to dateForms(content.months[monthIndex].target),
                "{year}" to (listOf(reading.display) + reading.accepted).distinct(),
            ),
        )
        val numeric = content.numeric
            .replace("{d}", day.toString())
            .replace("{m}", (monthIndex + 1).toString())
            .replace("{y}", year.toString())
        return DateDrillTask(
            kind = DateTaskKind.FullDateWithYear,
            id = "$day.${monthIndex + 1}.$year",
            promptText = "${abbr(weekday)}, $numeric",
            accepted = accepted,
            display = accepted.first(),
        )
    }

    /** ISO weekday of a date, 0 = Monday — Sakamoto's method, no clock and no calendar API. */
    fun weekdayIndex(year: Int, month: Int, day: Int): Int {
        val y = if (month < 3) year - 1 else year
        val sundayFirst = (y + y / 4 - y / 100 + y / 400 + SAKAMOTO[month - 1] + day) % 7
        return (sundayFirst + 6) % 7
    }

    fun daysIn(monthIndex: Int): Int = DAYS[monthIndex]

    fun daysIn(monthIndex: Int, year: Int): Int =
        if (monthIndex == 1 && !leap(year)) 28 else DAYS[monthIndex]

    /**
     * Every form a name takes inside an assembled date. Where a [DateNames.dateForm] is
     * authored the date declines, and only that form is known to — a declined synonym
     * would be invented. Everywhere else the name stands as it is, so whatever a bare
     * answer accepts an assembled one accepts too (de `Sonnabend, der dritte März`).
     */
    private fun dateForms(name: DateNames): List<String> =
        name.dateForm?.let(::listOf) ?: (listOf(name.text) + name.synonyms + name.variants)

    /** The accepted set: each pattern form crossed with every reading of every part. */
    private fun fill(pattern: DatePattern, slots: List<Pair<String, List<String>>>): List<String> {
        var forms = listOf(pattern.text) + pattern.variants
        for ((marker, options) in slots) {
            forms = forms.flatMap { form -> options.map { form.replace(marker, it) } }
        }
        return forms.distinct()
    }

    /**
     * The numeric format with its year dropped, for the Sprossen that ask no year. The year
     * leaves with the separator that joined it on — except a "." standing before it,
     * which is the ordinal dot de/uk write after every number ("3.3."), not a joiner.
     */
    private fun numericDayMonth(numeric: String, day: Int, month: Int): String {
        val at = numeric.indexOf("{y}")
        val bare = when {
            at < 0 -> numeric
            at == 0 -> numeric.removeRange(0, minOf(numeric.length, 4))
            else -> numeric.removeRange(if (numeric[at - 1] == '.') at else at - 1, at + 3)
        }
        return bare.replace("{d}", day.toString()).replace("{m}", month.toString())
    }

    private fun abbr(weekday: DateEntry): String =
        checkNotNull(weekday.source.abbr) { "weekday without abbr on the prompt side" }

    private fun leap(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    /** Days each month can carry — February keeps its 29th, so a yearless draw reaches all 366. */
    private val DAYS = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    private val SAKAMOTO = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)

    private fun DateEntry.prompt(reverse: Boolean) = if (reverse) target else source

    private fun DateEntry.answer(reverse: Boolean) = if (reverse) source else target
}
