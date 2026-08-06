package net.spross.kern.trainer

import net.spross.kern.trainer.SpanishClockForms as Forms

/**
 * Spanish conversational clock times.
 *
 * Three families read the same time. The additive one counts up from the hour on the
 * clock ("son las dos y veinte"), the countdown one down from the coming hour ("son las
 * tres menos veinte") and, across America, from the coming hour with `para` ("veinte
 * para las tres", "faltan veinte minutos para las tres"). Each brings its own copula,
 * which agrees with the hour it names — "es la una", "son las dos" — and the part of the
 * day follows that same named hour.
 *
 * The article-only reading ("las dos y media") and the timetable register ("son las
 * catorce treinta") are accepted beside them; naming the part of the day is optional
 * everywhere. 12-hour cycle — the prompt carries the 24-hour value.
 */
internal object SpanishClock {

    /**
     * One reading without its article. [namedHour] is the hour it names, null where a
     * part of the day would be redundant (the timetable register names 0–23 already).
     */
    private data class Core(
        val text: String,
        val namedHour: Int?,
        val countdown: Boolean = false,
        /** Whether the part of the day rides along — only the conversational readings. */
        val period: Boolean = true,
    )

    fun task(hours: Int, minutes: Int): ClockReading {
        if (minutes == 0 && hours == 0) return MIDNIGHT
        if (minutes == 0 && hours == 12) return NOON
        val readings = mutableListOf<String>()
        for (core in cores(hours, minutes)) {
            val head = core.text.substringBefore(' ')
            val parts = core.namedHour
                ?.takeIf { core.period }
                ?.let { Forms.dayParts(it, minutes, core.countdown) }
                .orEmpty()
            for (dressed in listOf("${Forms.copula(head)} ${core.text}", "${Forms.article(head)} ${core.text}")) {
                for (part in parts) readings += "$dressed $part"
                readings += dressed
            }
            readings += core.text
        }
        readings += bare(hours, minutes)
        val accepted = readings.distinct()
        return ClockReading(accepted.first(), accepted, gloss(accepted))
    }

    private fun cores(h: Int, m: Int): List<Core> {
        val cur = Forms.hourWords[h % 12]
        val next = Forms.hourWords[(h % 12 + 1) % 12]
        val nextHour = (h + 1) % 24
        val count = SpanishNumbers.underHundred(m)
        val rest = 60 - m
        val restCount = SpanishNumbers.underHundred(rest)
        val forms = mutableListOf<Core>()

        // Additive: counting up from the hour on the clock.
        when (m) {
            0 -> {
                forms += Core(cur, h)
                forms += Core("$cur en punto", h)
            }
            // why: Spanish counts one minute with the noun, never with a bare "uno".
            1 -> {
                forms += Core("$cur y un minuto", h)
                forms += Core("$cur y uno", h)
            }
            15 -> {
                forms += Core("$cur y cuarto", h)
                forms += Core("$cur y quince", h)
            }
            30 -> {
                forms += Core("$cur y media", h)
                forms += Core("$cur y treinta", h)
            }
            else -> forms += Core("$cur y $count", h)
        }
        // Spelling the noun out, and reading the face without the "y", are the same
        // time said plainly — the part of the day rides on the idiomatic readings.
        if (m > 0) {
            for (spelled in Forms.beforeMinutos(m)) {
                forms += Core("$cur y $spelled minutos", h, period = false)
            }
            forms += Core("$cur $count", h, period = false)
        }

        // Countdown: the coming hour, less what is left of this one.
        if (m > 30) {
            when {
                m == 45 -> {
                    forms += Core("$next menos cuarto", nextHour, countdown = true)
                    forms += Core("$next menos quince", nextHour, countdown = true)
                }
                rest == 1 -> {
                    forms += Core("$next menos un minuto", nextHour, countdown = true)
                    forms += Core("$next menos uno", nextHour, countdown = true)
                }
                else -> forms += Core("$next menos $restCount", nextHour, countdown = true)
            }
            for (spelled in Forms.beforeMinutos(rest)) {
                forms += Core("$next menos $spelled minutos", nextHour, countdown = true, period = false)
            }
        }
        return forms.leadWith(displayCore(h, m, cur, next, count, restCount)) { it.text }
    }

    /**
     * Readings that carry no article of their own: the American `para` family and the
     * timetable register. Both are complete statements already, so neither takes the
     * copula the hour-word cores are dressed in.
     */
    private fun bare(h: Int, m: Int): List<String> {
        val out = mutableListOf<String>()
        if (m > 30) out += paraFamily(h, m)
        if (m == 0) {
            // "en punto" reads on either side of the part of the day.
            val cur = Forms.hourWords[h % 12]
            for (part in Forms.dayParts(h, 0, countdown = false)) {
                out += "${Forms.copula(cur)} $cur $part en punto"
            }
        }
        out += official(h, m)
        return out
    }

