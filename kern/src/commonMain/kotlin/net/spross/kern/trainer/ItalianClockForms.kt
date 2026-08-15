package net.spross.kern.trainer

/**
 * The hour words, the copula and the parts of the day [ItalianClock] builds on.
 *
 * Italian tells the hour as a plural noun phrase whose head (ora/ore) is elided, so the
 * copula and the article agree with the hour a reading NAMES — `è l'una` against
 * `sono le due` — and the article is written onto the hour word at one, where it elides.
 */
internal object ItalianClockForms {

    /** index 0..12, both ends noon/midnight. */
    val hourWords = listOf(
        "dodici", "una", "due", "tre", "quattro", "cinque", "sei",
        "sette", "otto", "nove", "dieci", "undici", "dodici",
    )

    /** "è l'una" against "sono le due" — singular only for one, and l' takes no space. */
    fun copula(hourWord: String): String = if (hourWord == "una") "è l'" else "sono le "

    /** The article on its own, for the elliptical reading "le due e mezza". */
    fun article(hourWord: String): String = if (hourWord == "una") "l'" else "le "

    /** `a` + article, the form the countdown's `manca … alle tre` needs. */
    fun toTheHour(hourWord: String): String = if (hourWord == "una") "all'una" else "alle $hourWord"

    /**
     * The parts of the day that fit the hour the reading NAMES, canonical first.
     * Twelve gets none, and that empty list is Italian's own answer rather than a gap.
     */
    fun dayParts(namedHour: Int): List<String> = when (namedHour) {
        in 0..4 -> listOf("di notte")
        in 5..11 -> listOf("di mattina", "del mattino")
        12 -> emptyList()
        in 13..17 -> listOf("di pomeriggio")
        18 -> listOf("di sera", "di pomeriggio")
        in 19..21 -> listOf("di sera")
        else -> listOf("di sera", "di notte")
    }

    /**
     * The 0–23 register's hour word: the plain cardinal, since Italian counts the hours
     * with the same numeral the timetable prints ("le quattordici", "le ventitré").
     */
    fun officialHour(hour: Int): String = ItalianNumbers.cardinal(hour.toLong())

    /**
     * The count as it reads before `minuti`, apocopated first: a numeral ending in -uno
     * drops that vowel in front of the noun it counts ("ventun minuti"), and the whole
     * form is current beside it. Never called at one, where `un minuto` is a reading of
     * its own rather than a count plus a plural.
     */
    fun beforeMinuti(n: Int): List<String> {
        val plain = ItalianNumbers.cardinal(n.toLong())
        return if (plain.endsWith("uno")) listOf(plain.dropLast(1), plain) else listOf(plain)
    }
}
