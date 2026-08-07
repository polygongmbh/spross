package net.spross.kern.trainer

/**
 * German readings of the written number forms (Duden · DWDS · Wikipedia "Zahlwort").
 *
 * Every form is attested across the whole drilled range, so nothing is excluded.
 * Two range edges are encoded rather than implemented speculatively:
 * the fraction noun's -tel → -stel switch starts at denominator 20, unreachable at d ≤ 12,
 * and the ordinal -te → -ste switch is governed by the LAST cardinal component —
 * within 1..100 that is exactly "≤19 → -te, ≥20 → -ste", but 101. is "hunderterste"
 * again, so widening [LIMITS] means rewriting the rule, not extending it.
 */
internal object GermanForms {

    val LIMITS = FormLimits(
        forms = NumberForm.entries.toSet(),
        fractionDenominators = (2..12).toSet(),
        ordinalRange = 1L..100L,
    )

    fun reading(value: NumberValue): List<String> = when (value) {
        is NumberValue.Negative -> listOf("minus " + GermanNumbers.cardinal(value.magnitude))
        is NumberValue.Decimal -> decimal(value.whole, value.fractionDigits)
        is NumberValue.Percent -> bareStems(attributive(value.n)).map { "$it Prozent" }
        is NumberValue.Multiplicative -> multiplicative(value.n)
        is NumberValue.Fraction -> fraction(value.numerator, value.denominator)
        is NumberValue.Ordinal -> ordinal(value.n)
    }

    /**
     * The attributive stem. [GermanNumbers.cardinal] reads 1 as "eins", which stands alone
     * only where no noun follows — "eins Komma fünf". Before Prozent, -mal and every
     * fraction noun the numeral is "ein", so those must never take the plain cardinal.
     */
    private fun attributive(n: Long): String =
        if (n == 1L) "ein" else GermanNumbers.cardinal(n)

    /**
     * "einhundert…"/"eintausend…" is what the generator writes and is not wrong, but bare
     * "hundert Prozent"/"hundertmal"/"hundertste" is what people say — and it is unreachable
     * from the cardinal, so it is added here.
     */
    private fun bareStems(stem: String): List<String> =
        if (stem.startsWith("einhundert") || stem.startsWith("eintausend")) {
            listOf(stem, stem.removePrefix("ein"))
        } else {
            listOf(stem)
        }

    private fun decimal(whole: Long, fractionDigits: String): List<String> {
        val head = GermanNumbers.cardinal(whole) + " Komma "
        val digitwise = fractionDigits
            .map { GermanNumbers.cardinal(it.digitToInt().toLong()) }
            .joinToString(" ")
        val readings = mutableListOf(head + digitwise)
        // why: the colloquial run-together reading is only the SAME number when no leading
        // zero is swallowed — "null Komma fünf" ≠ "null Komma null fünf".
        if (fractionDigits.length >= 2 && fractionDigits.first() != '0') {
            readings += head + GermanNumbers.cardinal(fractionDigits.toLong())
        }
        return readings.distinct()
    }

    /**
     * One lowercase word ("dreimal") — Duden classes these as adverbs written together.
     * The separated capitalized spelling is Duden-sanctioned where both words are
     * stressed, so it grades too.
     */
    private fun multiplicative(n: Long): List<String> =
        bareStems(attributive(n)).flatMap { listOf(it + "mal", "$it Mal") }

    /**
     * Numerator + denominator noun, the noun being the ordinal stem + "el"
     * (dritt+el, zwölft+el). All are neuter nouns whose plural is identical to the
     * singular, so the numerator never reshapes them. d == 2 is suppletive — "Zweitel"
     * is veraltet — and since 1 ≤ n < d, d == 2 forces n == 1, so no plural of halb exists.
     */
    private fun fraction(n: Long, d: Long): List<String> {
        val numerator = attributive(n)
        if (d == 2L) return listOf("$numerator halb", "${numerator}halb", "die Hälfte", "eine Hälfte")
        return ordinalStems(d).map { "$numerator " + it.replaceFirstChar(Char::uppercaseChar) + "el" }
    }

    /**
     * The ordinal stem, before the adjective ending. 8 keeps its single t (acht+t would
     * double it) and 7 leads with "siebt": Duden's headword is "siebte", DWDS heads
     * "siebente" and marks the other a Nebenform, so both grade and neither source decides.
     */
    private fun ordinalStems(n: Long): List<String> = when (n) {
        1L -> listOf("erst")
        3L -> listOf("dritt")
        7L -> listOf("siebt", "siebent")
        8L -> listOf("acht")
        else -> bareStems(GermanNumbers.cardinal(n)).map { it + if (n < 20) "t" else "st" }
    }

    /**
     * Canonical is the bare "-e" form; the other three adjective endings grade —
     * Duden's own headword line reads "siebte, siebter, siebtes" and Lingolia's
     * declension table adds "-en". Lowercase throughout, like any adjective.
     */
    private val ORDINAL_ENDINGS = listOf("e", "er", "en", "es")

    private fun ordinal(n: Long): List<String> =
        ordinalStems(n).flatMap { stem -> ORDINAL_ENDINGS.map { stem + it } }
}
