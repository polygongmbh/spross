package net.spross.kern.trainer

/**
 * The hour words, the parts of the day and the 24-hour register [FrenchClock] builds on.
 *
 * French names the hour as a counted noun — `une heure` singular, `deux heures` plural —
 * and noon and midnight replace it outright: `midi` and `minuit` never take `heures`, and
 * being masculine they take `et demi` where an hour takes `et demie`.
 */
internal object FrenchClockForms {

    /** The 12-hour word a reading names, `midi`/`minuit` at twelve. */
    fun hourWord(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        if (h == 0) return "minuit"
        if (h == 12) return "midi"
        val count = (h % 12).toLong()
        return FrenchNumbers.feminine(count) + if (count == 1L) " heure" else " heures"
    }

    /** `midi et demi` against `deux heures et demie` — the head decides the agreement. */
    fun half(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        return hourWord(h) + if (h % 12 == 0) " et demi" else " et demie"
    }

    /**
     * Parts of the day that fit the hour a reading NAMES, canonical first — so 19:45 reads
     * `huit heures moins le quart du soir` while its own count-up reading stays at seven.
     *
     * Noon and midnight are their own half of the day, so a part after them would say the
     * same thing twice. Small hours take `du matin` and `de la nuit` both, and six in the
     * evening is where `du soir` and `de l'après-midi` overlap, as speakers do; every
     * overlap stays inside one half of the cycle, which is what keeps a named part from
     * answering the hour twelve away.
     */
    fun dayParts(namedHour: Int): List<String> = when (((namedHour % 24) + 24) % 24) {
        0, 12 -> emptyList()
        in 1..4 -> listOf("du matin", "de la nuit")
        in 5..11 -> listOf("du matin")
        in 13..17 -> listOf("de l'après-midi")
        18 -> listOf("du soir", "de l'après-midi")
        23 -> listOf("du soir", "de la nuit")
        else -> listOf("du soir")
    }

    /**
     * The timetable hour, 0–23: `zéro heure`, `une heure`, `quatorze heures`,
     * `vingt et une heures` — `heure` is feminine, so the numeral agrees with it.
     */
    fun officialHour(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        return FrenchNumbers.feminine(h.toLong()) + if (h <= 1) " heure" else " heures"
    }
}
