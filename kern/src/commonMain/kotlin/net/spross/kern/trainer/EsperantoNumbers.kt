package net.spross.kern.trainer

/**
 * Esperanto cardinals and ordinals.
 *
 * The system is exceptionless, so this file authors nine unit words and nothing else:
 * every other reading is composed. Two spelling rules are not derivable from the numerals
 * and are therefore encoded here — the tens and hundreds WELD (`sepdek`, `naŭcent`) while
 * everything else, thousands included, stays apart, and a numeral that takes an ending
 * closes up again with a hyphen (`dudek-unua`), which the comparison pipeline deletes,
 * so the hyphen-free spelling grades exact without being emitted.
 */
internal object EsperantoNumbers {

    private val ones = listOf("", "unu", "du", "tri", "kvar", "kvin", "ses", "sep", "ok", "naŭ")

    /** Canonical reading, 0..9_999_999_999; beyond that the digits stand. */
    fun cardinal(n: Long): String = compose(n)

    /** Canonical first, then the bare zero and the x-system twins. */
    fun variants(n: Long): List<String> {
        val canonical = compose(n)
        if (canonical.any { it.isDigit() }) return listOf(canonical)
        val base = mutableListOf(canonical)
        // why: "nul" is the numeral and "nulo" the noun — a learner writes either at a bare 0.
        if (n == 0L) base += "nul"
        return spellings(base)
    }

    /** Words for 1..59 without a scale word — the clock's minute reading. */
    fun underHundred(n: Int): String = when {
        n == 0 -> ""
        n < 10 -> ones[n]
        n < 20 -> listOf("dek", ones[n - 10]).filter { it.isNotEmpty() }.joinToString(" ")
        n % 10 == 0 -> ones[n / 10] + "dek"
        else -> ones[n / 10] + "dek " + ones[n % 10]
    }

    /** The ordinal: the whole numeral hyphenated, then `-a` ("dudek-unua"). */
    fun ordinal(n: Long): String =
        if (n == 0L) "nula" else compose(n).replace(' ', '-') + "a"

    /**
     * The ordinal, with only its x-system twin beside it: the closed-up spelling a writer
     * may use instead of the hyphen normalizes to the same string, and the SPACED one is
     * what the rule against writing a derivative apart already rules out.
     */
    fun ordinalVariants(n: Long): List<String> = spellings(listOf(ordinal(n)))

    /**
     * Every reading beside its x-system twin. A keyboard without `ŭ` writes `naux`, which
     * sits two edits from `naŭ` and would grade wrong on a one-slip budget; the twin is
     * accepted and never displayed.
     */
    fun spellings(readings: List<String>): List<String> =
        readings.flatMap { listOf(it, it.replace("ŭ", "ux")) }.distinct()

    private fun compose(n: Long): String {
        if (n < 0) return n.toString()
        if (n == 0L) return "nulo"
        if (n / 1_000_000_000 > 9) return n.toString()
        val words = mutableListOf<String>()
        var rest = n
        val milliards = rest / 1_000_000_000
        rest %= 1_000_000_000
        if (milliards > 0) words += scale(milliards, "miliardo")
        val millions = rest / 1_000_000
        rest %= 1_000_000
        if (millions > 0) words += scale(millions, "miliono")
        val thousands = rest / 1000
        rest %= 1000
        // A bare thousand is "mil": it counts nothing, so no "unu" leads it.
        if (thousands > 0) {
            words += if (thousands == 1L) "mil" else underThousand(thousands.toInt()) + " mil"
        }
        if (rest > 0) words += underThousand(rest.toInt())
        return words.joinToString(" ")
    }

    /** miliono and miliardo are NOUNS: they take a numeral before them and pluralize. */
    private fun scale(count: Long, noun: String): String =
        if (count == 1L) "unu $noun" else underThousand(count.toInt()) + " ${noun}j"

    private fun underThousand(n: Int): String {
        val hundreds = n / 100
        val head = when (hundreds) {
            0 -> ""
            1 -> "cent"
            else -> ones[hundreds] + "cent"
        }
        val tail = underHundred(n % 100)
        return listOf(head, tail).filter { it.isNotEmpty() }.joinToString(" ")
    }

}
