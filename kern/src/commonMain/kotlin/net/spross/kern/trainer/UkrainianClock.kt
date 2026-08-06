package net.spross.kern.trainer

import net.spross.kern.trainer.UkrainianClockForms as Forms

/**
 * Colloquial and official Ukrainian clock times.
 *
 * Two systems run side by side and both are accepted everywhere. The colloquial one
 * counts on a 12-hour ordinal: the hour on its own (`друга година`), minutes INTO the
 * coming hour (`двадцять хвилин на третю`, `чверть на третю`, `пів на третю`) or past
 * the current one (`двадцять хвилин по другій`), and a countdown once the half is gone
 * (`за двадцять хвилин третя`, `чверть до третьої`). The official one runs 0–23 and is
 * what timetables, news and announcements use (`чотирнадцята година сорок хвилин`).
 *
 * A colloquial reading is completed by the part of the day — and it belongs to the hour
 * the reading NAMES, not the hour on the clock, so 11:45 is `за чверть дванадцята дня`.
 * The official reading never takes one; it has 24 hours of its own.
 *
 * Naming the part of the day is optional: every reading is accepted without it too.
 */
internal object UkrainianClock {

    /** One reading, and the hour whose part of the day completes it (null: official). */
    private data class Core(val text: String, val namedHour: Int?)

    fun task(hours: Int, minutes: Int): ClockReading {
        if (minutes == 0 && hours == 0) return NAMED_MIDNIGHT
        if (minutes == 0 && hours == 12) return NAMED_NOON
        val accepted = mutableListOf<String>()
        for (core in cores(hours, minutes)) {
            val parts = core.namedHour?.let(Forms::dayParts).orEmpty()
            for (part in parts) accepted += "${core.text} $part"
            accepted += core.text
        }
        return ClockReading(accepted.first(), accepted.distinct(), gloss(hours, minutes))
    }

    /** Every reading of the time, the one the reveal teaches first. */
    private fun cores(h: Int, m: Int): List<Core> {
        val cur = Forms.index(h)
        val next = Forms.index(h + 1)
        val nextHour = (h + 1) % 24
        val toAccusative = Forms.accusative[next]
        val past = Forms.locative[cur]
        val count = Forms.minuteNumeral(m)
        val rest = 60 - m
        val forms = mutableListOf<Core>()

        when {
            m == 0 -> {
                forms += Core("${Forms.nominative[cur]} година", h)
                forms += Core(Forms.nominative[cur], h)
                forms += Core("рівно ${Forms.nominative[cur]} година", h)
                forms += Core("рівно ${Forms.nominative[cur]}", h)
                // why: постпозитивне рівно closes the phrase, so nothing may follow it —
                // this reading takes no part of the day.
                forms += Core("${Forms.nominative[cur]} година рівно", null)
            }
            m < 30 -> {
                if (m == 15) {
                    forms += Core("чверть на $toAccusative", nextHour)
                    forms += Core("чверть по $past", h)
                }
                // why: at a count of one the numeral is dropped, not spelled — "хвилина
                // на третю", the way Ukrainian counts a single anything.
                if (m == 1) forms += Core("хвилина на $toAccusative", nextHour)
                forms += Core("$count ${Forms.minuteNoun(m)} на $toAccusative", nextHour)
                if (m == 1) forms += Core("хвилина по $past", h)
                forms += Core("$count ${Forms.minuteNoun(m)} по $past", h)
                // why: the noun carries the count of one on its own, so the bare numeral
                // ("одна на третю") is not a reading anyone offers.
                if (m > 1) {
                    forms += Core("$count на $toAccusative", nextHour)
                    forms += Core("$count по $past", h)
                }
                forms += digital(h, m, cur)
            }
            m == 30 -> {
                forms += Core("пів на $toAccusative", nextHour)
                forms += Core("пів ${Forms.genitive[next]}", nextHour)
                forms += Core("тридцять хвилин на $toAccusative", nextHour)
                forms += Core("тридцять хвилин по $past", h)
                forms += Core("тридцять по $past", h)
                forms += digital(h, m, cur)
            }
            else -> {
                if (m == 45) {
                    forms += Core("за чверть ${Forms.nominative[next]}", nextHour)
                    forms += Core("чверть до ${Forms.genitive[next]}", nextHour)
                }
                val left = Forms.minuteNumeralAccusative(rest)
                if (rest == 1) {
                    forms += Core("за хвилину ${Forms.nominative[next]}", nextHour)
                    forms += Core("за одну хвилину ${Forms.nominative[next]}", nextHour)
                    forms += Core("хвилина до ${Forms.genitive[next]}", nextHour)
                } else {
                    forms += Core("за $left ${Forms.minuteNoun(rest, accusative = true)} ${Forms.nominative[next]}", nextHour)
                    forms += Core("за $left ${Forms.nominative[next]}", nextHour)
                    forms += Core("${Forms.minuteNumeral(rest)} ${Forms.minuteNoun(rest)} до ${Forms.genitive[next]}", nextHour)
                    forms += Core("${Forms.minuteNumeral(rest)} до ${Forms.genitive[next]}", nextHour)
                }
                forms += digital(h, m, cur)
            }
        }
        forms += official(h, m)
        return forms.leadWith(displayText(h, m, cur, next))
    }

