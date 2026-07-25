package net.spross.kern.trainer

/**
 * Swahili saa-system clock times, ported from the prototype `ClockTrainer.tsx`.
 * Saa hour = western hour shifted by 6; day periods asubuhi/mchana/jioni/usiku;
 * "na nusu" for half past, "kasoro dakika ..." counting down past the half hour.
 */
internal object SwahiliClock {
    private val hourWords = listOf("kumi na mbili", "moja", "mbili", "tatu", "nne", "tano", "sita", "saba", "nane", "tisa", "kumi", "kumi na moja", "kumi na mbili")

    const val GLOSS = "Saa ± 6h · asubuhi/mchana/jioni/usiku optional"

    /**
     * Day periods that fit the hour, canonical (display) form first. mchana
     * = afternoon starts at noon, not 10; the mchana↔jioni boundary isn't
     * fixed, so both are accepted across the late-afternoon overlap. The
     * period is optional (see [accepted]), so these only widen what counts.
     */
    private fun periods(hours: Int): List<String> = when (hours) {
        in 4..11 -> listOf("asubuhi")
        in 12..14 -> listOf("mchana")
        15 -> listOf("mchana", "jioni")
        in 16..17 -> listOf("jioni", "mchana")
        18 -> listOf("jioni")
        else -> listOf("usiku")
    }

    /**
     * Canonical display string, with the primary day period appended.
     * Any minute is spelled out (`SwahiliNumbers.cardinal`).
     */
    fun time(hours: Int, minutes: Int): String =
        core(hours, minutes) + " " + periods(hours)[0]

    /**
     * All accepted spellings: the bare reading (period optional) plus one per
     * plausible day period. First is the period-less form.
     */
    fun accepted(hours: Int, minutes: Int): List<String> {
        val base = core(hours, minutes)
        return listOf(base) + periods(hours).map { "$base $it" }
    }

    /** The time reading without any day period. */
    private fun core(hours: Int, minutes: Int): String {
        val saaHour = (hours + 6) % 12
        val nextSaaHour = (saaHour + 1) % 12
        val hWord = hourWords[saaHour]
        val nextWord = hourWords[nextSaaHour]

        if (minutes == 0) return "Saa $hWord"
        if (minutes == 30) return "Saa $hWord na nusu"
        if (minutes < 30) return "Saa $hWord na dakika ${SwahiliNumbers.cardinal(minutes.toLong())}"
        return "Saa $nextWord kasoro dakika ${SwahiliNumbers.cardinal((60 - minutes).toLong())}"
    }
}
