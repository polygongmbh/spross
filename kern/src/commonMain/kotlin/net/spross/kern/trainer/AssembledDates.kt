package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.DateDrillContent

/**
 * The dates drill's assembled Sprossen — day and month, the full date, the dated line —
 * drawn as values rather than picked out of a list. [DateDrill] owns which kind is asked;
 * [DateDrillTasks] what the drawn date looks like.
 */
internal object AssembledDates {

    /**
     * One assembled date of [kind] the run does not hold, resampled once off [avoid].
     * Null ⇒ [DrillSolved.SPENT_ATTEMPTS] draws in a row landed on solved dates.
     */
    fun sample(
        content: DateDrillContent,
        kind: DateTaskKind,
        reverse: Boolean,
        avoid: String?,
        solved: Set<String>,
        rng: Random,
    ): DateDrillTask? {
        val first = unsolved(content, kind, reverse, solved, rng) ?: return null
        if (DrillSolved.key(first) != avoid) return first
        return unsolved(content, kind, reverse, solved, rng) ?: first
    }

    private fun unsolved(
        content: DateDrillContent,
        kind: DateTaskKind,
        reverse: Boolean,
        solved: Set<String>,
        rng: Random,
    ): DateDrillTask? {
        repeat(DrillSolved.SPENT_ATTEMPTS) {
            val task = assemble(content, kind, reverse, rng)
            if (DrillSolved.key(task) !in solved) return task
        }
        return null
    }

    /**
     * [reverse] reaches only the dated Sprosse, and only to choose which reading its card
     * carries: turned round the answer is the date in digits, so a weekday on the card would
     * be a thing the question shows and then throws away ([DateDrillParsing]).
     */
    private fun assemble(
        content: DateDrillContent,
        kind: DateTaskKind,
        reverse: Boolean,
        rng: Random,
    ): DateDrillTask {
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
                    weekdayFree = reverse,
                )
            }
        }
    }
}
