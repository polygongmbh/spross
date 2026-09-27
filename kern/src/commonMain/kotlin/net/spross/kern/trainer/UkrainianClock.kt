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
 *
 * Both registers also read the time WHEN, «о» + locative (`о четвертій дня`,
 * `о шістнадцятій тридцять`): accepted, never the display, and the only readings a
 * time-when frame composes ([TrainerLanguagePack.readingPrepositions]).
 */
internal object UkrainianClock {

    /** One reading, and the hour whose part of the day completes it (null: official). */
    private data class Core(val text: String, val namedHour: Int?)

    fun task(hours: Int, minutes: Int): ClockReading {
        if (minutes == 0 && hours == 0) return NAMED_MIDNIGHT
        if (minutes == 0 && hours == 12) return NAMED_NOON
        val cores = cores(hours, minutes)
        val accepted = mutableListOf<String>()
        for (core in cores) {
            val parts = core.namedHour?.let(Forms::dayParts).orEmpty()
            for (part in parts) accepted += "${core.text} $part"
            accepted += core.text
        }
        accepted += timeWhen(hours, minutes)
        val readings = accepted.distinct()
        // The alternatives worth naming on the reveal, each a different way of SAYING the
        // time rather than the same one shorter ([ClockGloss]).
        val gloss = ClockGloss.line(
            readings.first(), cores.drop(1).take(2).map { it.text },
            limit = 2, lead = "також: ", separator = ", ",
        )
        return ClockReading(readings.first(), readings, gloss)
    }

    /**
     * Every reading of the time, in the order the reveal spends them: the display first,
     * the two alternatives its gloss names second and third, everything else after.
     *
     * Round steps display the construction a speaker reaches for; a minute off that grid
     * is simply read out (`друга сімнадцять`) — "сімнадцять хвилин на третю" is correct
     * and almost never said, so it is glossed instead. Where the colloquial register has
     * no alternative left, the official one fills the gloss: at the full hour its ordinal
     * collapses into the display below thirteen — the same word, which [ClockGloss] drops —
     * so the spoken-zero digital reading is the one register left to name there.
     */
    private fun cores(h: Int, m: Int): List<Core> {
        val cur = Forms.index(h)
        val next = Forms.index(h + 1)
        val nextHour = (h + 1) % 24
        fun into(label: String) = Core("$label на ${Forms.accusative[next]}", nextHour)
        fun after(label: String) = Core("$label по ${Forms.locative[cur]}", h)
        fun ahead(label: String) = Core("за $label ${Forms.nominative[next]}", nextHour)
        fun before(label: String) = Core("$label до ${Forms.genitive[next]}", nextHour)
        val count = Forms.minuteNumeral(m)
        val counted = "$count ${Forms.minuteNoun(m)}"
        val rest = 60 - m
        val left = Forms.minuteNumeralAccusative(rest)
        val leftCounted = "$left ${Forms.minuteNoun(rest, accusative = true)}"
        val restCount = Forms.minuteNumeral(rest)
        val restCounted = "$restCount ${Forms.minuteNoun(rest)}"
        val digital = digital(h, m, cur)
        val official = official(h, m)

        val forms = when {
            // why: the full hour has no colloquial alternative — counting zero minutes
            // into the coming hour ("нуль хвилин на третю") is what nobody says.
            m == 0 -> listOf(Core("${Forms.nominative[cur]} година", h)) + official +
                Core(Forms.nominative[cur], h)
            m == 15 -> listOf(into("чверть"), into(counted), after("чверть"), after(counted)) +
                into(count) + after(count) + digital + official
            m == 30 -> listOf(
                into("пів"), Core("пів ${Forms.genitive[next]}", nextHour), into(counted),
                after(counted), after(count),
            ) + digital + official
            m == 45 -> listOf(ahead("чверть"), ahead(leftCounted), before("чверть")) +
                ahead(left) + before(restCounted) + before(restCount) + digital + official
            m < 30 -> {
                val lead = if (m in ROUND_STEPS) into(counted) else digital.first()
                val glossed = if (m in ROUND_STEPS) listOf(after(counted), official.first())
                else listOf(into(counted), after(counted))
                // why: at a count of one the numeral is dropped, not spelled — "хвилина
                // на третю", the way Ukrainian counts a single anything — and the noun
                // carries that count on its own, so the bare "одна на третю" is no reading.
                val shortened = if (m == 1) listOf(into("хвилина"), after("хвилина"))
                else listOf(into(count), after(count))
                listOf(lead) + glossed + into(counted) + after(counted) + shortened + digital + official
            }
            rest == 1 -> listOf(digital.first(), ahead("одну хвилину"), before("хвилина"), ahead("хвилину")) +
                digital + official
            else -> {
                val lead = if (rest in ROUND_STEPS) ahead(left) else digital.first()
                listOf(lead, ahead(leftCounted), before(restCounted)) +
                    ahead(left) + before(restCount) + digital + official
            }
        }
        return forms.distinct()
    }

