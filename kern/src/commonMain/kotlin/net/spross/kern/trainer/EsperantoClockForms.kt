package net.spross.kern.trainer

/**
 * The word tables [EsperantoClock] reads its hours, minutes and parts of the day out of.
 *
 * The hour is an ORDINAL with `horo` elided — `la tria` — so the article is part of the
 * reading and no copula is: `je la tria` and `Nun estas la tria` both take it unchanged,
 * which is what lets Esperanto keep the prepositional frames every other pack had to drop.
 */
internal object EsperantoClockForms {

    /** index 0..12, both ends noon/midnight. */
    val hourWords = listOf(
        "dek-dua", "unua", "dua", "tria", "kvara", "kvina", "sesa",
        "sepa", "oka", "naŭa", "deka", "dek-unua", "dek-dua",
    )

    /** The 0–23 hour of the timetable register: `la dek-kvara horo`, `la nula horo`. */
    fun officialHour(hour: Int): String = EsperantoNumbers.ordinal(hour.toLong())

    /** One minute is counted with the singular, everything else with the plural. */
    fun minuteNoun(n: Int): String = if (n == 1) "minuto" else "minutoj"

    /**
     * The parts of the day that fit the hour the reading NAMES, canonical first. They are
     * adverbs, so they simply follow the reading; boundaries overlap where speakers do.
     *
     * [countdown] tells a `antaŭ`/`minus` reading from an additive one: counting DOWN to
     * noon is still morning, so 11:45 is `kvarono antaŭ la dek-dua antaŭtagmeze` and never
     * `posttagmeze`. Noon sharp is named by [EsperantoClock] instead and never reaches here.
     */
    fun dayParts(namedHour: Int, countdown: Boolean): List<String> = when {
        namedHour in 0..3 -> listOf("nokte")
        namedHour == 4 -> listOf("nokte", "matene")
        namedHour in 5..9 -> listOf("matene")
        namedHour in 10..11 -> listOf("antaŭtagmeze", "matene")
        namedHour == 12 && countdown -> listOf("antaŭtagmeze", "matene")
        namedHour in 12..17 -> listOf("posttagmeze")
        namedHour == 18 -> listOf("vespere", "posttagmeze")
        namedHour in 19..21 -> listOf("vespere")
        else -> listOf("nokte", "vespere")
    }
}
