package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.catalog.DateEntry

/**
 * The warm-up Sprosse: the calendar's nineteen names picked off four tiles instead of
 * written out — recognition before production, which is the opening the letters ladder
 * already climbs ([LetterStage.ChoiceEasy]) and the rule the box keeps for a word's first
 * exposure. Nothing above it is tapped: the Sprosse is a landing, not a step the ladder
 * carries on ([DateDrill.kinds]).
 *
 * A tapped tile is submitted as the text it carries, which is the answer's own canonical
 * reading, so it grades Exact through the ordinary path and no surface below learns a
 * second way of being right.
 *
 * The tiles come from ONE group — weekdays against weekdays, months against months.
 * [net.spross.kern.session.MultipleChoice]'s rules are about a word standing beside company
 * of another class or another area; a calendar group is homogeneous already, so the whole
 * of the question is which of seven names is Wednesday.
 */
internal object DateDrillChoices {

    /** Tiles per question: the answer plus three others of its own group. */
    const val COUNT: Int = 4

    private const val WEEKDAY: String = "w"

    private const val MONTH: String = "m"

    /** The whole warm-up pool: every weekday and every month, each asked once. */
    fun pool(content: DateDrillContent, reverse: Boolean): List<DateDrillTask> =
        content.weekdays.map { question(WEEKDAY, it, reverse) } +
            content.months.map { question(MONTH, it, reverse) }

    /**
     * The drawn name with its company. Filled at DRAW time rather than in the pool, so
     * meeting a name again in the next run is not the same question with the same three
     * neighbors standing beside it.
     */
    fun tiles(
        content: DateDrillContent,
        task: DateDrillTask,
        reverse: Boolean,
        rng: Random,
    ): DateDrillTask {
        val group = if (task.id.startsWith(WEEKDAY)) content.weekdays else content.months
        val others = group
            .map { answerText(it, reverse) }
            .filter { it != task.display }
            .shuffled(rng)
            .take(COUNT - 1)
        return task.copy(choices = (others + task.display).shuffled(rng))
    }

    /**
     * The name's own accepted set, borrowed whole from the written Sprosse — a tile question
     * and a typed one are the same question about the same name, and only the id says
     * which group it came from, so the two Sprossen never solve each other's prompts.
     */
    private fun question(tag: String, entry: DateEntry, reverse: Boolean): DateDrillTask =
        DateDrillTasks.name(DateTaskKind.NameChoice, entry, reverse).copy(id = "$tag${entry.index}")

    private fun answerText(entry: DateEntry, reverse: Boolean): String =
        if (reverse) entry.source.text else entry.target.text
}