    /** The five-minute steps a reading counts toward or away from the coming hour. */
    private val ROUND_STEPS = setOf(5, 10, 20, 25)

    /** Reading the face out: "друга тридцять п'ять", "п'ята нуль п'ять". */
    private fun digital(h: Int, m: Int, cur: Int): List<Core> {
        val count = Forms.minuteNumeral(m)
        val plain = Core("${Forms.nominative[cur]} $count", h)
        return if (m in 1..9) listOf(Core("${Forms.nominative[cur]} нуль $count", h), plain) else listOf(plain)
    }

    /** The 0–23 register's readings on their own, time-when ones included. */
    fun twentyFourHour(h: Int, m: Int): List<String> = official(h, m).map { it.text } + officialWhen(h, m)

    /**
     * The hour-first readings in the locative «о» governs — the full hour and the face read
     * out, in both registers. The minute stays the nominative count it is in the digital
     * reading: `о четвертій тридцять дня`, `о шістнадцятій годині тридцять хвилин`.
     */
    private fun timeWhen(h: Int, m: Int): List<String> {
        val cur = Forms.index(h)
        val hour = Forms.at(Forms.locative[cur])
        val count = Forms.minuteNumeral(m)
        val colloquial = when {
            m == 0 -> listOf(hour, "$hour годині")
            m < 10 -> listOf("$hour нуль $count", "$hour $count")
            else -> listOf("$hour $count")
        }
        val parts = Forms.dayParts(h)
        return colloquial.flatMap { core -> parts.map { "$core $it" } + core } + officialWhen(h, m)
    }

    /** [official] after «о»; hour zero again takes no clipped reading. */
    private fun officialWhen(h: Int, m: Int): List<String> {
        val hour = Forms.at(Forms.officialLocative[h])
        if (m == 0) return if (h == 0) listOf("$hour годині") else listOf(hour, "$hour годині")
        val full = "$hour годині ${Forms.minuteNumeral(m)} ${Forms.minuteNoun(m)}"
        return if (h == 0) listOf(full) else listOf(full, "$hour ${Forms.minuteNumeral(m)}")
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

    private val NAMED_MIDNIGHT = ClockReading(
        "північ",
        listOf(
            "північ", "опівночі", "дванадцята година ночі", "дванадцята ночі",
            "дванадцята година", "дванадцята", "нульова година", "двадцять четверта година",
            "о дванадцятій ночі", "о дванадцятій годині ночі", "о дванадцятій", "о дванадцятій годині",
            "о нульовій годині",
        ),
        "також: опівночі, нульова година",
    )

    private val NAMED_NOON = ClockReading(
        "дванадцята година дня",
        listOf(
            "дванадцята година дня", "дванадцята дня", "полудень", "опівдні",
            "дванадцята година", "дванадцята", "дванадцять нуль нуль",
            "о дванадцятій дня", "о дванадцятій годині дня", "о дванадцятій", "о дванадцятій годині",
        ),
        "також: полудень, опівдні",
    )
}
