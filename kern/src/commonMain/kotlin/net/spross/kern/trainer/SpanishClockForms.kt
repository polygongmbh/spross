package net.spross.kern.trainer

/**
 * The parts of the day, the copula and the timetable register [SpanishClock] builds on.
 *
 * Spanish tells the hour as a plural noun phrase carrying its own article, so the copula
 * agrees with the hour a reading NAMES — `es la una`, `son las dos` — and so does the
 * part of the day: 19:45 is read "son las ocho menos cuarto", and eight in the evening
 * is `de la noche` even though the clock still says seven.
 */
internal object SpanishClockForms {

    /** index 0..12, both ends noon/midnight. */
    val hourWords = listOf(
        "doce", "una", "dos", "tres", "cuatro", "cinco", "seis",
        "siete", "ocho", "nueve", "diez", "once", "doce",
    )

    /** "es la una" against "son las dos" — singular only for one. */
    fun copula(hourWord: String): String = if (hourWord == "una") "es la" else "son las"

    /** The article on its own, for the elliptical reading "las dos y media". */
    fun article(hourWord: String): String = if (hourWord == "una") "la" else "las"

    /**
     * Parts of the day that fit the hour the reading names, canonical first — and it is
     * the named hour that decides, so 00:45 reads "es la una menos cuarto de la
     * madrugada" while its own additive reading stays "de la noche".
     *
     * [countdown] tells a `menos`/`para` reading from an additive one: counting DOWN to
     * noon or to one is still morning and afternoon respectively, so "son las doce menos
     * cuarto del día" and "es la una menos cuarto del mediodía" are not offered.
     * Noon sharp is not `de la tarde`; noon with minutes on it ("son las doce y media de
     * la tarde") is ordinary.
     */
    fun dayParts(namedHour: Int, minutes: Int, countdown: Boolean): List<String> = when {
        namedHour == 0 && minutes == 0 -> listOf("de la noche")
        namedHour == 0 -> listOf("de la noche", "de la madrugada")
        namedHour in 1..5 -> listOf("de la madrugada", "de la mañana")
        namedHour == 6 -> listOf("de la mañana", "de la madrugada")
        namedHour in 7..11 -> listOf("de la mañana")
        namedHour == 12 && countdown -> listOf("de la mañana")
        namedHour == 12 && minutes == 0 -> listOf("del mediodía", "del día", "de la mañana")
        namedHour == 12 -> listOf("del mediodía", "del día", "de la mañana", "de la tarde")
        namedHour == 13 && countdown -> listOf("de la tarde")
        namedHour == 13 -> listOf("de la tarde", "del mediodía")
        namedHour in 14..18 -> listOf("de la tarde")
        namedHour == 19 -> listOf("de la tarde", "de la noche")
        namedHour == 20 -> listOf("de la noche", "de la tarde")
        else -> listOf("de la noche")
    }

    /** The 0–23 hour as a feminine count: "las trece", "es la una", "las veintiuna". */
    fun officialHour(hour: Int): String = when (hour) {
        1 -> "una"
        21 -> "veintiuna"
        else -> SpanishNumbers.cardinal(hour.toLong())
    }

    /**
     * The count as it reads before "minutos" — apocopated wherever it apocopates
     * ("veintiún minutos", "treinta y un minutos"; "veintiuno minutos" is not Spanish).
     * Empty at one: "un minuto" is a reading of its own, never a count plus a plural.
     */
    fun beforeMinutos(n: Int): List<String> {
        if (n == 1) return emptyList()
        val forms = SpanishNumbers.variants(n.toLong())
        return if (forms.size > 1) listOf(forms[1]) else forms.take(1)
    }
}
