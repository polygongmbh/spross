package net.spross.kern.trainer

/** [Numbers.reversed]'s body: the reading becomes the prompt and the value the answer. */
internal fun reversedTask(task: NumbersTask): NumbersTask {
    val value = slotValue(task)
    val accepted = when (task.kind) {
        // why: the forward prompt showed "12 345", so the separator must grade.
        NumbersReading.Cardinal -> listOf(value, groupDigits(value)).distinct()
        NumbersReading.Year -> listOf(value)
        // why: "18.05" is how German writes a time, and the one separator a number
        // pad without a colon key can type.
        NumbersReading.Clock -> clockDigitForms(value).flatMap { listOf(it, it.replace(':', '.')) }
        // why: a form is written, not just spelled — "3,7" and "3.7" are the same
        // number, "20." and "20" the same rank, so the notation must not cost the Sprosse.
        NumbersReading.Form -> formDigitForms(task.prompt, task.promptDisplay)
        // A fraction has one notation and no separator to get wrong.
        NumbersReading.Fraction -> listOf(value)
        // why: the prompt showed the country's grouping, so its spaces must grade too.
        NumbersReading.Phone -> listOf(value, Numbers.phone(value, task.language).promptDisplay)
    }
    // The reveal shows the readable rendering, which is always one of the accepted ones.
    val reveal = when (task.kind) {
        NumbersReading.Cardinal -> groupDigits(value)
        NumbersReading.Form -> task.promptDisplay
        NumbersReading.Phone -> Numbers.phone(value, task.language).promptDisplay
        else -> value
    }
    return NumbersTask(
        kind = task.kind, language = task.language,
        prompt = task.display, accepted = accepted,
        display = reveal, gloss = task.gloss,
    )
}

/**
 * The bare value a task asks about: a plain drill's whole prompt, and the value
 * embedded in a phrase task's sentence ("Wir haben 347 Teller." → "347",
 * "Ich brauche 1/4 Kilo Mehl." → "1/4").
 */
private fun slotValue(task: NumbersTask): String =
    SLOT_VALUE.find(task.prompt)?.value ?: task.prompt

/** "08:05" and "8:05" — the same pair the phrase slots grade against. */
internal fun clockDigitForms(time: String): List<String> {
    val bare = time.substringBefore(':').toInt().toString() + ":" + time.substringAfter(':')
    return listOf(time, bare).distinct()
}

/** A clock time, a fraction, or a plain run of digits — whichever the sentence carries. */
private val SLOT_VALUE = Regex("""\d+(?:[:/]\d+)?""")
