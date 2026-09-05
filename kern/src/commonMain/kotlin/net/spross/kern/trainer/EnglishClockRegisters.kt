package net.spross.kern.trainer

/**
 * The registers [EnglishClock] reads beside the conversational past/to forms: the part
 * of the day, the meridiem, the 24-hour reading, and the anchors that count from
 * midnight and noon.
 */
internal object EnglishClockRegisters {

    /**
     * The minute counted off the nearer hour: past the current one up to and including
     * the half hour, to the next one beyond it — the one pivot every English count
     * ("twenty past", "twenty-five to", "ten after", "quarter to noon") shares.
     */
    class MinuteOffset(minute: Int) {
        val past: Boolean = minute <= 30
        val count: Int = if (past) minute else 60 - minute
        val counted: String = EnglishNumbers.underHundred(count)
        val noun: String = if (count == 1) "minute" else "minutes"
        fun target(cur: String, next: String): String = if (past) cur else next
        fun joiner(past: String, to: String): String = if (this.past) past else to
    }

    /** The mark as a style guide writes it — what the reveal names. */
    fun meridiemMark(hour: Int): String = if (hour < 12) "a.m." else "p.m."

    /**
     * a.m./p.m. on the readings that carry a bare number — the digital one and the bare
     * hour — decided by the 24-hour prompt. A learner meets the meridiem constantly, so
     * the drill has to know it; the past/to readings never take it, because "quarter to
     * five p.m." is not English.
     *
     * Both spellings are emitted because both are correct English and only an emitted
     * one grades exact. The comparison pipeline turns "." into a space and deletes only
     * `-'’`, so "four forty-five p.m." is four words against "four forty-five pm"'s
     * three; differing word counts fall back to the whole-form budget, where the two sit
     * one space apart. A list carrying just one of them would therefore book the other,
     * a correct answer, as almost — a typo.
     *
     * `am` and `pm` themselves ARE one substitution apart under the drill's flat
     * one-slip-per-word budget, so each grades correct for the other and the twelve-hour
     * cycle stays open across the meridiem. That is accepted: typing the wrong meridiem
     * is a knowledge error rather than a slip, and a typo does not auto-advance — it
     * holds the card and shows the right form, which is the teaching outcome wanted here.
     */
    fun meridiem(hour: Int, numeric: List<String>): List<String> {
        val marks = listOf(meridiemMark(hour), meridiemMark(hour).replace(".", ""))
        return EnglishNumbers.spellings(numeric).flatMap { form -> marks.map { "$form $it" } }
    }

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
        val offset = MinuteOffset(minute)
        val direction = offset.joiner("past", "to")
        val heads = when (offset.count) {
            15 -> listOf("quarter", "a quarter", "fifteen")
            30 -> listOf("half")
            else -> listOf(offset.counted)
        }
        val noun = "${offset.counted} ${offset.noun}"
        return EnglishNumbers.spellings(
            anchor.flatMap { name -> (heads + noun).map { "$it $direction $name" } },
        )
    }
}