    /** "veinte para las tres", "faltan veinte minutos para las tres" — America. */
    private fun paraFamily(h: Int, m: Int): List<String> {
        val next = Forms.hourWords[(h % 12 + 1) % 12]
        val target = "${Forms.article(next)} $next"
        val rest = 60 - m
        val count = SpanishNumbers.underHundred(rest)
        val out = mutableListOf<String>()
        when (rest) {
            // Singular verb, and the noun is not droppable at a count of one.
            1 -> {
                out += "un minuto para $target"
                out += "falta un minuto para $target"
            }
            15 -> {
                out += "cuarto para $target"
                out += "un cuarto para $target"
                out += "falta un cuarto para $target"
                out += "quince minutos para $target"
                out += "quince para $target"
            }
            // why: an apocopating count keeps the noun — "veintiún para" is not Spanish.
            21 -> out += "veintiún minutos para $target"
            else -> {
                out += "$count para $target"
                out += "$count minutos para $target"
                out += "son $count para $target"
                out += "faltan $count para $target"
                out += "faltan $count minutos para $target"
            }
        }
        // The part of the day rides on the shortest form only; five copies of it
        // teach nothing the first does not.
        val parts = Forms.dayParts((h + 1) % 24, m, countdown = true)
        return listOf(out.first()) + parts.map { "${out.first()} $it" } + out.drop(1)
    }

    /** "son las catorce treinta", "son las catorce horas treinta minutos". */
    private fun official(h: Int, m: Int): List<String> {
        val hourWord = Forms.officialHour(h)
        val copula = if (h == 1) "es la" else "son las"
        val article = if (h == 1) "la" else "las"
        if (m == 0) {
            // At one o'clock the register says nothing the 12-hour reading has not.
            if (h == 1) return emptyList()
            return listOf("$copula $hourWord horas", "$copula $hourWord", "$article $hourWord horas")
        }
        val count = SpanishNumbers.underHundred(m)
        val spelled = if (m == 1) "un minuto" else "${Forms.beforeMinutos(m).first()} minutos"
        return listOf(
            "$copula $hourWord $count",
            "$article $hourWord $count",
            "$copula $hourWord horas $spelled",
        )
    }

    /**
     * The reading the reveal teaches. Round steps take the countdown a speaker reaches
     * for; a minute off that grid is counted up instead — "son las dos y treinta y
     * siete" is what 14:37 is called, not "son las tres menos veintitrés".
     */
    private fun displayCore(h: Int, m: Int, cur: String, next: String, count: String, restCount: String): String = when {
        m == 0 -> cur
        m == 1 -> "$cur y un minuto"
        m == 15 -> "$cur y cuarto"
        m == 30 -> "$cur y media"
        m == 45 -> "$next menos cuarto"
        m == 59 -> "$next menos un minuto"
        m > 30 && (m % 5 == 0 || m >= 56) -> "$next menos $restCount"
        else -> "$cur y $count"
    }

    /**
     * One alternative per family — the countdown, the American `para`, the additive
     * form and the timetable register — picked out of the accepted set itself so the
     * reveal can never name a reading the drill would then mark wrong, and filtered by
     * [ClockGloss] so it never spends a line on the same reading said shorter.
     */
    private fun gloss(accepted: List<String>): String? {
        val display = accepted.first()
        // why: the gloss teaches the other CONSTRUCTION, so it names readings stripped
        // of the part of the day — repeating that would spend all three lines on it.
        val plain = accepted.filter { " de la " !in it && " del " !in it && " horas " !in it }
        val copular = plain.filter { it.startsWith("son las ") || it.startsWith("es la ") }
        // Every copular reading is a candidate; the filter below is what decides which
        // of them are genuinely other ways of saying it.
        val candidates = listOfNotNull(
            copular.firstOrNull { " menos " in it },
            plain.firstOrNull { " para " in it },
        ) + copular
        return ClockGloss.line(display, candidates, limit = 3, lead = "auch: ", separator = " · ")
    }

    private val MIDNIGHT = ClockReading(
        "son las doce de la noche",
        listOf(
            "son las doce de la noche", "es medianoche", "es la medianoche", "medianoche",
            "son las doce en punto de la noche", "son las doce de la noche en punto",
            "las doce de la noche", "son las doce", "son las doce en punto",
            "las doce", "doce", "doce en punto", "son las cero horas", "las cero horas",
        ),
        "auch: es medianoche · las cero horas",
    )

    private val NOON = ClockReading(
        "son las doce del mediodía",
        listOf(
            "son las doce del mediodía", "son las doce del día", "son las doce de la mañana",
            "es mediodía", "es el mediodía", "es mediodía en punto", "mediodía",
            "las doce del mediodía", "las doce del día", "son las doce", "son las doce en punto",
            "las doce", "doce", "doce en punto", "son las doce horas",
        ),
        "auch: son las doce del día · es mediodía",
    )
}
