package net.spross.kern.trainer

import net.spross.kern.trainer.EsperantoClockForms as Forms

/**
 * Esperanto clock times.
 *
 * One conversational family counts up from the hour on the clock (`la tria kaj dudek`,
 * `la tria kaj kvarono`) and one counts down from the coming hour (`kvarono antaŭ la
 * kvara`); the timetable register runs 0–23 and spells `horo` and `minutoj` out. The part
 * of the day is an adverb hung on the conversational readings, and it follows the hour the
 * reading NAMES, so a countdown takes the coming hour's.
 *
 * Every reading carries its own article and no copula, which is what keeps it usable
 * inside a prepositional frame. 12-hour cycle — the prompt carries the 24-hour value.
 */
internal object EsperantoClock {

    /**
     * One reading, and the hour whose part of the day completes it.
     * [period] is false where naming the part would only restate a reading already carrying
     * one, and null [namedHour] marks the timetable register, which names 0–23 itself.
     */
    private data class Core(
        val text: String,
        val namedHour: Int?,
        val countdown: Boolean = false,
        val period: Boolean = true,
    )

    fun task(hours: Int, minutes: Int): ClockReading {
        if (minutes == 0 && hours == 0) return MIDNIGHT
        if (minutes == 0 && hours == 12) return NOON
        val readings = mutableListOf<String>()
        for (core in cores(hours, minutes)) {
            val parts = core.namedHour
                ?.takeIf { core.period }
                ?.let { Forms.dayParts(it, core.countdown) }
                .orEmpty()
            // why: the reveal teaches the part of the day, which is what the 24-hour prompt
            // asks about — the bare reading follows it and grades exactly as well.
            for (part in parts) readings += "${core.text} $part"
            readings += core.text
        }
        // why: the x-system twin rides on the whole reading, so a keyboard without ŭ answers
        // the countdown and the parts of the day, not only the numerals inside them.
        val accepted = EsperantoNumbers.spellings(readings)
        return ClockReading(accepted.first(), accepted, gloss(hours, minutes, accepted))
    }

    private fun cores(h: Int, m: Int): List<Core> {
        val cur = Forms.hourWords[h % 12]
        val next = Forms.hourWords[h % 12 + 1]
        val nextHour = (h + 1) % 24
        val count = EsperantoNumbers.underHundred(m)
        val rest = 60 - m
        val restCount = EsperantoNumbers.underHundred(rest)
        val forms = mutableListOf<Core>()

        // Counting up from the hour on the clock.
        when (m) {
            0 -> {
                forms += Core("la $cur", h)
                forms += Core("la $cur horo", h)
            }
            15 -> {
                forms += Core("la $cur kaj kvarono", h)
                forms += Core("kvarono post la $cur", h)
                forms += Core("la $cur kaj dek kvin", h)
                forms += Core("la $cur kaj dek kvin minutoj", h, period = false)
            }
            30 -> {
                forms += Core("la $cur kaj duono", h)
                forms += Core("duono post la $cur", h)
                forms += Core("la $cur kaj tridek", h)
                forms += Core("la $cur kaj tridek minutoj", h, period = false)
            }
            else -> {
                // why: at a count of one the noun is what marks it as a minute — "la tria
                // kaj unu" is a number hanging off an hour until "minuto" lands after it.
                if (m == 1) forms += Core("la $cur kaj unu minuto", h)
                forms += Core("la $cur kaj $count", h)
                if (m > 1) forms += Core("la $cur kaj $count minutoj", h, period = false)
            }
        }
        // The joiner is optional in every counted reading, and dropping it is the register
        // a board is read in: "la dek-kvina dek kvin".
        if (m > 0) forms += Core("la $cur $count", h, period = false)

        // Counting down to the coming hour. "minus" is attested at the quarter and
        // nowhere else, so it stays there rather than being generalized off the grid.
        if (m > 30) {
            if (m == 45) {
                forms += Core("kvarono antaŭ la $next", nextHour, countdown = true)
                forms += Core("la $next minus kvarono", nextHour, countdown = true)
            } else {
                forms += Core("$restCount ${Forms.minuteNoun(rest)} antaŭ la $next", nextHour, countdown = true)
            }
        }
        forms += official(h, m)
        return forms.leadWith(displayCore(h, m, cur, next, count, restCount)) { it.text }
    }

