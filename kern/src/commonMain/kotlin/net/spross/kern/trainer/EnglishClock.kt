package net.spross.kern.trainer

/**
 * English conversational clock times. The canonical reading is the
 * past/to form a speaker uses ("quarter past two", "twenty-five to three");
 * the digital reading ("two thirty-five", "two oh five") is accepted beside
 * it, as are the "a quarter"/"after"/"till" variants. 12-hour cycle — the
 * prompt already carries the 24-hour value, so am/pm is never asked for.
 */
internal object EnglishClock {
    private val hourWords = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")

    fun task(hours: Int, minutes: Int): ClockReading {
        val readings = readings(hours, minutes)
        return ClockReading(readings.first(), readings)
    }

    private fun readings(hours: Int, minutes: Int): List<String> {
        val h12 = hours % 12
        val cur = hourWords[h12]
        val next = hourWords[(h12 + 1) % 12]

        if (minutes == 0 && hours == 0) return listOf("midnight", "twelve o'clock", "twelve")
        if (minutes == 0 && hours == 12) return listOf("noon", "midday", "twelve o'clock", "twelve")

        val conversational = when (minutes) {
            0 -> listOf("$cur o'clock", cur)
            15 -> listOf("quarter past $cur", "a quarter past $cur", "quarter after $cur", "a quarter after $cur")
            30 -> listOf("half past $cur", "half $cur")
            45 -> listOf("quarter to $next", "a quarter to $next", "quarter till $next")
            in 1..29 -> listOf("${EnglishNumbers.underHundred(minutes)} past $cur")
            else -> listOf("${EnglishNumbers.underHundred(60 - minutes)} to $next")
        }
        // The quarters also read with their minute count ("fifteen past two").
        val counted = when (minutes) {
            15, 30 -> listOf("${EnglishNumbers.underHundred(minutes)} past $cur")
            45 -> listOf("${EnglishNumbers.underHundred(60 - minutes)} to $next")
            else -> emptyList()
        }
        val digital = if (minutes == 0) emptyList() else listOf(digital(cur, minutes))
        return EnglishNumbers.spellings(conversational + counted + digital)
    }

    /** "two oh five" below ten past, "two thirty-five" above. */
    private fun digital(hourWord: String, minutes: Int): String = when {
        minutes < 10 -> "$hourWord oh ${EnglishNumbers.underHundred(minutes)}"
        else -> "$hourWord ${EnglishNumbers.underHundred(minutes)}"
    }
}
