package net.spross.kern.trainer

/**
 * German conversational clock times, ported from the prototype `ClockTrainer.tsx`:
 * Hochdeutsch standard plus regional (Oberfranken) variants
 * ("Viertel sieben", "Dreiviertel sieben", "punkt sechs").
 */
internal object GermanClock {
    private val hourWords = listOf("zwölf", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf")

    /** The counts a relative reading uses; only these take an optional "Minuten". */
    private val MINUTE_COUNTS = setOf("fünf", "zehn", "zwanzig", "fünfundzwanzig")

    private data class Conversational(val standard: String, val regional: String)

    /**
     * Directly before "Uhr" German apocopates "eins" to "ein" ("ein Uhr fünf");
     * the bare hour word stays "eins" ("punkt eins", "um eins", "halb eins").
     */
    private fun beforeUhr(hourWord: String) = if (hourWord == "eins") "ein" else hourWord

    /** Non-round minutes fall back to a plain reading ("drei Uhr siebzehn"). */
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
            // why: the prompt already IS "21:17" — a reveal that answers it with
            // digits teaches nothing, and the spelled minute was graded wrong.
            else -> same("${beforeUhr(hWord)} Uhr ${GermanNumbers.cardinal(minutes.toLong())}")
        }
    }

    private fun same(phrase: String) = Conversational(phrase, phrase)

    fun task(hours: Int, minutes: Int): ClockReading {
        val c = conversational(hours, minutes)
        val accepted = mutableListOf(c.standard)
        if (c.regional != c.standard) accepted += c.regional
        // Colloquial "um zehn" reads a full hour the same as "zehn Uhr" / "punkt zehn".
        if (minutes == 0 && c.standard.endsWith("Uhr")) accepted += "um ${hourWords[hours % 12]}"
        for (reading in twentyFourHour(hours, minutes)) accepted.addUnlessPresent(reading)
        if (minutes == 0) {
            val hourWord = hourWords[hours % 12]
            // why: at noon and midnight the standard reading is a NAME, so the gate
            // above skipped the colloquial full-hour forms at exactly the two hours
            // where a speaker says them out loud to disambiguate.
            for (form in listOf("${beforeUhr(hourWord)} Uhr", "punkt $hourWord", "um $hourWord")) {
                accepted.addUnlessPresent(form)
            }
            accepted.addUnlessPresent("punkt ${beforeUhr(hourWord)} Uhr")
            for (part in dayParts(hours)) accepted.addUnlessPresent("${beforeUhr(hourWord)} Uhr $part")
        }
        // The half hour is also counted from ten out on either side.
        val nextWord = hourWords[(hours % 12 + 1) % 12]
        if (minutes == 20) accepted.addUnlessPresent("zehn vor halb $nextWord")
        if (minutes == 40) accepted.addUnlessPresent("zehn nach halb $nextWord")
        for (form in accepted.toList()) accepted.addUnlessPresent(withMinuten(form) ?: continue)
        // The reveal names the other ways to SAY the hour ("Dreiviertel sieben" against
        // "Viertel vor sieben"), never the same reading with a word added or dropped.
        val picks = ClockGloss.alternatives(c.standard, accepted.drop(1), limit = 3)
        val gloss = if (picks.isEmpty()) null else "auch: ${picks.joinToString(" oder ")}"
        return ClockReading(c.standard, accepted, gloss)
    }

    private fun MutableList<String>.addUnlessPresent(reading: String) {
        if (reading !in this) this += reading
    }

    /** "fünf nach sechs" also reads "fünf Minuten nach sechs"; "Viertel" never does. */
    private fun withMinuten(reading: String): String? {
        val words = reading.split(" ")
        if (words.size < 3 || words[0] !in MINUTE_COUNTS || words[1] !in setOf("nach", "vor")) return null
        return words[0] + " Minuten " + words.drop(1).joinToString(" ")
    }

    /**
     * How the hour is placed in the day, at the full hour. Deliberately generous where
     * speakers disagree — the sets for an hour and the hour twelve on stay disjoint
     * (nothing before noon is ever abends, nothing after is ever morgens), so widening
     * one never lets it answer the other. "früh" is accepted, never taught.
     */
    private fun dayParts(hours: Int): List<String> = when (hours) {
        0, 1 -> listOf("nachts")
        2, 3 -> listOf("nachts", "morgens")
        4, 5 -> listOf("früh", "morgens", "nachts")
        6, 7 -> listOf("morgens", "früh")
        8 -> listOf("morgens", "früh", "vormittags")
        9 -> listOf("morgens", "vormittags")
        10, 11 -> listOf("vormittags", "morgens")
        12 -> listOf("mittags")
        13, 14 -> listOf("nachmittags", "mittags")
        15, 16 -> listOf("nachmittags")
        17 -> listOf("nachmittags", "abends")
        in 18..20 -> listOf("abends")
        21, 22 -> listOf("abends", "nachts")
        else -> listOf("nachts", "abends")
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
