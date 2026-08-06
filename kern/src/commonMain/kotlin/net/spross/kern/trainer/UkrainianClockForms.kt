package net.spross.kern.trainer

/**
 * The word tables [UkrainianClock] reads its hours and minutes out of.
 *
 * Hour ordinals are feminine, agreeing with година, and each construction takes its
 * own case: `друга година` (nominative), `на другу` (accusative), `по другій`
 * (locative), `до другої` / `пів другої` (genitive). Minutes count хвилина, also
 * feminine — the numeral agrees with it and the noun takes the Slavic count form.
 */
internal object UkrainianClockForms {

    // index 1..12
    val nominative = listOf("", "перша", "друга", "третя", "четверта", "п'ята", "шоста", "сьома", "восьма", "дев'ята", "десята", "одинадцята", "дванадцята")
    val accusative = listOf("", "першу", "другу", "третю", "четверту", "п'яту", "шосту", "сьому", "восьму", "дев'яту", "десяту", "одинадцяту", "дванадцяту")
    val locative = listOf("", "першій", "другій", "третій", "четвертій", "п'ятій", "шостій", "сьомій", "восьмій", "дев'ятій", "десятій", "одинадцятій", "дванадцятій")
    val genitive = listOf("", "першої", "другої", "третьої", "четвертої", "п'ятої", "шостої", "сьомої", "восьмої", "дев'ятої", "десятої", "одинадцятої", "дванадцятої")

    /** The official register's ordinals, index 0..23 — timetables, news, announcements. */
    val official = listOf(
        "нульова", "перша", "друга", "третя", "четверта", "п'ята", "шоста", "сьома",
        "восьма", "дев'ята", "десята", "одинадцята", "дванадцята", "тринадцята",
        "чотирнадцята", "п'ятнадцята", "шістнадцята", "сімнадцята", "вісімнадцята",
        "дев'ятнадцята", "двадцята", "двадцять перша", "двадцять друга", "двадцять третя",
    )

    /** 1..12 from a 24-hour hour, twelve for both noons. */
    fun index(hour: Int): Int = (hour % 12).let { if (it == 0) 12 else it }

    /**
     * The parts of the day that fit the hour the READING NAMES, canonical first.
     * Which hour that is depends on the construction: `друга година` and the digital
     * readings name their own hour, while `на третю` / `пів на третю` / `за чверть
     * третя` all name the COMING one — so 11:45 is `за чверть дванадцята дня`, not
     * `ранку`, and 17:45 is `за чверть шоста вечора`, not `дня`.
     * Boundaries overlap where speakers do; both readings are accepted there.
     */
    fun dayParts(namedHour: Int): List<String> = when (namedHour) {
        0, 1, 2 -> listOf("ночі")
        3 -> listOf("ночі", "ранку")
        in 4..11 -> listOf("ранку")
        in 12..16 -> listOf("дня")
        17 -> listOf("вечора", "дня")
        in 18..22 -> listOf("вечора")
        else -> listOf("вечора", "ночі")
    }

    /** 1 → хвилина/хвилину, 2–4 → хвилини, else (11–14 included) → хвилин. */
    fun minuteNoun(n: Int, accusative: Boolean = false): String = when {
        n % 100 in 11..14 -> "хвилин"
        n % 10 == 1 -> if (accusative) "хвилину" else "хвилина"
        n % 10 in 2..4 -> "хвилини"
        else -> "хвилин"
    }

    /** Feminine numeral for a minute count: `дві хвилини`, `двадцять одна хвилина`. */
    fun minuteNumeral(n: Int): String = UkrainianNumbers.feminine(n.toLong())

    /** The same numeral under `за`, which governs the accusative: `за одну хвилину`. */
    fun minuteNumeralAccusative(n: Int): String =
        minuteNumeral(n).let { if (it.endsWith("одна")) it.dropLast(4) + "одну" else it }
}
