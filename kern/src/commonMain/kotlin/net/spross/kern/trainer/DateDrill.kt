package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.catalog.DateEntry
import net.spross.kern.catalog.DateNames
import net.spross.kern.catalog.DatePattern
import net.spross.kern.model.Language

/**
 * The dates drill: the weekday names alone, the month names alone, the day-of-month
 * numeral alone — and then the whole spoken date assembled out of them.
 *
 * Registry-by-file like the atlas: a pair has this drill exactly when
 * [net.spross.kern.catalog.Catalog.dateDrillContent] joins its two calendars. Pure and
 * stateless like its siblings — no schedule read, no review booked, every draw on the
 * caller's [Random].
 *
 * Unlike the atlas ladder its rungs do not accumulate: each one asks ONLY what it
 * introduces — three weekday wins must never carry a learner past a rung whose own
 * question they never met — and the top rung is where everything below mixes. A rung the
 * answer language cannot carry is absent, not locked: no `dateWithYear` pattern, no year
 * rung, and the ladder simply tops out a rung short (uk, `docs/date-readings.md`).
 * Reversed, only the bare-name rungs stand — the numeric side of a date is a separator
 * convention, not a language skill — so that ladder is the two names plus their mix.
 *
 * The bare rungs are a symmetric pair join; the assembled rungs are half authored and
 * half generated: names and pattern from the catalog, the day from the answer pack's
 * [TrainerLanguagePack.dateDay], the year from its year reading. Their draws are
 * generated like the slot drill's, so [DrillSolved.SPENT_ATTEMPTS] is what "spent"
 * means there.
 */
object DateDrill {

    /** Three clean wins a rung — the country drill's pacing, for the country drill's reason. */
    const val WINS_TO_ADVANCE = 3

    /** Years an assembled date may draw: both the hundred-counted and the thousand-counted centuries. */
    internal val YEARS: IntRange = 1900..2099

    fun winsToAdvance(fast: Boolean): Int = if (fast) 1 else WINS_TO_ADVANCE

    /** How tall this pair's ladder is: its rungs plus the mixed top one. */
    fun maxLevel(content: DateDrillContent, reverse: Boolean): Int =
        rungs(content, reverse).size + 1

    /** Fast is earned by having EVER stood on this ladder's top rung, like the atlas'. */
    fun fastUnlocked(bestLevel: Int, content: DateDrillContent, reverse: Boolean): Boolean =
        bestLevel >= maxLevel(content, reverse)

    /** The rung ramp, on this pair's own ceiling and rung length ([DrillRamp.step]). */
    fun step(
        content: DateDrillContent,
        reverse: Boolean,
        level: Int,
        winsAtLevel: Int,
        correct: Boolean,
        clean: Boolean,
        fast: Boolean,
    ): DrillRamp.RungStep =
        DrillRamp.step(level, winsAtLevel, correct, clean, maxLevel(content, reverse), winsToAdvance(fast))

    /** What [level] may ask: one kind per rung, everything below on the top one. */
    fun kinds(content: DateDrillContent, level: Int, reverse: Boolean): List<DateTaskKind> {
        val ladder = rungs(content, reverse)
        val rung = level.coerceIn(1, ladder.size + 1)
        return if (rung > ladder.size) ladder else listOf(ladder[rung - 1])
    }

    /**
     * One question from [level]'s pool, never one [solved] already holds ([DrillSolved]).
     * [avoidId] is the previous answer's id, resampled once so a repeat needs two unlucky
     * draws. Null ⇒ the rung is answered out — for an assembled rung, that
     * [DrillSolved.SPENT_ATTEMPTS] draws in a row landed on solved questions.
     */
    fun sample(
        content: DateDrillContent,
        level: Int,
        reverse: Boolean,
        avoidId: String?,
        solved: Set<String>,
        rng: Random,
    ): DateDrillTask? {
        for (kind in kinds(content, level, reverse).shuffled(rng)) {
            val task = when (kind) {
                DateTaskKind.Weekday, DateTaskKind.Month, DateTaskKind.DayOfMonth ->
                    samplePool(pool(content, kind, reverse), avoidId, solved, rng)
                else -> sampleComposed(content, kind, avoidId, solved, rng)
            }
            if (task != null) return task
        }
        return null
    }

    /**
     * The first rung at or above [level] with a question left — a spent rung is climbed
     * past, never repeated, and once the whole ladder is out the task is null, which ends
     * the run on its summary.
     */
    fun draw(
        content: DateDrillContent,
        level: Int,
        reverse: Boolean,
        avoidId: String?,
        solved: Set<String>,
        rng: Random,
    ): DateDrillDraw {
        val top = maxLevel(content, reverse)
        for (rung in level.coerceIn(1, top)..top) {
            val task = sample(content, rung, reverse, avoidId, solved, rng)
            if (task != null) return DateDrillDraw(task, rung)
        }
        return DateDrillDraw(null, level)
    }

    /** The language an answer is owed in — the learned one, or the learner's own reversed. */
    fun answerLanguage(content: DateDrillContent, reverse: Boolean): Language =
        if (reverse) content.source else content.target

    /** The other side of the same pair: the language the prompt is written in. */
    fun promptLanguage(content: DateDrillContent, reverse: Boolean): Language =
        if (reverse) content.target else content.source

