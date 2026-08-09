package net.spross.kern.trainer

/**
 * Swahili readings of the written number forms
 * (Almasi et al., *Swahili Grammar for Introductory and Intermediate Levels*, ch. 19 ·
 * Tanzania Institute of Education, Std 4/5 Hisabati · NYSED maths glossaries).
 *
 * Two exclusions, both structural rather than gaps in the research:
 *
 * **No ordinals.** A Swahili ordinal is `-a kwanza`, and that leading dash is a required
 * concord slot the counted noun fills — mwanafunzi WA kwanza, kitabu CHA pili, duka LA
 * tatu. A bare `20.` supplies no noun, so every prefix would be an invention shown to the
 * learner as fact; that every published source cites ordinals with the slot still empty is
 * the lexicographers saying the same thing. Ordinals arrive with a noun-bearing frame or
 * not at all (`docs/backlog.md`: the numeral-side agreement field).
 *
 * **Denominators stop at 4.** nusu, theluthi and robo are the everyday words; past them the
 * sources give three mutually incompatible systems (Almasi's `sehemu … ya/za …` periphrasis,
 * a full Arabic unit series, and `n kwa d`), and several of the Arabic words double as
 * money or tax terms in modern use. Grading one series right would teach the others wrong.
 *
 * Every reading composes over [SwahiliNumbers.acceptedVariants], never over `cardinal`
 * alone, so the "na"-less spelling speakers routinely use grades behind hasi/asilimia/mara
 * exactly as it does in the plain drill.
 */
internal object SwahiliForms {

    val LIMITS = FormLimits(
        forms = setOf(
            NumberForm.Negative,
            NumberForm.Decimal,
            NumberForm.Percent,
            NumberForm.Multiplicative,
            NumberForm.Fraction,
        ),
        fractionDenominators = setOf(2, 3, 4),
        ordinalRange = LongRange.EMPTY,
    )

    fun reading(value: NumberValue): List<String> = when (value) {
        is NumberValue.Negative -> negative(value.magnitude)
        is NumberValue.Decimal -> decimal(value.whole, value.fractionDigits)
        is NumberValue.Percent -> percent(value.n)
        is NumberValue.Multiplicative -> multiplicative(value.n)
        is NumberValue.Fraction -> fraction(value.numerator, value.denominator)
        is NumberValue.Ordinal -> emptyList()
    }

    /**
     * PROVISIONAL, and the weakest entry in this pack. hasi is an invariable adjective, so
     * nothing blocks it before the numeral, and every corpus instance of a spelled-out
     * negative VALUE puts it first ("Jumla ya namba mbili ni hasi kumi na nne") — but the
     * only such corpus is a translation of unverified provenance. The mirror order grades so
     * a learner applying the ordinary noun-adjective rule is accepted; kasoro does not,
     * because it is subtractive "less" (saa tatu kasorobo) and the glossaries map "minus"
     * to kutoa, i.e. to the operation. The English loan "minus" is heard in East African
     * maths speech and grades, but no source cites it, so it is never the reading shown.
     */
    private fun negative(magnitude: Long): List<String> =
        SwahiliNumbers.acceptedVariants(magnitude)
            .flatMap { listOf("hasi $it", "$it hasi", "minus $it") }

    /**
     * Whole part as a full compound cardinal, then the fraction digits ONE AT A TIME —
     * each read through [SwahiliNumbers.cardinal] so a zero comes out "sifuri" rather than
     * the empty string the digit table holds. nukta is the mark; pointi is the English loan
     * Almasi records as widely used today, so it grades but is never shown.
     * desimali stays out: it is the noun for a decimal number, not the spoken mark.
     */
    private fun decimal(whole: Long, fractionDigits: String): List<String> {
        val digitwise = fractionDigits
            .map { SwahiliNumbers.cardinal(it.digitToInt().toLong()) }
            .joinToString(" ")
        return SwahiliNumbers.acceptedVariants(whole).flatMap { head ->
            listOf("$head nukta $digitwise", "$head pointi $digitwise")
        }
    }

    /**
     * asilimia PRECEDES the number — Almasi notes the word order is the reverse of English,
     * and the etymology (asili ya mia) agrees: the head noun leads. The equally standard
     * post-posed "kwa mia" grades beside it.
     */
    private fun percent(n: Long): List<String> =
        SwahiliNumbers.acceptedVariants(n).flatMap { listOf("asilimia $it", "$it kwa mia") }

    /**
     * mara + the cardinal, the adverb-of-frequency construction. No clash with the ×
     * operator: primary maths reads 2 × 1 as "mbili kuzidisha kwa moja", so "mara tatu"
     * can only mean three times. maradufu ("double") grades for 2 alone.
     */
    private fun multiplicative(n: Long): List<String> {
        val readings = SwahiliNumbers.acceptedVariants(n).map { "mara $it" }.toMutableList()
        if (n == 2L) readings += "maradufu"
        return readings
    }

    /**
     * The unit fraction is the bare Arabic-derived noun and a numerator above 1 FOLLOWS it
     * as a plain cardinal: "theluthi mbili", "robo tatu". With d ≤ 4 that is exactly five
     * reduced values, every one directly attested. The explicit "moja" and Almasi's sehemu
     * periphrasis grade for the thirds and quarters; he reserves the periphrasis for the
     * denominators that have no word of their own, so it is never shown.
     */
    private fun fraction(n: Long, d: Long): List<String> {
        val words = FRACTION_NOUNS[d.toInt()] ?: return emptyList()
        val readings = words
            .map { if (n == 1L) it else "$it ${SwahiliNumbers.cardinal(n)}" }
            .toMutableList()
        if (d == 2L) return readings
        if (n == 1L) readings += words.map { "$it moja" }
        val denominator = SwahiliNumbers.cardinal(d)
        readings += if (n == 1L) {
            "sehemu moja ya $denominator"
        } else {
            "sehemu ${SwahiliNumbers.cardinal(n)} za $denominator"
        }
        return readings
    }

    /** Almasi spells the third theluthi; the Kansas materials list thuluthi beside it. */
    private val FRACTION_NOUNS = mapOf(
        2 to listOf("nusu"),
        3 to listOf("theluthi", "thuluthi"),
        4 to listOf("robo"),
    )
}
