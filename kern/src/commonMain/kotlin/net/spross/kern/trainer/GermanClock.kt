package net.spross.kern.trainer

/**
 * German conversational clock times, ported from the prototype `ClockTrainer.tsx`:
 * Hochdeutsch standard plus regional (Oberfranken) variants
 * ("Viertel sieben", "Dreiviertel sieben", "punkt sechs").
 */
internal object GermanClock {
    private val hourWords = listOf("zwölf", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf")

    private data class Conversational(val standard: String, val regional: String)

    /**
     * Directly before "Uhr" German apocopates "eins" to "ein" ("ein Uhr fünf");
     * the bare hour word stays "eins" ("punkt eins", "um eins", "halb eins").
     */
    private fun beforeUhr(hourWord: String) = if (hourWord == "eins") "ein" else hourWord

    /** Non-round minutes fall back to a digital reading ("drei Uhr 17"). */
    private fun conversational(hours: Int, minutes: Int): Conversational {
        val h12 = hours % 12
        val nextH = (h12 + 1) % 12
        val hWord = hourWords[h12]
        val nextWord = hourWords[nextH]

        if (hours == 0 && minutes == 0) return Conversational("Mitternacht", "Mitternacht")
        if (hours == 12 && minutes == 0) return Conversational("Mittag", "Mittag")

        return when (minutes) {
            0 -> Conversational("${beforeUhr(hWord)} Uhr", "punkt $hWord")
            5 -> same("fünf nach $hWord")
            10 -> same("zehn nach $hWord")
            15 -> Conversational("Viertel nach $hWord", "Viertel $nextWord")
            20 -> same("zwanzig nach $hWord")
            25 -> same("fünf vor halb $nextWord")
            30 -> same("halb $nextWord")
            35 -> same("fünf nach halb $nextWord")
            40 -> same("zwanzig vor $nextWord")
            45 -> Conversational("Viertel vor $nextWord", "Dreiviertel $nextWord")
            50 -> same("zehn vor $nextWord")
            55 -> same("fünf vor $nextWord")
            else -> same("${beforeUhr(hWord)} Uhr $minutes")
        }
    }

    private fun same(phrase: String) = Conversational(phrase, phrase)

    fun task(hours: Int, minutes: Int): ClockReading {
        val c = conversational(hours, minutes)
        val accepted = mutableListOf(c.standard)
        if (c.regional != c.standard) accepted += c.regional
        // Colloquial "um zehn" reads a full hour the same as "zehn Uhr" / "punkt zehn".
        if (minutes == 0 && c.standard.endsWith("Uhr")) accepted += "um ${hourWords[hours % 12]}"
        for (reading in twentyFourHour(hours, minutes)) {
            if (reading !in accepted) accepted += reading
        }
        val alternatives = accepted.drop(1)
        val gloss = if (alternatives.isEmpty()) null else "auch: ${alternatives.joinToString(" oder ")}"
        return ClockReading(c.standard, accepted, gloss)
    }

    /**
     * Formal 24-hour readings, accepted alongside the 12-hour display:
     * "achtzehn Uhr", "achtzehn Uhr fünfunddreißig"; 0:00 reads "null Uhr"
     * and, equivalently, "vierundzwanzig Uhr".
     */
    private fun twentyFourHour(hours: Int, minutes: Int): List<String> {
        val minuteSuffix = if (minutes == 0) "" else " " + GermanNumbers.cardinal(minutes.toLong())
        val readings = mutableListOf(beforeUhr(GermanNumbers.cardinal(hours.toLong())) + " Uhr" + minuteSuffix)
        if (hours == 0 && minutes == 0) readings += "vierundzwanzig Uhr"
        return readings
    }
}
