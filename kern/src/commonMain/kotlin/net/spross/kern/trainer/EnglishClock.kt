package net.spross.kern.trainer

import net.spross.kern.trainer.EnglishClockRegisters as Registers

/**
 * English conversational clock times. The canonical reading is the past/to form a
 * speaker uses ("quarter past two", "twenty-five to three") until the minute leaves the
 * five-minute grid, where the digital reading takes over ("two seventeen") — nobody
 * reads a clock aloud as "seventeen minutes past two".
 *
 * Accepted beside it: the digital reading everywhere, the "a quarter"/"minutes" forms,
 * American "after"/"till"/"quarter of", the part of the day, and the 24-hour reading.
 * 12-hour cycle — the prompt carries the 24-hour value.
 */
internal object EnglishClock {
    private val hourWords = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")

    fun task(hours: Int, minutes: Int): ClockReading {
        val readings = readings(hours, minutes)
        return ClockReading(readings.first(), readings, gloss(readings, hours))
    }

    private fun readings(hours: Int, minutes: Int): List<String> {
        val h12 = hours % 12
        val cur = hourWords[h12]
        val next = hourWords[(h12 + 1) % 12]

        if (minutes == 0 && hours == 0) {
            return listOf("midnight", "twelve midnight", "twelve o'clock at night", "twelve o'clock", "twelve") +
                Registers.twentyFourHour(0, 0)
        }
        if (minutes == 0 && hours == 12) {
            return listOf("noon", "midday", "twelve noon", "twelve midday", "twelve o'clock", "twelve") +
                Registers.twentyFourHour(12, 0)
        }

        val conversational = when (minutes) {
            0 -> listOf("$cur o'clock", cur, "$cur o'clock sharp", "$cur o'clock on the dot", "exactly $cur o'clock")
            15 -> listOf("quarter past $cur", "a quarter past $cur", "quarter after $cur", "a quarter after $cur")
            30 -> listOf("half past $cur", "half $cur")
            45 -> listOf(
                "quarter to $next", "a quarter to $next", "quarter till $next", "a quarter till $next",
                "quarter of $next", "a quarter of $next",
            )
            in 1..29 -> listOf("${EnglishNumbers.underHundred(minutes)} past $cur")
            else -> listOf("${EnglishNumbers.underHundred(60 - minutes)} to $next")
        }
        // The quarters also read with their minute count ("fifteen past two").
        val counted = when (minutes) {
            15 -> listOf("${EnglishNumbers.underHundred(minutes)} past $cur")
            45 -> listOf("${EnglishNumbers.underHundred(60 - minutes)} to $next", "fifteen till $next")
            else -> emptyList()
        }
        // On the hour there is no minute to spell, to join, or to read off the face.
        val spelledOut = if (minutes == 0) emptyList() else spelledMinutes(minutes, cur, next)
        val american = american(minutes, cur, next)
        val digital = if (minutes == 0) emptyList() else listOf(digital(cur, minutes))
        val display = if (minutes % 5 == 0) conversational.first() else digital.first()

        val core = EnglishNumbers.spellings(conversational + counted + spelledOut + american + digital)
        val placed = EnglishNumbers.spellings(listOf(conversational.first()) + digital)
            .flatMap { form -> Registers.dayParts(hours).map { "$form $it" } }
        val all = core + placed + Registers.anchors(hours, minutes) + Registers.twentyFourHour(hours, minutes)
        return (listOf(display) + all).distinct()
    }

    /** "seventeen minutes past two", "one minute to twelve" — the count with its noun. */
    private fun spelledMinutes(minutes: Int, cur: String, next: String): List<String> {
        // why: "half past two" is the reading at :30; "thirty past two" is not said.
        val past = minutes <= 30
        val count = if (past) minutes else 60 - minutes
        val noun = if (count == 1) "minute" else "minutes"
        val target = if (past) cur else next
        return listOf("${EnglishNumbers.underHundred(count)} $noun ${if (past) "past" else "to"} $target")
    }

    /**
     * The American joiners. "after" for the first half, "till" for the second — but
     * never a numeric "<n> of <hour>": `of` is one edit from the digital joiner `oh`,
     * so "ten of three" would grade correct for 3:10 as well. Only "quarter of" is
     * safe, and it is above with the other quarter readings.
     */
    private fun american(minutes: Int, cur: String, next: String): List<String> {
        if (minutes == 0 || minutes == 30) return emptyList()
        val past = minutes < 30
        val count = EnglishNumbers.underHundred(if (past) minutes else 60 - minutes)
        val noun = if ((if (past) minutes else 60 - minutes) == 1) "minute" else "minutes"
        val joiner = if (past) "after" else "till"
        val target = if (past) cur else next
        return listOf("$count $joiner $target", "$count $noun $joiner $target")
    }

    /** "two oh five" below ten past, "two thirty-five" above. */
    private fun digital(hourWord: String, minutes: Int): String = when {
        minutes < 10 -> "$hourWord oh ${EnglishNumbers.underHundred(minutes)}"
        else -> "$hourWord ${EnglishNumbers.underHundred(minutes)}"
    }

    /**
     * Two alternatives from the time's own accepted set, plus — where the 24-hour
     * prompt invites the calque — the one thing English does not say. A drill that
     * advertises a reading it then marks wrong is worse than no gloss at all.
     */
    private fun gloss(readings: List<String>, hours: Int): String? {
        val candidates = readings.drop(1)
            .filter { " in the " !in it && " at night" !in it && " hours" !in it }
        val also = ClockGloss.line(readings.first(), candidates, limit = 2, lead = "also: ", separator = " or ")
        val warning = if (hours in 13..23) "never \"${EnglishNumbers.cardinal(hours.toLong())} o'clock\"" else null
        return listOfNotNull(also, warning).joinToString(" — ").ifEmpty { null }
    }
}
