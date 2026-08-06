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

    /** The steps a speaker names with a fraction word instead of a count. */
    private val ROUND_STEPS = setOf(0, 15, 30, 45)

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
        return ClockReading(accepted.first(), accepted, gloss(accepted, hours, minutes))
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
            0 -> forms += Core(cur, h)
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
        // why: a single-digit minute is spoken with its zero — a board reads 16:05 as
        // "las dieciséis cero cinco". The bare count is said too and stays accepted, but
        // the zero-bearing form leads, so it is the one the reveal teaches.
        val counts = if (m < 10) listOf("cero $count", count) else listOf(count)
        return counts.map { "$copula $hourWord $it" } +
            counts.map { "$article $hourWord $it" } +
            "$copula $hourWord horas $spelled"
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
     * One alternative per family — the countdown, the American `para`, the spelled-out
     * `minutos` register and one reading that counts the minute up from the hour —
     * picked out of the accepted set itself so the reveal can never name a reading the
     * drill would then mark wrong, and filtered by [ClockGloss] so it never spends a
     * line on the same reading said shorter.
     */
    private fun gloss(accepted: List<String>, h: Int, m: Int): String? {
        val display = accepted.first()
        // why: the gloss teaches the other CONSTRUCTION, so it names readings stripped
        // of the part of the day — repeating that would spend all three lines on it.
        val plain = accepted.filter { " de la " !in it && " del " !in it && " horas " !in it }
        val copular = plain.filter { it.startsWith("son las ") || it.startsWith("es la ") }
        // why: "son las cuatro y cuarenta y cinco" and "son las dieciséis cuarenta y
        // cinco" are two registers doing one move, so only one of them takes a line —
        // the timetable one, which is the register a learner cannot derive from the
        // conversational display.
        val counting = official(h, m).firstOrNull()
        // why: off the round steps, spelling the noun out is what marks the number as a
        // COUNTED minute — the contrast a learner needs against the fraction words
        // ("y cuarto", "y media") the round steps taught them. At those steps the same
        // reading is the display's own count with a word added, so it is not offered.
        val spelledMinutes = copular
            .firstOrNull { " y " in it && it.endsWith(" minutos") }
            ?.takeIf { m !in ROUND_STEPS }
        // One candidate per family: the countdown and the `para` reading are taken in
        // accepted order, which is the plainest each family has, and no further
        // countdown may follow — a second one is the same construction with its noun
        // spelled out ("es la una menos veintiún minutos"), which the subsequence rule
        // cannot see across an apocopated count. Both outrank the `minutos` register:
        // another construction teaches more than another way to say this one.
        val candidates = listOfNotNull(
            copular.firstOrNull { " menos " in it },
            plain.firstOrNull { " para " in it },
            spelledMinutes,
        ) + copular.filter { " menos " !in it }
        val trimmed = candidates.filter {
            it == counting || it == spelledMinutes || !countsUpFromTheHour(it, m)
        }
        return ClockGloss.line(display, trimmed, limit = 3, lead = "también: ", separator = " · ")
    }

    /**
     * Does the reading count the minute UP from the hour on the clock, as a number —
     * "son las cuatro y cuarenta y cinco", "son las cuatro cuarenta y cinco", "son las
     * dieciséis cuarenta y cinco"? That is one move in several registers, and the gloss
     * names the ones a learner cannot derive from the display — the timetable reading,
     * and off the round steps the spelled-out `minutos` — never the bare count, which
     * is the display with its day part dropped. The countdown and the American `para` count
     * from the COMING hour instead: different constructions, never in this class however
     * they name the number, which is also what keeps the hour word of "cinco para las
     * cinco" from reading as the minute.
     *
     * The count is matched in both spellings, plain and apocopated ("y veintiuno", "y
     * veintiún minutos"), or the apocopated twin outlives the reading it restates.
     */
    private fun countsUpFromTheHour(reading: String, m: Int): Boolean {
        if (m == 0 || " menos " in reading || " para " in reading) return false
        val head = reading.removeSuffix(" minutos").removeSuffix(" minuto")
        val counts = listOf(SpanishNumbers.underHundred(m)) + Forms.beforeMinutos(m)
        return counts.any { head.endsWith(" $it") }
    }

    private val MIDNIGHT = ClockReading(
        "son las doce de la noche",
        listOf(
            "son las doce de la noche", "es medianoche", "es la medianoche", "medianoche",
            "las doce de la noche", "son las doce",
            "las doce", "doce", "son las cero horas", "las cero horas",
        ),
        "también: es medianoche · las cero horas",
    )

    private val NOON = ClockReading(
        "son las doce del mediodía",
        listOf(
            "son las doce del mediodía", "son las doce del día", "son las doce de la mañana",
            "es mediodía", "es el mediodía", "mediodía",
            "las doce del mediodía", "las doce del día", "son las doce",
            "las doce", "doce", "son las doce horas",
        ),
        "también: son las doce del día · es mediodía",
    )
}
