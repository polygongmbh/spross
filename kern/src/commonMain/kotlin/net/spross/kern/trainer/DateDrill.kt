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
 * The Sprossen ACCUMULATE, like the atlas ladder's: a Sprosse asks what it introduces plus
 * everything below it, so the weekdays keep coming once the months arrive and the top
 * Sprosse is simply the last one. What keeps the new question from drowning in the old is
 * the draw order, not a narrower Sprosse ([drawOrder]). A Sprosse the answer language cannot
 * carry is absent, not locked: no `dateWithYear` pattern, no year Sprosse, and the ladder
 * simply tops out a Sprosse short (uk, `docs/date-readings.md`).
 * Reverse is a DIRECTION, not a shorter ladder: every Sprosse stands both ways round. The
 * bare names swap sides, and the numeric Sprossen turn into a parse — the card carries the
 * reading and the run wants the date written down ([DateDrillParsing]). Which is the easier
 * half of the skill and the half a learner actually spends, so the whole reversed ladder is
 * a comprehension check, answered in digits rather than in the learner's own prose.
 *
 * The ladder OPENS on a tapped Sprosse either way ([DateDrillChoices]): the names are met
 * on four tiles before any of them is written out, which is the letters ladder's opening
 * and the box's rule for a word nobody has produced yet.
 *
 * The bare Sprossen enumerate their pools; the assembled Sprossen are drawn like the slot
 * drill's values, so [DrillSolved.SPENT_ATTEMPTS] is what "spent" means there. What a
 * drawn question looks like — prompt, accepted set, display — is [DateDrillTasks]'.
 */
object DateDrill {

    /** Three clean wins a Sprosse — the country drill's pacing, for the country drill's reason. */
    const val WINS_TO_ADVANCE = 3

    fun winsToAdvance(fast: Boolean): Int = if (fast) 1 else WINS_TO_ADVANCE

    /** How tall this pair's ladder is: one Sprosse per kind the pair can ask. */
    fun maxLevel(content: DateDrillContent, reverse: Boolean): Int = sprossen(content, reverse).size

    /** Fast is earned by having EVER stood on this ladder's top Sprosse, like the atlas'. */
    fun fastUnlocked(bestLevel: Int, content: DateDrillContent, reverse: Boolean): Boolean =
        bestLevel >= maxLevel(content, reverse)

    /** The Sprosse ramp, on this ladder's Sprosse length ([DrillRamp.step]). */
    fun step(
        content: DateDrillContent,
        reverse: Boolean,
        level: Int,
        winsAtLevel: Int,
        correct: Boolean,
        clean: Boolean,
        fast: Boolean,
    ): DrillRamp.SprosseStep =
        DrillRamp.step(level, winsAtLevel, correct, clean, winsToAdvance(fast))

    /**
     * What [level] may ask: the kind it introduces, and every kind below it — except the
     * warm-up, which every Sprosse above it leaves behind.
     *
     * why: [DateTaskKind.NameChoice] is a landing rather than a step. Four tiles carried
     * up among written dates would be a free point, and the Sprosse above would climb on
     * a tap that asked nothing the Sprosse below had not already answered.
     */
    fun kinds(content: DateDrillContent, level: Int, reverse: Boolean): List<DateTaskKind> {
        val ladder = sprossen(content, reverse)
        val carried = ladder.take(level.coerceIn(1, ladder.size))
        return if (carried.size == 1) carried else carried.drop(1)
    }

    /**
     * One question from [level]'s pool, never one [solved] already holds ([DrillSolved]).
     * [avoid] is the previous task's [DrillSolved.key], resampled once so a repeat needs
     * two unlucky draws. Null ⇒ the Sprosse is answered out — for an assembled Sprosse, that
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
                DateTaskKind.NameChoice ->
                    samplePool(DateDrillChoices.pool(content, reverse), avoid, solved, rng)
                        ?.let { DateDrillChoices.tiles(content, it, reverse, rng) }
                DateTaskKind.Weekday, DateTaskKind.Month, DateTaskKind.DayOfMonth ->
                    samplePool(DateDrillTasks.pool(content, kind, reverse), avoid, solved, rng)
                else -> sampleComposed(content, kind, avoid, solved, rng)
            }
            // why: the flip happens HERE and nowhere else — the draw, the avoid key and the
            // solved set all work on the forward task, whose kind and id a parse keeps.
            if (task != null) return if (reverse) DateDrillParsing.parsed(task) else task
        }
        return null
    }

    /**
     * The first Sprosse at or above [level] with a question left ([DrillLadder.climb]). The
     * Sprossen nest, so a Sprosse is spent only once everything it carries is answered out, and
     * the one above always has at least as much to offer.
     */
    fun draw(
        content: DateDrillContent,
        level: Int,
        reverse: Boolean,
        avoid: String?,
        solved: Set<String>,
        rng: Random,
    ): DateDrillDraw {
        val climbed = DrillLadder.climb(level, maxLevel(content, reverse)) { sprosse ->
            sample(content, sprosse, reverse, avoid, solved, rng)
        }
        return DateDrillDraw(climbed.task, climbed.level)
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
     * The order [sample] tries a Sprosse's kinds in. Half the draws lead with the kind the
     * Sprosse INTRODUCES — three weekday wins must never carry a learner past a Sprosse whose
     * own question they never met — and the other half are a fair mix of everything the
     * Sprosse carries, which is what keeps the names alive once the dates are assembled.
     */
    private fun drawOrder(ladder: List<DateTaskKind>, rng: Random): List<DateTaskKind> {
        val shuffled = ladder.shuffled(rng)
        val newest = ladder.last()
        return if (rng.nextBoolean()) listOf(newest) + shuffled.filterNot { it == newest } else shuffled
    }

    /**
     * why: [reverse] is taken and ignored — this ladder is the same height both ways round
     * now that the numeric Sprossen have a reversed direction of their own. The parameter
     * stays because the shared overview asks every drill the same question and the atlas
     * still answers it differently: its flag Sprosse has no way round.
     */
    private fun sprossen(content: DateDrillContent, reverse: Boolean): List<DateTaskKind> =
        listOfNotNull(
            DateTaskKind.NameChoice,
            DateTaskKind.Weekday,
            DateTaskKind.Month,
            DateTaskKind.DayOfMonth,
            DateTaskKind.DayAndMonth,
            DateTaskKind.FullDate,
            DateTaskKind.FullDateWithYear.takeIf { content.patterns.dateWithYear != null },
        )

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
