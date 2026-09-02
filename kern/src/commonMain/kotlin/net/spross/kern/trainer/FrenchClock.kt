package net.spross.kern.trainer

import net.spross.kern.trainer.FrenchClockForms as Forms

/**
 * French conversational clock times.
 *
 * The reading is BARE — `deux heures et quart`, `midi`, `minuit` — and `il est …` grades
 * beside it. That is the whole reason French can carry a prepositional frame at all: `à`
 * never contracts with an hour word, so `à deux heures et quart` composes for every draw,
 * while the copula stays a reading of its own and is dropped wherever a frame does not
 * already say it (`TrainerLanguagePack.readingPrepositions`).
 *
 * Two families read the same face. The count-up one counts from the hour on the clock
 * (`deux heures vingt`), the countdown one from the coming hour (`trois heures moins
 * vingt`), and the part of the day follows the hour a reading NAMES. Round steps take the
 * fraction words `et quart` · `et demie` · `moins le quart`; the timetable register runs
 * 0–23 and names no part of the day. 12-hour cycle — the prompt carries the 24-hour value.
 */
internal object FrenchClock {

    /** One reading without its copula, paired with the hour it names. */
    private data class Core(val text: String, val namedHour: Int)

    fun task(hours: Int, minutes: Int): ClockReading {
        val readings = mutableListOf<String>()
        for (core in cores(hours, minutes)) {
            val parts = Forms.dayParts(core.namedHour)
            for (dressed in listOf(core.text, "il est ${core.text}")) {
                readings += dressed
                for (part in parts) readings += "$dressed $part"
            }
        }
        for (reading in official(hours, minutes)) {
            readings += reading
            readings += "il est $reading"
        }
        // why: the pipeline DELETES a hyphen instead of spacing it, so "quarante-cinq" and
        // "quarante cinq" are unrelated strings and a learner who spaces one loses the Sprosse.
        val accepted = (readings + readings.map { it.replace('-', ' ') }).distinct()
        return ClockReading(accepted.first(), accepted, gloss(hours, minutes))
    }

    private fun cores(h: Int, m: Int): List<Core> {
        val here = Forms.hourWord(h)
        val next = Forms.hourWord(h + 1)
        val nextHour = (h + 1) % 24
        val out = mutableListOf<Core>()
        when (m) {
            0 -> out += Core(here, h)
            15 -> {
                out += Core("$here et quart", h)
                out += Core("$here quinze", h)
            }
            30 -> {
                out += Core(Forms.half(h), h)
                out += Core("$here trente", h)
            }
            45 -> {
                out += Core("$next moins le quart", nextHour)
                out += Core("$next moins un quart", nextHour)
                out += Core("$here quarante-cinq", h)
            }
            else -> out += Core("$here ${FrenchNumbers.minute(m)}", h)
        }
        // The countdown reaches every minute past the half hour; "moins trente" does not
        // exist, so the half is the one step that has only the fraction word.
        if (m > 30 && m != 45) out += Core("$next moins ${FrenchNumbers.minute(60 - m)}", nextHour)
        return out.leadWith(display(h, m)) { it.text }
    }

    /**
     * The reading the reveal teaches. The round steps take the idiom a speaker reaches for
     * — the fraction words up to the half, the countdown past it — and a minute off the
     * five-minute grid is READ OUT: 14:37 is `deux heures trente-sept`, and counting
     * twenty-three minutes back off three o'clock is correct and nobody does it.
     */
    private fun display(h: Int, m: Int): String {
        val here = Forms.hourWord(h)
        val next = Forms.hourWord(h + 1)
        return when {
            m == 0 -> here
            m == 15 -> "$here et quart"
            m == 30 -> Forms.half(h)
            m == 45 -> "$next moins le quart"
            m > 30 && (m % 5 == 0 || m >= 56) -> "$next moins ${FrenchNumbers.minute(60 - m)}"
            else -> "$here ${FrenchNumbers.minute(m)}"
        }
    }

    /** `quatorze heures quinze`, `zéro heure trente` — timetables, news, announcements. */
    private fun official(h: Int, m: Int): List<String> {
        val hour = Forms.officialHour(h)
        return if (m == 0) listOf(hour) else listOf("$hour ${FrenchNumbers.minute(m)}")
    }

    /**
     * The reveal names the 24-hour register and nothing else, because nothing else is a
     * construction the display did not already take: whichever of the two 12-hour families
     * fits the step IS the display, and the other one says the same clock in the register
     * this line already carries (`docs/clock-registers.md` § What a reveal names). Where the
     * two coincide — a small hour read out off the grid — the gloss is absent, not empty.
     */
    private fun gloss(h: Int, m: Int): String? = ClockGloss.line(
        display(h, m), official(h, m), limit = 3, lead = "aussi : ", separator = " · ",
    )
}
