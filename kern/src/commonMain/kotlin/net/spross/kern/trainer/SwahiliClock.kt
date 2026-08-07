package net.spross.kern.trainer

/**
 * Swahili saa-system clock times. The saa hour is the western hour shifted by six, so
 * eight in the morning is saa mbili — the second hour of daylight. Quarters have their
 * own words (`na robo`, `kasorobo`), the half hour is `na nusu`, and past the half the
 * reading counts down to the coming hour with `kasoro`.
 *
 * The part of the day is what separates saa mbili in the morning from saa mbili at
 * night, and it is optional: every reading is accepted bare as well.
 */
internal object SwahiliClock {
    private val hourWords = listOf("kumi na mbili", "moja", "mbili", "tatu", "nne", "tano", "sita", "saba", "nane", "tisa", "kumi", "kumi na moja", "kumi na mbili")

    /**
     * Parts of the day that fit the hour, canonical (display) form first. alfajiri owns
     * dawn, 04–06; usiku runs 19–03; asubuhi is the morning proper from seven, mchana
     * runs from noon, jioni from four, and alasiri belongs to the late afternoon only —
     * never to the top of the list, and never before three.
     *
     * A second entry is there because speakers disagree at a BOUNDARY, and each overlap
     * is named from one side only (04 looks back at 03, 06 forward at 07, 16 back at 15,
     * 19 back at 18); an hour interior to its block carries no neighbour.
     *
     * The canonical form must stay ONE word: `TrainerGoldenTests` recovers the
     * period-less reading by dropping the display's last word — so `usiku wa manane`,
     * the pack's only multi-word part, may never lead either.
     */
    fun dayParts(hours: Int): List<String> = when (hours) {
        in 0..3 -> listOf("usiku", "usiku wa manane")
        4 -> listOf("alfajiri", "usiku")
        5 -> listOf("alfajiri")
        6 -> listOf("alfajiri", "asubuhi")
        in 7..11 -> listOf("asubuhi")
        in 12..14 -> listOf("mchana")
        15 -> listOf("mchana", "jioni", "alasiri")
        16 -> listOf("jioni", "mchana", "alasiri")
        17 -> listOf("jioni", "alasiri")
        18 -> listOf("jioni")
        19 -> listOf("usiku", "jioni")
        else -> listOf("usiku")
    }

    /** The shape every pack's clock is asked in, so the registry calls one thing. */
    fun task(hours: Int, minutes: Int): ClockReading =
        ClockReading(time(hours, minutes), accepted(hours, minutes), gloss())

    /** Canonical display string, with the primary part of the day appended. */
    fun time(hours: Int, minutes: Int): String =
        cores(hours, minutes).first() + " " + dayParts(hours)[0]

    /**
     * All accepted spellings: every reading bare, and every reading with each plausible
     * part of the day. The bare forms are deliberately open across the 12-hour cycle —
     * "saa sita" is midnight and noon alike, and only naming the part closes it.
     */
    fun accepted(hours: Int, minutes: Int): List<String> =
        cores(hours, minutes)
            .flatMap { core -> listOf(core) + dayParts(hours).map { "$core $it" } }
            .distinct()

    /** The rule the reveal restates: the saa hour is the western one shifted by six. */
    fun gloss(): String = "Saa ± 6h"

    /** Every reading of the time without a part of the day, canonical first. */
    private fun cores(hours: Int, minutes: Int): List<String> {
        val saaHour = (hours + 6) % 12
        val hWord = hourWords[saaHour]
        val nextWord = hourWords[(saaHour + 1) % 12]
        val out = mutableListOf<String>()

        when {
            minutes == 0 -> out += "Saa $hWord"
            minutes == 15 -> {
                out += "Saa $hWord na robo"
                out += "Saa $hWord na dakika kumi na tano"
            }
            minutes == 30 -> out += "Saa $hWord na nusu"
            minutes < 30 -> out += "Saa $hWord na dakika ${SwahiliNumbers.cardinal(minutes.toLong())}"
            minutes == 45 -> {
                out += "Saa $nextWord kasorobo"
                out += "Saa $nextWord kasoro robo"
                out += "Saa $nextWord kasoro dakika kumi na tano"
            }
            else -> out += "Saa $nextWord kasoro dakika ${SwahiliNumbers.cardinal((60 - minutes).toLong())}"
        }
        if (minutes > 30) {
            out += "Saa $nextWord kasoro ${SwahiliNumbers.cardinal((60 - minutes).toLong())}"
        }
        // why: past the half hour the additive reading still counts on the CURRENT saa
        // hour — 23:45 is saa tano na dakika arobaini na tano, never saa sita. One
        // published course prints the coming hour here; correcting the code toward it
        // would put 720 readings an hour out.
        if (minutes >= 30) {
            out += "Saa $hWord na dakika ${SwahiliNumbers.cardinal(minutes.toLong())}"
        }
        return out.distinct()
    }
}