    /** The overview table, from the same joined rows the drill grades against. */
    fun reference(content: DateDrillContent): List<DateReferenceGroup> = listOf(
        DateReferenceGroup(DateTaskKind.Weekday, content.weekdays.map(::referenceRow)),
        DateReferenceGroup(DateTaskKind.Month, content.months.map(::referenceRow)),
    )

    /** The enumerable rungs' whole pools; the assembled kinds are drawn, never enumerated. */
    internal fun pool(content: DateDrillContent, kind: DateTaskKind, reverse: Boolean): List<DateDrillTask> =
        when (kind) {
            DateTaskKind.Weekday -> content.weekdays.map { nameTask(kind, it, reverse) }
            DateTaskKind.Month -> content.months.map { nameTask(kind, it, reverse) }
            DateTaskKind.DayOfMonth -> (1..31).map { dayTask(content, it) }
            else -> emptyList()
        }

    internal fun nameTask(kind: DateTaskKind, entry: DateEntry, reverse: Boolean): DateDrillTask {
        val answer = entry.answer(reverse)
        return DateDrillTask(
            kind = kind,
            id = entry.index.toString(),
            promptText = entry.prompt(reverse).text,
            accepted = listOf(answer.text) + answer.synonyms + answer.variants,
            display = answer.text,
        )
    }

    internal fun dayTask(content: DateDrillContent, day: Int): DateDrillTask {
        val readings = Trainer.pack(content.target).dateDay(day)
        return DateDrillTask(
            kind = DateTaskKind.DayOfMonth,
            id = day.toString(),
            promptText = "$day.",
            accepted = readings,
            display = readings.first(),
        )
    }

    internal fun dayMonthTask(content: DateDrillContent, day: Int, monthIndex: Int): DateDrillTask {
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

    internal fun fullDateTask(
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

    internal fun fullDateWithYearTask(
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
    internal fun weekdayIndex(year: Int, month: Int, day: Int): Int {
        val y = if (month < 3) year - 1 else year
        val sundayFirst = (y + y / 4 - y / 100 + y / 400 + SAKAMOTO[month - 1] + day) % 7
        return (sundayFirst + 6) % 7
    }

    private fun rungs(content: DateDrillContent, reverse: Boolean): List<DateTaskKind> =
        if (reverse) {
            listOf(DateTaskKind.Weekday, DateTaskKind.Month)
        } else {
            listOfNotNull(
                DateTaskKind.Weekday,
                DateTaskKind.Month,
                DateTaskKind.DayOfMonth,
                DateTaskKind.DayAndMonth,
                DateTaskKind.FullDate,
                DateTaskKind.FullDateWithYear.takeIf { content.patterns.dateWithYear != null },
            )
        }

    private fun samplePool(
        pool: List<DateDrillTask>,
        avoidId: String?,
        solved: Set<String>,
        rng: Random,
    ): DateDrillTask? {
        val open = pool.filterNot { DrillSolved.key(it) in solved }
        if (open.isEmpty()) return null
        var picked = open[rng.nextInt(open.size)]
        if (picked.id == avoidId) picked = open[rng.nextInt(open.size)]
        return picked
    }

    private fun sampleComposed(
        content: DateDrillContent,
        kind: DateTaskKind,
        avoidId: String?,
        solved: Set<String>,
        rng: Random,
    ): DateDrillTask? {
        val first = composedUnsolved(content, kind, solved, rng) ?: return null
        if (first.id != avoidId) return first
        return composedUnsolved(content, kind, solved, rng) ?: first
    }

    private fun composedUnsolved(
        content: DateDrillContent,
        kind: DateTaskKind,
        solved: Set<String>,
        rng: Random,
    ): DateDrillTask? {
        repeat(DrillSolved.SPENT_ATTEMPTS) {
            val task = compose(content, kind, rng)
            if (DrillSolved.key(task) !in solved) return task
        }
        return null
    }

    private fun compose(content: DateDrillContent, kind: DateTaskKind, rng: Random): DateDrillTask {
        val monthIndex = rng.nextInt(content.months.size)
        return when (kind) {
            DateTaskKind.DayAndMonth ->
                dayMonthTask(content, rng.nextInt(daysIn(monthIndex)) + 1, monthIndex)
            DateTaskKind.FullDate -> fullDateTask(
                content,
                rng.nextInt(content.weekdays.size),
                rng.nextInt(daysIn(monthIndex)) + 1,
                monthIndex,
            )
            else -> {
                val year = YEARS.first + rng.nextInt(YEARS.last - YEARS.first + 1)
                fullDateWithYearTask(content, rng.nextInt(daysIn(monthIndex, year)) + 1, monthIndex, year)
            }
        }
    }

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
     * The numeric format with its year dropped, for the rungs that ask no year. The year
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

    private fun daysIn(monthIndex: Int): Int = DAYS[monthIndex]

    private fun daysIn(monthIndex: Int, year: Int): Int =
        if (monthIndex == 1 && !leap(year)) 28 else DAYS[monthIndex]

    private fun leap(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    /** Days each month can carry — February keeps its 29th, so a yearless draw reaches all 366. */
    private val DAYS = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    private val SAKAMOTO = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)

    private fun referenceRow(entry: DateEntry) = DateReferenceRow(
        source = entry.source.text,
        target = entry.target.text,
        synonyms = entry.target.synonyms,
        abbr = entry.target.abbr,
        dateForm = entry.target.dateForm,
    )

    private fun DateEntry.prompt(reverse: Boolean) = if (reverse) target else source

    private fun DateEntry.answer(reverse: Boolean) = if (reverse) source else target
}
