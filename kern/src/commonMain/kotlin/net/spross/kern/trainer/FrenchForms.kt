package net.spross.kern.trainer

/**
 * French readings of the written number forms
 * (Académie française · Grevisse · BDL; the sources and the refusals are
 * `docs/number-forms.md` § French).
 *
 * Everything composes over [FrenchNumbers.variants], so the rectified spelling, the spaced
 * twin and the regional decades grade behind `moins`, `pour cent` and a fraction noun
 * exactly as they do in the plain cardinal drill. The one place the agreement changes is
 * the multiplicative: `fois` is feminine, so 21× is `vingt et une fois`.
 */
internal object FrenchForms {

    /** All six, at the drill's own reach: -ième is productive and every ordinal to 100 derives. */
    val LIMITS = FormLimits(forms = NumberForm.entries.toSet())

    fun reading(value: NumberValue): List<String> = when (value) {
        is NumberValue.Negative -> FrenchNumbers.variants(value.magnitude).map { "moins $it" }
        is NumberValue.Decimal -> decimal(value.whole, value.fractionDigits)
        is NumberValue.Percent -> FrenchNumbers.variants(value.n).map { "$it pour cent" }
        is NumberValue.Multiplicative -> FrenchNumbers.feminineVariants(value.n).map { "$it fois" }
        is NumberValue.Fraction -> fraction(value.numerator, value.denominator)
        is NumberValue.Ordinal -> ordinal(value.n)
    }

    /**
     * Digit by digit leads, with the run-together reading of the fractional part accepted
     * beside it — French says both, and `trois virgule quatorze` is what π sounds like in a
     * classroom. It is suppressed on a leading zero, where it would name a different number.
     */
    private fun decimal(whole: Long, fractionDigits: String): List<String> {
        val heads = FrenchNumbers.variants(whole)
        val tails = mutableListOf(
            fractionDigits.map { FrenchNumbers.cardinal(it.digitToInt().toLong()) }.joinToString(" "),
        )
        if (fractionDigits.length >= 2 && fractionDigits.first() != '0') {
            tails += FrenchNumbers.variants(fractionDigits.toLong())
        }
        return heads.flatMap { head -> tails.map { "$head virgule $it" } }.distinct()
    }

    /**
     * `un tiers`, `trois quarts`, `cinq douzièmes` — a masculine noun the numerator counts.
     * Thirds and quarters are suppletive; from a fifth the noun IS the ordinal, which is why
     * no second table exists for it. `tiers` already ends in -s and takes no plural mark.
     *
     * Only `demi` modifies a noun directly, so a half is the suppletive `un demi` with the
     * feminine noun `la moitié` beside it; since 1 ≤ n < d, d == 2 forces n == 1.
     */
    private fun fraction(n: Long, d: Long): List<String> {
        if (d == 2L) return listOf("un demi", "demi", "la moitié", "une demie")
        val noun = when (d) {
            3L -> "tiers"
            4L -> "quart"
            else -> ordinalOf(FrenchNumbers.cardinal(d))
        }
        val counted = if (n > 1L && !noun.endsWith("s")) noun + "s" else noun
        return FrenchNumbers.variants(n).map { "$it $counted" }
    }

    /**
     * The cardinal with -ième on its last segment, which is all a French ordinal is past
     * the first: `premier` is suppletive and `unième` only ever appears inside a compound
     * (`vingt et unième`), so a bare `unième` never grades. `second` belongs to a series of
     * exactly two and is accepted there, never derived.
     */
    private fun ordinal(n: Long): List<String> {
        if (n !in LIMITS.ordinalRange) return emptyList()
        if (n == 1L) return listOf("premier", "première")
        val derived = FrenchNumbers.variants(n).map(::ordinalOf)
        return if (n == 2L) derived + listOf("second", "seconde") else derived
    }

    /** -ième on the last segment; a hyphen counts as a segment break exactly as a space does. */
    private fun ordinalOf(reading: String): String {
        val cut = maxOf(reading.lastIndexOf(' '), reading.lastIndexOf('-'))
        return reading.substring(0, cut + 1) + stem(reading.substring(cut + 1))
    }

    /**
     * The stem -ième attaches to: the plural mark of a multiplied `vingt`/`cent` goes
     * (`quatre-vingts` → `quatre-vingtième`) while the -s of `trois` is part of the word,
     * a final mute -e goes (`quatre` → `quatrième`), and `cinq`/`neuf` shift for the sound.
     */
    private fun stem(word: String): String {
        val bare = when (word) {
            "vingts" -> "vingt"
            "cents" -> "cent"
            "cinq" -> "cinqu"
            "neuf" -> "neuv"
            else -> word
        }
        return bare.removeSuffix("e") + "ième"
    }
}
