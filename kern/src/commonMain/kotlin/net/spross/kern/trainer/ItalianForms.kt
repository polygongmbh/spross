package net.spross.kern.trainer

/**
 * Italian readings of the written number forms
 * (Treccani, *La grammatica italiana* § numerali · Accademia della Crusca).
 *
 * Italian departs from the drill's defaults nowhere: `-esimo` is productive from eleven up,
 * so an ordinal is available at every value the ladder reaches, and the fraction nouns are
 * those same ordinals, so the denominators run the full 2–12.
 *
 * What the forms DO need is the noun a bare cardinal never has. `uno` agrees with it
 * ("una volta"), apocopates before it ("ventun volte") and stays whole in front of a
 * preposition ("uno per cento") — three readings of one numeral, so each arm below spells
 * its own instead of taking [ItalianNumbers.cardinal] as it stands.
 */
internal object ItalianForms {

    val LIMITS = FormLimits(forms = NumberForm.entries.toSet())

    fun reading(value: NumberValue): List<String> = when (value) {
        is NumberValue.Negative -> listOf("meno " + ItalianNumbers.cardinal(value.magnitude))
        is NumberValue.Decimal -> decimal(value.whole, value.fractionDigits)
        is NumberValue.Percent -> listOf(ItalianNumbers.cardinal(value.n) + " per cento")
        is NumberValue.Multiplicative -> multiplicative(value.n)
        is NumberValue.Fraction -> fraction(value.numerator, value.denominator)
        is NumberValue.Ordinal -> ordinal(value.n)
    }

    /**
     * Digit by digit leads, with the run-together reading of the fractional part accepted
     * beside it — Italian says both, and the second is what a broadcast reads off a
     * percentage ("tre virgola quarantacinque per cento"). It is suppressed on a leading
     * zero, where it would name a different number.
     *
     * `virgola` is the only mark: the comma is the separator Italian writes, and `punto`
     * names the thousands dot instead, so reading it here would say the wrong number.
     */
    private fun decimal(whole: Long, fractionDigits: String): List<String> {
        val head = ItalianNumbers.cardinal(whole) + " virgola "
        val readings = mutableListOf(
            head + fractionDigits
                .map { ItalianNumbers.cardinal(it.digitToInt().toLong()) }
                .joinToString(" "),
        )
        if (fractionDigits.length >= 2 && fractionDigits.first() != '0') {
            readings += head + ItalianNumbers.cardinal(fractionDigits.toLong())
        }
        return readings
    }

    /**
     * Frequency, and therefore FEMININE: volta is a feminine noun, so 1 is `una volta`.
     * From 21 up the numeral stands before that noun and drops its final vowel —
     * `ventun volte` leads, with the unapocopated `ventuno volte` accepted beside it,
     * because both are current and only the apocope is a rule a learner has to be taught.
     * The quantity multiplicatives (doppio, triplo) are a different family and stay out.
     */
    private fun multiplicative(n: Long): List<String> {
        if (n == 1L) return listOf("una volta")
        val cardinal = ItalianNumbers.cardinal(n)
        if (!cardinal.endsWith("uno")) return listOf("$cardinal volte")
        return listOf(cardinal.dropLast(1) + " volte", "$cardinal volte")
    }

    /**
     * The numerator apocopates to `un` at one, and the denominator is the ordinal used as a
     * noun, pluralized by the numerator ("due terzi", "cinque dodicesimi"). Halves are
     * suppletive: `mezzo` is the word, and `la metà` names the thing rather than counting
     * it. Since 1 ≤ n < d, d == 2 forces n == 1, so no plural of mezzo exists to author.
     */
    private fun fraction(n: Long, d: Long): List<String> {
        if (d == 2L) return listOf("un mezzo", "mezzo", "la metà", "metà")
        val noun = ORDINALS[d] ?: suffixed(d)
        if (n == 1L) return listOf("un $noun")
        return listOf(ItalianNumbers.cardinal(n) + " " + noun.dropLast(1) + "i")
    }

    /**
     * The first ten are their own words; from eleven the cardinal loses its final vowel and
     * takes `-esimo`, which is productive to any value the drill draws. Two seams the rule
     * has to know: a compound ending in `tré` keeps the e it needs before the suffix
     * (ventitreesimo), and one ending in `sei` keeps the whole diphthong (ventiseiesimo).
     *
     * The feminine grades beside the masculine — a bare prompt names no noun to agree with,
     * so neither gender can be the wrong one.
     */
    private fun ordinal(n: Long): List<String> {
        val masculine = ORDINALS[n] ?: suffixed(n)
        return listOf(masculine, masculine.dropLast(1) + "a")
    }

    private fun suffixed(n: Long): String {
        val cardinal = ItalianNumbers.cardinal(n)
        return when {
            cardinal.endsWith("tré") -> cardinal.dropLast(1) + "eesimo"
            cardinal.endsWith("sei") -> cardinal + "esimo"
            else -> cardinal.dropLast(1) + "esimo"
        }
    }

    /** The suppletive ten — nothing about primo/secondo/terzo follows from the cardinal. */
    private val ORDINALS = mapOf(
        1L to "primo", 2L to "secondo", 3L to "terzo", 4L to "quarto", 5L to "quinto",
        6L to "sesto", 7L to "settimo", 8L to "ottavo", 9L to "nono", 10L to "decimo",
    )
}