    /**
     * The 0–23 register. Below thirteen its ordinal is the conversational one, so only the
     * spelled-out `horo … minutoj` reading is new there and the bare one is left out.
     */
    private fun official(h: Int, m: Int): List<Core> {
        val hourWord = Forms.officialHour(h)
        val count = EsperantoNumbers.underHundred(m)
        if (m == 0) {
            if (h < 13) return emptyList()
            return listOf(Core("la $hourWord horo", null), Core("la $hourWord", null))
        }
        val spelled = Core("la $hourWord horo kaj $count ${Forms.minuteNoun(m)}", null)
        if (h < 13) return listOf(spelled)
        return listOf(spelled, Core("la $hourWord kaj $count", null), Core("la $hourWord $count", null))
    }

    /**
     * The reading the reveal teaches. The quarters and the five-minute steps past the half
     * take the idiom a speaker reaches for; a minute off that grid is read out, because
     * counting seventeen minutes down from the coming hour is correct and nobody does it.
     */
    private fun displayCore(h: Int, m: Int, cur: String, next: String, count: String, restCount: String): String = when {
        m == 0 -> "la $cur"
        m == 1 -> "la $cur kaj unu minuto"
        m == 15 -> "la $cur kaj kvarono"
        m == 30 -> "la $cur kaj duono"
        m == 45 -> "kvarono antaŭ la $next"
        m > 30 && (m % 5 == 0 || m >= 56) ->
            "$restCount ${Forms.minuteNoun(60 - m)} antaŭ la $next"
        else -> "la $cur kaj $count"
    }

    /**
     * The other CONSTRUCTIONS, each already accepted: the counted reading behind a fraction
     * word, the `post` phrasing, the `minus` calque behind the `antaŭ` countdown, and the
     * timetable register — which below thirteen is the display with `horo` added and is
     * dropped there by [ClockGloss].
     */
    private fun gloss(h: Int, m: Int, accepted: List<String>): String? {
        val cur = Forms.hourWords[h % 12]
        val next = Forms.hourWords[h % 12 + 1]
        val rest = 60 - m
        val restCount = EsperantoNumbers.underHundred(rest)
        val count = EsperantoNumbers.underHundred(m)
        val candidates = when {
            m == 0 -> emptyList()
            m == 15 -> listOf("la $cur kaj dek kvin", "kvarono post la $cur")
            m == 30 -> listOf("la $cur kaj tridek", "duono post la $cur")
            m == 45 -> listOf("la $cur kaj kvardek kvin", "la $next minus kvarono")
            // Past the half the two families take one line each, whichever of them leads.
            m > 30 -> listOf(
                "$restCount ${Forms.minuteNoun(rest)} antaŭ la $next",
                "la $cur kaj $count",
            )
            else -> emptyList()
        }
        val listed = (candidates + official(h, m).map { it.text }).filter { it in accepted }
        return ClockGloss.line(accepted.first(), listed, limit = 3, lead = "ankaŭ: ", separator = " · ")
    }

    private val MIDNIGHT = ClockReading(
        "noktomezo",
        listOf(
            "noktomezo", "meznokto", "la dek-dua nokte", "la dek-dua horo nokte",
            "la dek-dua", "la dek-dua horo", "la nula horo", "la nula",
        ),
        "ankaŭ: meznokto · la nula horo",
    )

    private val NOON = ClockReading(
        "tagmezo",
        listOf(
            "tagmezo", "la dek-dua tagmeze", "la dek-dua horo tagmeze",
            "la dek-dua", "la dek-dua horo",
        ),
        "ankaŭ: la dek-dua tagmeze · la dek-dua horo",
    )
}
