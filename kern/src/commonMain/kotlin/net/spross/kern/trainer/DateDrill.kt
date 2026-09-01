package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.catalog.DateEntry
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
 * The bare rungs enumerate their pools; the assembled rungs are drawn like the slot
 * drill's values, so [DrillSolved.SPENT_ATTEMPTS] is what "spent" means there. What a
 * drawn question looks like — prompt, accepted set, display — is [DateDrillTasks]'.
 */
object DateDrill {

    /** Three clean wins a rung — the country drill's pacing, for the country drill's reason. */
    const val WINS_TO_ADVANCE = 3

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
                    samplePool(DateDrillTasks.pool(content, kind, reverse), avoidId, solved, rng)
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
            DateTaskKind.DayAndMonth -> DateDrillTasks.dayMonth(
                content,
                rng.nextInt(DateDrillTasks.daysIn(monthIndex)) + 1,
                monthIndex,
            )
            DateTaskKind.FullDate -> DateDrillTasks.fullDate(
                content,
                rng.nextInt(content.weekdays.size),
                rng.nextInt(DateDrillTasks.daysIn(monthIndex)) + 1,
                monthIndex,
            )
            else -> {
                val years = DateDrillTasks.YEARS
                val year = years.first + rng.nextInt(years.last - years.first + 1)
                DateDrillTasks.fullDateWithYear(
                    content,
                    rng.nextInt(DateDrillTasks.daysIn(monthIndex, year)) + 1,
                    monthIndex,
                    year,
                )
            }
        }
    }

    private fun referenceRow(entry: DateEntry) = DateReferenceRow(
        source = entry.source.text,
        target = entry.target.text,
        synonyms = entry.target.synonyms,
        abbr = entry.target.abbr,
        dateForm = entry.target.dateForm,
    )
}
