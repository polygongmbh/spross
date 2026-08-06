package net.spross.kern.trainer

/**
 * The registers [EnglishClock] reads beside the conversational past/to forms: the part
 * of the day, the 24-hour reading, and the anchors that count from midnight and noon.
 */
internal object EnglishClockRegisters {

    /**
     * How the hour is placed in the day, canonical first. The small hours are "in the
     * morning" — "two o'clock at night" is not what anyone says — and "at night" starts
     * where the evening runs out.
     */
    fun dayParts(hour: Int): List<String> = when (hour) {
        0 -> listOf("in the morning", "at night")
        in 1..11 -> listOf("in the morning")
        in 12..16 -> listOf("in the afternoon")
        17 -> listOf("in the afternoon", "in the evening")
        in 18..20 -> listOf("in the evening")
        21 -> listOf("in the evening", "at night")
        else -> listOf("at night")
    }

    /**
     * The 24-hour reading: "fourteen thirty", "oh nine hundred hours", "twenty-two oh
     * five". The hour word keeps its hyphen deliberately — spaced, "twenty two eleven"
     * comes within one slip per word of "twenty to eleven", a different time. For the
     * same reason a single-digit minute always takes its "oh", never the bare count.
     */
    fun twentyFourHour(hour: Int, minute: Int): List<String> {
        if (hour == 0) {
            return if (minute == 0) {
                listOf("zero hundred hours", "oh hundred hours", "twenty-four hundred hours")
            } else {
                emptyList()
            }
        }
        val hourWord = if (hour < 10) "oh ${EnglishNumbers.underHundred(hour)}" else EnglishNumbers.cardinal(hour.toLong())
        if (minute == 0) return listOf("$hourWord hundred", "$hourWord hundred hours")
        val joiner = if (minute < 10) "oh " else ""
        return EnglishNumbers.spellings(listOf(EnglishNumbers.underHundred(minute)))
            .map { "$hourWord $joiner$it" }
    }

    /**
     * Counting off midnight and noon by name, which English does for the half hour
     * either side of them: "ten past midnight", "quarter to noon". Accepted only — the
     * ordinary reading is what the reveal should teach.
     */
    fun anchors(hour: Int, minute: Int): List<String> {
        val anchor = when {
            hour == 0 && minute in 1..30 -> listOf("midnight")
            hour == 12 && minute in 1..30 -> listOf("noon", "midday")
            hour == 23 && minute > 30 -> listOf("midnight")
            hour == 11 && minute > 30 -> listOf("noon", "midday")
            else -> return emptyList()
        }
        val past = minute <= 30
        val count = if (past) minute else 60 - minute
        val direction = if (past) "past" else "to"
        val heads = buildList {
            when (count) {
                15 -> {
                    add("quarter")
                    add("a quarter")
                    add("fifteen")
                }
                30 -> add("half")
                else -> add(EnglishNumbers.underHundred(count))
            }
        }
        val noun = if (count == 1) "one minute" else "${EnglishNumbers.underHundred(count)} minutes"
        return EnglishNumbers.spellings(
            anchor.flatMap { name -> (heads + noun).map { "$it $direction $name" } },
        )
    }
}
