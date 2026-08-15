package net.spross.kern.trainer

/**
 * Esperanto readings of the written number forms (PMEG «Nombraj vortoj», PIV).
 *
 * Nothing here departs from the drill's own reach: the fraction suffix `-on-` and the
 * ordinal `-a` are productive over every numeral, so no cap is authored and the
 * defaults stand — which is itself the claim this pack makes about the language.
 *
 * Every arm composes over [EsperantoNumbers.variants], so the x-system spelling grades
 * behind `minus`, `procentoj` and `fojojn` exactly as it does in the plain drill.
 */
internal object EsperantoForms {

    val LIMITS = FormLimits(forms = NumberForm.entries.toSet())

    fun reading(value: NumberValue): List<String> = when (value) {
        is NumberValue.Negative -> negative(value.magnitude)
        is NumberValue.Decimal -> decimal(value.whole, value.fractionDigits)
        is NumberValue.Percent -> percent(value.n)
        is NumberValue.Multiplicative -> multiplicative(value.n)
        is NumberValue.Fraction -> fraction(value.numerator, value.denominator)
        is NumberValue.Ordinal -> EsperantoNumbers.ordinalVariants(value.n)
    }

    private fun negative(magnitude: Long): List<String> =
        EsperantoNumbers.variants(magnitude).map { "minus $it" }

    /**
     * Digit by digit after `komo`, with the run-together reading of the fractional part
     * accepted beside it. It is suppressed on a leading zero, where it would name a
     * different number.
     */
    private fun decimal(whole: Long, fractionDigits: String): List<String> {
        val head = EsperantoNumbers.cardinal(whole)
        val tails = mutableListOf(
            fractionDigits
                .map { EsperantoNumbers.cardinal(it.digitToInt().toLong()) }
                .joinToString(" "),
        )
        if (fractionDigits.length >= 2 && fractionDigits.first() != '0') {
            tails += EsperantoNumbers.cardinal(fractionDigits.toLong())
        }
        return EsperantoNumbers.spellings(tails.map { "$head komo $it" })
    }

    /** The noun is counted, so it pluralizes; `elcento` is the same word built from Esperanto roots. */
    private fun percent(n: Long): List<String> {
        val suffix = if (n == 1L) listOf("procento", "elcento") else listOf("procentoj", "elcentoj")
        return EsperantoNumbers.variants(n).flatMap { count -> suffix.map { "$count $it" } }
    }

    /**
     * Frequency reads as the adverbial accusative `tri fojojn`, the one form every numeral
     * takes; the `-foje` adverb is accepted wherever the numeral is a single word, which is
     * as far as it welds ("dudek unu fojojn" has no adverb).
     */
    private fun multiplicative(n: Long): List<String> {
        val noun = if (n == 1L) "fojon" else "fojojn"
        return EsperantoNumbers.variants(n).flatMap { count ->
            if (' ' in count) listOf("$count $noun") else listOf("$count $noun", "${count}foje")
        }
    }

    /**
     * `-on-` nouns: `duono`, `kvarono`, `du trionoj`. The numerator is a bare numeral and
     * only the noun pluralizes; at one the numeral is dropped as often as it is said, so
     * both grade and the bare noun leads.
     */
    private fun fraction(n: Long, d: Long): List<String> {
        val readings = denominators(d).flatMap { noun ->
            if (n == 1L) listOf(noun, "unu $noun")
            else listOf("${EsperantoNumbers.cardinal(n)} ${noun}j")
        }
        return EsperantoNumbers.spellings(readings)
    }

    /** The denominator noun closes its numeral up exactly as the ordinal does. */
    private fun denominators(d: Long): List<String> =
        listOf(EsperantoNumbers.cardinal(d).replace(' ', '-') + "ono")
}