    /**
     * The reading the reveal teaches. Round steps take the construction a speaker
     * reaches for; a minute off that grid is simply read out (`друга сімнадцять`) —
     * "сімнадцять хвилин на третю" is correct and almost never said.
     */
    private fun displayText(h: Int, m: Int, cur: Int, next: Int): String = when {
        m == 0 -> "${Forms.nominative[cur]} година"
        m == 15 -> "чверть на ${Forms.accusative[next]}"
        m == 30 -> "пів на ${Forms.accusative[next]}"
        m == 45 -> "за чверть ${Forms.nominative[next]}"
        m in setOf(5, 10, 20, 25) ->
            "${Forms.minuteNumeral(m)} ${Forms.minuteNoun(m)} на ${Forms.accusative[next]}"
        60 - m in setOf(5, 10, 20, 25) ->
            "за ${Forms.minuteNumeralAccusative(60 - m)} ${Forms.nominative[next]}"
        else -> digital(h, m, cur).first().text
    }

    /** Reading the face out: "друга тридцять п'ять", "п'ята нуль п'ять". */
    private fun digital(h: Int, m: Int, cur: Int): List<Core> {
        val count = Forms.minuteNumeral(m)
        val plain = Core("${Forms.nominative[cur]} $count", h)
        return if (m in 1..9) listOf(Core("${Forms.nominative[cur]} нуль $count", h), plain) else listOf(plain)
    }

    /**
     * The 0–23 register. Hour zero has an ordinal (`нульова`) but no clipped reading —
     * "нульова п'ять" is not Ukrainian.
     */
    private fun official(h: Int, m: Int): List<Core> {
        val hourWord = Forms.official[h]
        if (m == 0) {
            if (h == 0) return listOf(Core("нульова година", null))
            return listOf(
                Core("$hourWord година", null),
                Core("${UkrainianNumbers.cardinal(h.toLong())} нуль нуль", null),
            )
        }
        val full = Core("$hourWord година ${Forms.minuteNumeral(m)} ${Forms.minuteNoun(m)}", null)
        if (h == 0) return listOf(full)
        return listOf(full, Core("$hourWord ${Forms.minuteNumeral(m)}", null))
    }

    /** Alternatives worth naming on the reveal — each one already accepted. */
    private fun gloss(h: Int, m: Int): String {
        val cur = Forms.index(h)
        val next = Forms.index(h + 1)
        val alternatives = when {
            m == 0 -> listOf("${Forms.nominative[cur]} ${Forms.dayParts(h)[0]}", "рівно ${Forms.nominative[cur]}")
            m == 15 -> listOf("п'ятнадцять хвилин на ${Forms.accusative[next]}", "чверть по ${Forms.locative[cur]}")
            m == 30 -> listOf("пів ${Forms.genitive[next]}", "тридцять хвилин на ${Forms.accusative[next]}")
            m == 45 -> listOf("за п'ятнадцять хвилин ${Forms.nominative[next]}", "чверть до ${Forms.genitive[next]}")
            m < 30 -> listOf(
                "${Forms.minuteNumeral(m)} ${Forms.minuteNoun(m)} на ${Forms.accusative[next]}",
                "${Forms.minuteNumeral(m)} ${Forms.minuteNoun(m)} по ${Forms.locative[cur]}",
            )
            60 - m == 1 -> listOf("за одну хвилину ${Forms.nominative[next]}", "хвилина до ${Forms.genitive[next]}")
            else -> listOf(
                "за ${Forms.minuteNumeralAccusative(60 - m)} ${Forms.minuteNoun(60 - m, accusative = true)} ${Forms.nominative[next]}",
                "${Forms.minuteNumeral(60 - m)} ${Forms.minuteNoun(60 - m)} до ${Forms.genitive[next]}",
            )
        }
        return "також: " + alternatives.joinToString(", ")
    }

    private fun List<Core>.leadWith(text: String): List<Core> {
        val at = indexOfFirst { it.text == text }
        return if (at <= 0) this else listOf(this[at]) + filterIndexed { i, _ -> i != at }
    }

    private val NAMED_MIDNIGHT = ClockReading(
        "північ",
        listOf(
            "північ", "опівночі", "дванадцята година ночі", "дванадцята ночі",
            "дванадцята година", "дванадцята", "нульова година", "двадцять четверта година",
        ),
        "також: опівночі, нульова година",
    )

    private val NAMED_NOON = ClockReading(
        "дванадцята година дня",
        listOf(
            "дванадцята година дня", "дванадцята дня", "полудень", "опівдні",
            "дванадцята година", "дванадцята", "дванадцять нуль нуль",
        ),
        "також: полудень, опівдні",
    )
}
