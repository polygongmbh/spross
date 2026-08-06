package net.spross.kern.trainer

import net.spross.kern.trainer.EnglishClockRegisters as Registers

/**
 * English conversational clock times. The canonical reading is the past/to form a
 * speaker uses ("quarter past two", "twenty-five to three") until the minute leaves the
 * five-minute grid, where the digital reading takes over ("two seventeen") — nobody
 * reads a clock aloud as "seventeen minutes past two".
 *
 * Accepted beside it: the digital reading everywhere, the "a quarter"/"minutes" forms,
 * American "after"/"till"/"quarter of", the part of the day, the meridiem and the
 * 24-hour reading. 12-hour cycle — the prompt carries the 24-hour value.
 */
internal object EnglishClock {
    private val hourWords = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")

    fun task(hours: Int, minutes: Int): ClockReading {
        if (minutes == 0 && hours == 0) return MIDNIGHT
        if (minutes == 0 && hours == 12) return NOON
        val readings = readings(hours, minutes)
        val display = readings.first()
        val gloss = ClockGloss.line(display, named(hours, minutes), limit = 2, lead = "also: ", separator = " or ")
        return ClockReading(display, readings, gloss)
    }

    private fun readings(hours: Int, minutes: Int): List<String> {
        val h12 = hours % 12
        val cur = hourWords[h12]
        val next = hourWords[(h12 + 1) % 12]

        val conversational = when (minutes) {
            0 -> listOf("$cur o'clock", cur)
            15 -> listOf("quarter past $cur", "a quarter past $cur", "quarter after $cur", "a quarter after $cur")
            30 -> listOf("half past $cur", "half $cur")
            45 -> listOf(
                "quarter to $next", "a quarter to $next", "quarter till $next", "a quarter till $next",
                "quarter of $next", "a quarter of $next",
            )
            else -> listOf(counted(minutes, cur, next))
        }
        // The quarters also read with their minute count ("fifteen past two").
        val quarterCounts = when (minutes) {
            15 -> listOf("${EnglishNumbers.underHundred(minutes)} past $cur")
            45 -> listOf("${EnglishNumbers.underHundred(60 - minutes)} to $next", "fifteen till $next")
            else -> emptyList()
        }
        // On the hour there is no minute to spell, to join, or to read off the face.
        val spelledOut = if (minutes == 0) emptyList() else spelledMinutes(minutes, cur, next)
        val american = american(minutes, cur, next)
        val digital = if (minutes == 0) emptyList() else listOf(digital(cur, minutes))
        val display = if (minutes % 5 == 0) conversational.first() else digital.first()

        val core = EnglishNumbers.spellings(conversational + quarterCounts + spelledOut + american + digital)
        val placed = EnglishNumbers.spellings(listOf(conversational.first()) + digital)
            .flatMap { form -> Registers.dayParts(hours).map { "$form $it" } }
        val meridiem = Registers.meridiem(hours, digital.ifEmpty { listOf(cur) })
        val all = core + placed + meridiem +
            Registers.anchors(hours, minutes) + Registers.twentyFourHour(hours, minutes)
        return (listOf(display) + all).distinct()
    }

    /** "seventeen past two", "twenty-five to three" — the count off the hour. */
    private fun counted(minutes: Int, cur: String, next: String): String =
        if (minutes in 1..29) "${EnglishNumbers.underHundred(minutes)} past $cur"
        else "${EnglishNumbers.underHundred(60 - minutes)} to $next"

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
     * The readings the reveal names, in the order it would spend its two lines on them.
     * Built explicitly rather than filtered out of the accepted set, because what is
     * left out is the point: a joiner swap ("quarter till five" for "quarter to five",
     * "ten after two" for "ten past two") is one construction with the preposition
     * changed and teaches nothing a second time.
     *
     * Every one of them is an ACCEPTED reading — a drill that advertises a form it then
     * marks wrong is worse than no gloss at all, and `ClockRevealTests` holds it to that.
     */
    private fun named(hours: Int, minutes: Int): List<String> {
        val h12 = hours % 12
        val cur = hourWords[h12]
        val next = hourWords[(h12 + 1) % 12]
        return listOfNotNull(
            // why: German "Viertel fünf" is 4:15 and English "quarter of five" is 4:45,
            // so a learner carrying the idiom across lands half an hour out. That false
            // friend earns the line even though "till" is accepted and never named.
            "quarter of $next".takeIf { minutes == 45 },
            "${if (minutes == 0) cur else digital(cur, minutes)} ${Registers.meridiemMark(hours)}",
            // Off the five-minute grid the display IS the digital reading, so the count
            // off the hour is the construction it does not show.
            counted(minutes, cur, next).takeIf { minutes % 5 != 0 },
            // why: with a minute on it the 24-hour reading is the digital one in another
            // register — one reading that spells the minute out is all a gloss needs.
            Registers.twentyFourHour(hours, minutes).firstOrNull()?.takeIf { minutes == 0 },
        )
    }

    /**
     * why: at the two named hours the meridiem is accepted but never advertised —
     * "twelve a.m." and "twelve p.m." are the pair native speakers themselves get
     * backwards, so the reveal keeps teaching "midnight" and "noon" here.
     */
    private val MIDNIGHT = ClockReading(
        "midnight",
        listOf("midnight", "twelve midnight", "twelve o'clock at night", "twelve o'clock", "twelve") +
            Registers.meridiem(0, listOf("twelve")) + Registers.twentyFourHour(0, 0),
        "also: twelve o'clock",
    )

    private val NOON = ClockReading(
        "noon",
        listOf("noon", "midday", "twelve noon", "twelve midday", "twelve o'clock", "twelve") +
            Registers.meridiem(12, listOf("twelve")) + Registers.twentyFourHour(12, 0),
        "also: midday or twelve o'clock",
    )
}
