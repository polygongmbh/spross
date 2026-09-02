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
 * The rungs ACCUMULATE, like the atlas ladder's: a rung asks what it introduces plus
 * everything below it, so the weekdays keep coming once the months arrive and the top
 * rung is simply the last one. What keeps the new question from drowning in the old is
 * the draw order, not a narrower rung ([drawOrder]). A rung the answer language cannot
 * carry is absent, not locked: no `dateWithYear` pattern, no year rung, and the ladder
 * simply tops out a rung short (uk, `docs/date-readings.md`).
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

    /** How tall this pair's ladder is: one rung per kind the pair can ask. */
    fun maxLevel(content: DateDrillContent, reverse: Boolean): Int = rungs(content, reverse).size

    /** Fast is earned by having EVER stood on this ladder's top rung, like the atlas'. */
    fun fastUnlocked(bestLevel: Int, content: DateDrillContent, reverse: Boolean): Boolean =
        bestLevel >= maxLevel(content, reverse)

    /** The rung ramp, on this ladder's rung length ([DrillRamp.step]). */
    fun step(
        content: DateDrillContent,
        reverse: Boolean,
        level: Int,
        winsAtLevel: Int,
        correct: Boolean,
        clean: Boolean,
        fast: Boolean,
    ): DrillRamp.RungStep =
        DrillRamp.step(level, winsAtLevel, correct, clean, winsToAdvance(fast))

    /** What [level] may ask: the kind it introduces, and every kind below it. */
    fun kinds(content: DateDrillContent, level: Int, reverse: Boolean): List<DateTaskKind> {
        val ladder = rungs(content, reverse)
        return ladder.take(level.coerceIn(1, ladder.size))
    }

    /**
     * One question from [level]'s pool, never one [solved] already holds ([DrillSolved]).
     * [avoid] is the previous task's [DrillSolved.key], resampled once so a repeat needs
     * two unlucky draws. Null ⇒ the rung is answered out — for an assembled rung, that
     * [DrillSolved.SPENT_ATTEMPTS] draws in a row landed on solved questions.
     */
    fun sample(
        content: DateDrillContent,
        level: Int,
        reverse: Boolean,
        avoid: String?,
        solved: Set<String>,
        rng: Random,
    ): DateDrillTask? {
        for (kind in drawOrder(kinds(content, level, reverse), rng)) {
            val task = when (kind) {
                DateTaskKind.Weekday, DateTaskKind.Month, DateTaskKind.DayOfMonth ->
                    samplePool(DateDrillTasks.pool(content, kind, reverse), avoid, solved, rng)
                else -> sampleComposed(content, kind, avoid, solved, rng)
            }
            if (task != null) return task
        }
        return null
    }

    /**
     * The first rung at or above [level] with a question left. The rungs nest, so a rung
     * is spent only once everything it carries is answered out, and the one above always
     * has at least as much to offer; once the whole ladder is out the task is null, which
     * ends the run on its summary. Past the top the content stands still and the NUMBER
     * goes on ([DrillRamp.step]): a rung above the ladder draws the top one and keeps its
     * own count.
     */
    fun draw(
        content: DateDrillContent,
        level: Int,
        reverse: Boolean,
        avoid: String?,
        solved: Set<String>,
        rng: Random,
    ): DateDrillDraw {
        val top = maxLevel(content, reverse)
        val standing = maxOf(1, level)
        for (rung in minOf(standing, top)..top) {
            val task = sample(content, rung, reverse, avoid, solved, rng)
            if (task != null) return DateDrillDraw(task, maxOf(standing, rung))
        }
        return DateDrillDraw(null, standing)
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

    /**
     * The order [sample] tries a rung's kinds in. Half the draws lead with the kind the
     * rung INTRODUCES — three weekday wins must never carry a learner past a rung whose
     * own question they never met — and the other half are a fair mix of everything the
     * rung carries, which is what keeps the names alive once the dates are assembled.
     */
    private fun drawOrder(ladder: List<DateTaskKind>, rng: Random): List<DateTaskKind> {
        val shuffled = ladder.shuffled(rng)
        val newest = ladder.last()
        return if (rng.nextBoolean()) listOf(newest) + shuffled.filterNot { it == newest } else shuffled
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
        avoid: String?,
        solved: Set<String>,
        rng: Random,
    ): DateDrillTask? {
        val open = pool.filterNot { DrillSolved.key(it) in solved }
        if (open.isEmpty()) return null
        var picked = open[rng.nextInt(open.size)]
        if (DrillSolved.key(picked) == avoid) picked = open[rng.nextInt(open.size)]
        return picked
    }

    private fun sampleComposed(
        content: DateDrillContent,
        kind: DateTaskKind,
        avoid: String?,
        solved: Set<String>,
        rng: Random,
    ): DateDrillTask? {
        val first = composedUnsolved(content, kind, solved, rng) ?: return null
        if (DrillSolved.key(first) != avoid) return first
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
