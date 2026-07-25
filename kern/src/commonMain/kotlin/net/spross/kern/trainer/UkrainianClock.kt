package net.spross.kern.trainer

/**
 * Colloquial Ukrainian clock times (v1-authored content, language-reviewed).
 * Patterns (one canonical form each, plus common accepted variants):
 * - exact hour: "друга година" (variant "друга")
 * - :15 → "чверть на третю" (variant "п'ятнадцять по другій")
 * - :30 → "пів на третю"
 * - :45 → "за чверть третя" (variant "за п'ятнадцять третя")
 * - other minutes → digital reading "друга тридцять п'ять"
 *   (variants "десять по другій" for <30, "за десять третя" for >30).
 * Hour ordinals are feminine, agreeing with година; 12-hour cycle.
 */
internal object UkrainianClock {
    // index 1..12
    private val ordinalNominative = listOf("", "перша", "друга", "третя", "четверта", "п'ята", "шоста", "сьома", "восьма", "дев'ята", "десята", "одинадцята", "дванадцята")
    private val ordinalAccusative = listOf("", "першу", "другу", "третю", "четверту", "п'яту", "шосту", "сьому", "восьму", "дев'яту", "десяту", "одинадцяту", "дванадцяту")
    private val ordinalLocative = listOf("", "першій", "другій", "третій", "четвертій", "п'ятій", "шостій", "сьомій", "восьмій", "дев'ятій", "десятій", "одинадцятій", "дванадцятій")
    private val ordinalGenitive = listOf("", "першої", "другої", "третьої", "четвертої", "п'ятої", "шостої", "сьомої", "восьмої", "дев'ятої", "десятої", "одинадцятої", "дванадцятої")

    private fun hourIndex(h: Int): Int {
        val h12 = h % 12
        return if (h12 == 0) 12 else h12
    }

    /** Non-round minutes fall back to a digital reading ("друга сімнадцять"). */
    fun task(hours: Int, minutes: Int): ClockReading {
        val cur = hourIndex(hours)
        val next = hourIndex(hours + 1)
        val minuteWord = UkrainianNumbers.cardinal(minutes.toLong())

        return when (minutes) {
            0 -> {
                val display = "${ordinalNominative[cur]} година"
                ClockReading(display, listOf(display, ordinalNominative[cur]))
            }
            15 -> {
                val display = "чверть на ${ordinalAccusative[next]}"
                // Variants confirmed equally common by the language review.
                val variants = listOf(
                    "п'ятнадцять по ${ordinalLocative[cur]}",
                    "чверть по ${ordinalLocative[cur]}",
                )
                ClockReading(
                    display, listOf(display) + variants + digital(cur, minuteWord),
                    "«чверть на …» zählt zur kommenden Stunde (wie „Viertel drei“)",
                )
            }
            30 -> {
                val display = "пів на ${ordinalAccusative[next]}"
                val variant = "пів ${ordinalGenitive[next]}"
                ClockReading(
                    display, listOf(display, variant, digital(cur, minuteWord)),
                    "«пів на …» zählt zur kommenden Stunde (wie deutsches „halb drei“)",
                )
            }
            45 -> {
                val display = "за чверть ${ordinalNominative[next]}"
                val variants = listOf(
                    "за п'ятнадцять ${ordinalNominative[next]}",
                    "за чверть до ${ordinalGenitive[next]}",
                )
                ClockReading(
                    display, listOf(display) + variants + digital(cur, minuteWord),
                    "«за чверть …» = Viertel vor der kommenden Stunde",
                )
            }
            else -> {
                val display = digital(cur, minuteWord)
                val variant = if (minutes < 30) {
                    "$minuteWord по ${ordinalLocative[cur]}"
                } else {
                    "за ${UkrainianNumbers.cardinal((60 - minutes).toLong())} ${ordinalNominative[next]}"
                }
                ClockReading(display, listOf(display, variant))
            }
        }
    }

    /** Digital-style reading: "друга тридцять п'ять". */
    private fun digital(hourIdx: Int, minuteWord: String): String =
        "${ordinalNominative[hourIdx]} $minuteWord"
}
