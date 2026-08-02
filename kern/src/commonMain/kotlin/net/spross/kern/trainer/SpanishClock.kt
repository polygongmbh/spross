package net.spross.kern.trainer

/**
 * Spanish conversational clock times. The hour is feminine and carries the
 * article, so the copula agrees with it: "es la una", "son las dos". Past the
 * half hour the reading counts down to the coming hour ("son las tres menos
 * cuarto"), which is why the copula is derived from each reading's own hour
 * word. The bare form without the copula ("dos y media") is accepted too.
 * 12-hour cycle; the prompt carries the 24-hour value, so the daypart
 * ("de la tarde") is never asked for.
 */
internal object SpanishClock {
    private val hourWords = listOf("doce", "una", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once", "doce")

    fun task(hours: Int, minutes: Int): ClockReading {
        val readings = readings(hours, minutes)
        return ClockReading(readings.first(), readings)
    }

    private fun readings(hours: Int, minutes: Int): List<String> {
        val h12 = hours % 12
        val cur = hourWords[h12]
        val next = hourWords[(h12 + 1) % 12]
        val minuteWord = SpanishNumbers.underHundred(minutes)

        val cores = when (minutes) {
            0 -> listOf(cur, "$cur en punto")
            15 -> listOf("$cur y cuarto", "$cur y quince")
            30 -> listOf("$cur y media", "$cur y treinta")
            45 -> listOf("$next menos cuarto", "$cur y cuarenta y cinco", "$next menos quince")
            in 1..29 -> listOf("$cur y $minuteWord")
            else -> listOf("$next menos ${SpanishNumbers.underHundred(60 - minutes)}", "$cur y $minuteWord")
        }
        val named = when {
            hours == 0 && minutes == 0 -> listOf("es medianoche", "medianoche")
            hours == 12 && minutes == 0 -> listOf("es mediodía", "mediodía")
            else -> emptyList()
        }
        return (cores.map(::withCopula) + named + cores).distinct()
    }

    /** "es la una y cuarto" / "son las dos y cuarto" — singular only for one. */
    private fun withCopula(core: String): String =
        (if (core.substringBefore(' ') == "una") "es la" else "son las") + " " + core
}
