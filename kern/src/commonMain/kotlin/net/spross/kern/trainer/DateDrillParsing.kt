package net.spross.kern.trainer

/**
 * The reversed direction of the numeric Sprossen — PARSING, where forward is production.
 *
 * Forward the card carries `Sa, 3.6.` and the run wants it read out. Turned round it carries
 * the reading and wants the date written down: hearing a date and getting it into a calendar
 * is the half of the skill a learner actually spends, and it is the easier half, which is
 * what makes it a way IN rather than a variation.
 *
 * That is also what the answer being DIGITS buys. Every other reversed drill owes prose in
 * the learner's own language, which grades their spelling of a language they already have;
 * a date parsed into digits is language-neutral, so the reversed run is a comprehension
 * check the whole way up instead of a typing exercise.
 *
 * Nothing new is drawn: a parse is the forward task with its two sides swapped, so the
 * calendar, the patterns and the draw are the ones already there. What is added is the
 * leniency a written date needs — the zero a learner may or may not pad with, and the
 * trailing dot German writes and drops.
 *
 * The weekday the card prints is DROPPED rather than made optional. `Sa,` is the source
 * language's own abbreviation, the prompt already named that day in the language being
 * learned, and no number pad types it: copying it back would be chrome standing in for a
 * skill.
 *
 * Two kinds print one, and dropping the Sprosse only settles the first of them: the full
 * date has no reversed direction at all ([DateDrill]), since without its weekday it asks
 * the day and month over again — but the DATED one keeps its Sprosse, because the year is
 * worth parsing, and it prefixes a weekday just the same. So the strip is what that top
 * Sprosse rests on, not a second opinion about the one below it.
 *
 * A digit slip is never forgiven: `3.7.` for `3.6.` is another date, and
 * [net.spross.kern.session.AnswerNormalizer] grades a digit-bearing word exact-only, so the
 * typo budget cannot bridge one date into another.
 */
internal object DateDrillParsing {

    /**
     * The kinds that turn round into a parse. The bare names are not among them — they swap
     * sides inside their own pool, where both sides are words.
     */
    private val PARSED = setOf(
        DateTaskKind.DayOfMonth,
        DateTaskKind.DayAndMonth,
        DateTaskKind.FullDateWithYear,
    )

    /** [task] the other way round, or unchanged where its kind has no other way round. */
    fun parsed(task: DateDrillTask): DateDrillTask {
        if (task.kind !in PARSED) return task
        val date = task.promptText.substringAfter(", ", task.promptText)
        return task.copy(
            promptText = task.display,
            accepted = forms(date),
            display = date,
            digits = true,
        )
    }

    /**
     * Every way the drawn date may be written: as the card would print it, zero-padded, and
     * without the ordinal dot that trails a German date. Canonical first — it is what the
     * reveal teaches.
     */
    private fun forms(canonical: String): List<String> =
        listOf(canonical, padded(canonical))
            .flatMap { form -> listOf(form) + listOfNotNull(form.dropLast(1).takeIf { form.endsWith(".") }) }
            .distinct()

    /**
     * The same date with every one-digit group written as two. Hand-walked rather than a
     * regex: a four-digit year must stay four digits, and "pad the groups of length one" is
     * the whole rule.
     */
    private fun padded(form: String): String = buildString {
        var at = 0
        while (at < form.length) {
            if (!form[at].isDigit()) {
                append(form[at])
                at++
                continue
            }
            var end = at
            while (end < form.length && form[end].isDigit()) end++
            val group = form.substring(at, end)
            append(if (group.length == 1) "0$group" else group)
            at = end
        }
    }
}
