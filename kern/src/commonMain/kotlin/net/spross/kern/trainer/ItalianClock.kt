package net.spross.kern.trainer

import net.spross.kern.trainer.ItalianClockForms as Forms

/**
 * Conversational Italian clock times.
 *
 * Two families read the same time. The additive one counts up from the hour on the clock
 * ("sono le due e venti") and the countdown one down from the coming hour, either as a
 * subtraction from it ("sono le tre meno venti") or as what is still missing before it
 * ("mancano venti minuti alle tre"). The first two carry the copula and the article the
 * named hour decides — "è l'una", "sono le due" — and the part of the day follows that
 * same named hour; the third is a complete statement already and takes neither.
 *
 * The article-only reading ("le due e mezza") and the timetable register ("sono le
 * quattordici e trenta") are accepted beside them; naming the part of the day is optional
 * everywhere. 12-hour cycle — the prompt carries the 24-hour value.
 */
internal object ItalianClock {

    /**
     * One reading without its copula. [namedHour] is the hour it names; [period] is false
     * where the reading spells the minute noun out, which says nothing about the time.
     */
    private data class Core(
        val text: String,
        val namedHour: Int,
        val period: Boolean = true,
    )

    fun task(hours: Int, minutes: Int): ClockReading {
        if (minutes == 0 && hours == 0) return MIDNIGHT
        if (minutes == 0 && hours == 12) return NOON
        val readings = mutableListOf<String>()
        for (core in cores(hours, minutes)) {
            val head = core.text.substringBefore(' ')
            val parts = if (core.period) Forms.dayParts(core.namedHour) else emptyList()
            for (dressed in listOf(Forms.copula(head) + core.text, Forms.article(head) + core.text)) {
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
        val count = ItalianNumbers.cardinal(m.toLong())
        val rest = 60 - m
        val restCount = ItalianNumbers.cardinal(rest.toLong())
        val forms = mutableListOf<Core>()

        // Additive: counting up from the hour on the clock.
        when (m) {
            0 -> forms += Core(cur, h)
            // why: Italian counts a single minute with its noun, never with a bare "uno".
            1 -> forms += Core("$cur e un minuto", h)
            15 -> {
                forms += Core("$cur e un quarto", h)
                forms += Core("$cur e quindici", h)
            }
            30 -> {
                forms += Core("$cur e mezza", h)
                forms += Core("$cur e mezzo", h)
                forms += Core("$cur e trenta", h)
            }
            45 -> {
                forms += Core("$cur e tre quarti", h)
                forms += Core("$cur e quarantacinque", h)
            }
            else -> forms += Core("$cur e $count", h)
        }
        // Spelling the noun out marks the number as a COUNTED minute and says nothing
        // about the time, so the part of the day does not ride on it.
        if (m > 1) {
            for (spelled in Forms.beforeMinuti(m)) forms += Core("$cur e $spelled minuti", h, period = false)
        }

        // Countdown: the coming hour, less what is left of this one. The subtraction
        // leaves the noun out — what is missing is counted WITH it, by the manca family.
        if (m > 30) {
            when {
                m == 45 -> {
                    forms += Core("$next meno un quarto", nextHour)
                    forms += Core("$next meno quindici", nextHour)
                }
                rest == 1 -> forms += Core("$next meno un minuto", nextHour)
                else -> forms += Core("$next meno $restCount", nextHour)
            }
        }
        return forms.leadWith(displayCore(h, m, cur, next, count, restCount)) { it.text }
    }

    /**
     * Readings that carry no copula of their own: what is still missing before the coming
     * hour, and the timetable register. Both are whole statements already.
     */
    private fun bare(h: Int, m: Int): List<String> {
        val out = mutableListOf<String>()
        if (m > 30) out += missingFamily(h, m)
        out += official(h, m)
        return out
    }

    /** "manca un quarto alle tre", "mancano venti minuti all'una" — the hour as a target. */
    private fun missingFamily(h: Int, m: Int): List<String> {
        val target = Forms.toTheHour(Forms.hourWords[(h % 12 + 1) % 12])
        val rest = 60 - m
        // The verb agrees with what is missing, so one minute and one quarter take mancA.
        val out = when (rest) {
            1 -> listOf("manca un minuto $target")
            15 -> listOf("manca un quarto $target", "mancano quindici minuti $target")
            else -> Forms.beforeMinuti(rest).map { "mancano $it minuti $target" }
        }
        // The part of the day rides on the shortest form only; a second copy of it
        // teaches nothing the first does not.
        val parts = Forms.dayParts((h + 1) % 24)
        return listOf(out.first()) + parts.map { "${out.first()} $it" } + out.drop(1)
    }

    /**
     * "sono le quattordici e trenta" — timetables, news and announcements. Offered from
     * thirteen up, which is where the register stops repeating the 12-hour reading word
     * for word.
     */
    private fun official(h: Int, m: Int): List<String> {
        if (h < 13) return emptyList()
        val hourWord = Forms.officialHour(h)
        if (m == 0) return listOf("sono le $hourWord", "le $hourWord")
        // why: one minute takes the noun here too — "e uno" names no unit at all.
        if (m == 1) return listOf("sono le $hourWord e un minuto", "le $hourWord e un minuto")
        val count = ItalianNumbers.cardinal(m.toLong())
        return listOf(
            "sono le $hourWord e $count",
            "le $hourWord e $count",
            "sono le $hourWord e ${Forms.beforeMinuti(m).first()} minuti",
        )
    }

    /**
     * The reading the reveal teaches. Round steps take the fraction word or the countdown
     * a speaker reaches for; a minute off the five-minute grid is counted up from the hour
     * instead — 14:37 is "sono le due e trentasette", not "sono le tre meno ventitré".
     */
    private fun displayCore(h: Int, m: Int, cur: String, next: String, count: String, restCount: String): String =
        when {
            m == 0 -> cur
            m == 1 -> "$cur e un minuto"
            m == 15 -> "$cur e un quarto"
            m == 30 -> "$cur e mezza"
            m == 45 -> "$next meno un quarto"
            m > 30 && m % 5 == 0 -> "$next meno $restCount"
            else -> "$cur e $count"
        }

    /**
     * One alternative per construction — the subtraction from the coming hour, what is
     * missing before it, the timetable register — picked out of the accepted set itself so
     * the reveal can never name a reading the drill would then mark wrong, and filtered by
     * [ClockGloss] so it never spends a line on the same reading said shorter.
     */
    private fun gloss(accepted: List<String>, h: Int, m: Int): String? {
        // why: the gloss teaches the other CONSTRUCTION, so it names readings stripped of
        // the part of the day — repeating that would spend every line on it.
        val plain = accepted.filter { " di " !in it && " del " !in it }
        val copular = plain.filter { it.startsWith("sono le ") || it.startsWith("è l'") }
        val candidates = listOfNotNull(
            copular.firstOrNull { " meno " in it },
            plain.firstOrNull { it.startsWith("manca") },
            official(h, m).firstOrNull()?.takeIf { it in accepted },
        ) + copular.filter { " meno " !in it }
        return ClockGloss.line(accepted.first(), candidates, limit = 3, lead = "anche: ", separator = " · ")
    }

    private val MIDNIGHT = ClockReading(
        "è mezzanotte",
        listOf(
            "è mezzanotte", "mezzanotte", "sono le dodici di notte", "le dodici di notte",
            "sono le dodici", "le dodici", "dodici",
            "sono le ventiquattro", "le ventiquattro",
        ),
        "anche: sono le dodici di notte · sono le ventiquattro",
    )

    private val NOON = ClockReading(
        "è mezzogiorno",
        listOf(
            "è mezzogiorno", "mezzogiorno",
            "sono le dodici", "le dodici", "dodici",
        ),
        "anche: sono le dodici",
    )
}
