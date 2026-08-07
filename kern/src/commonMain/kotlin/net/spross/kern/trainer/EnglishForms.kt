package net.spross.kern.trainer

/**
 * English readings of the written number forms (Wikipedia "English numerals" ·
 * Wiktionary "per cent").
 *
 * Everything routes through [EnglishNumbers.spellings], which adds the spaced twin of
 * every hyphenated compound: the comparison pipeline DELETES hyphens rather than turning
 * them into spaces, so "twenty-first" and a learner's "twenty first" are two different
 * strings and only the generated pair covers both.
 *
 * English gets no run-together decimal reading, unlike German and Spanish:
 * "three point forty-five" is not standard, so that asymmetry stays per pack.
 */
internal object EnglishForms {

    val LIMITS = FormLimits(
        forms = NumberForm.entries.toSet(),
        fractionDenominators = (2..12).toSet(),
        ordinalRange = 1L..100L,
    )

    fun reading(value: NumberValue): List<String> = EnglishNumbers.spellings(
        when (value) {
            is NumberValue.Negative -> listOf(
                "minus " + EnglishNumbers.cardinal(value.magnitude),
                "negative " + EnglishNumbers.cardinal(value.magnitude),
            )
            is NumberValue.Decimal -> decimal(value.whole, value.fractionDigits)
            is NumberValue.Percent ->
                hundredVariants(EnglishNumbers.cardinal(value.n))
                    .flatMap { listOf("$it percent", "$it per cent") }
            is NumberValue.Multiplicative -> multiplicative(value.n)
            is NumberValue.Fraction -> fraction(value.numerator, value.denominator)
            is NumberValue.Ordinal -> hundredVariants(ordinal(value.n))
        },
    )

    /**
     * "one hundred" also reads "a hundred" and bare "hundred" — idiomatic, and unreachable
     * from the cardinal generator, which always writes the "one".
     */
    private fun hundredVariants(reading: String): List<String> =
        if (reading.startsWith("one hundred")) {
            val bare = reading.removePrefix("one ")
            listOf(reading, "a $bare", bare)
        } else {
            listOf(reading)
        }

    /**
     * "a cardinal number, followed by 'point', and then by the digits of the fractional
     * part" — read ONE AT A TIME, never run together. A zero is canonically "zero" and
     * accepts "oh"; a whole part of zero additionally accepts Commonwealth "nought" and
     * being dropped entirely ("point five").
     */
    private fun decimal(whole: Long, fractionDigits: String): List<String> {
        val heads =
            if (whole == 0L) listOf("zero", "nought", "") else listOf(EnglishNumbers.cardinal(whole))
        var tails = listOf("")
        for (digit in fractionDigits) {
            val words = if (digit == '0') {
                listOf("zero", "oh")
            } else {
                listOf(EnglishNumbers.cardinal(digit.digitToInt().toLong()))
            }
            tails = tails.flatMap { tail -> words.map { if (tail.isEmpty()) it else "$tail $it" } }
        }
        return heads.flatMap { head ->
            tails.map { if (head.isEmpty()) "point $it" else "$head point $it" }
        }.distinct()
    }

    /** "thrice" is largely obsolete by its own source: accepted, never canonical. */
    private fun multiplicative(n: Long): List<String> = when (n) {
        1L -> listOf("once", "one time")
        2L -> listOf("twice", "two times")
        3L -> listOf("three times", "thrice")
        else -> hundredVariants(EnglishNumbers.cardinal(n)).map { "$it times" }
    }

    /**
     * Numerator + denominator, the denominator being the ordinal ("two thirds") with two
     * suppletions: "half" for 2, and "quarter" for 4 with "fourth" alongside.
     * The plural is a bare -s — "halves" is unreachable, because 1 ≤ n < d makes d == 2
     * force n == 1, and authoring dead content only gives the vocabulary sweep work.
     */
    private fun fraction(n: Long, d: Long): List<String> {
        val numerators = if (n == 1L) listOf("one", "a") else listOf(EnglishNumbers.cardinal(n))
        val denominators = denominatorWords(d).map { if (n > 1L) it + "s" else it }
        val readings = numerators.flatMap { num -> denominators.map { "$num $it" } }
        // "half a kilo", "a quarter past" — these two stand alone as nouns, the rest do not.
        val bare = if (n == 1L && (d == 2L || d == 4L)) listOf(denominators.first()) else emptyList()
        // why: spellings() only goes hyphen → space, and a fraction's canonical form is
        // spaced, so the hyphenated spelling has to be written the other way round here.
        return readings + readings.map { it.replace(' ', '-') } + bare
    }

    private fun denominatorWords(d: Long): List<String> = when (d) {
        2L -> listOf("half")
        4L -> listOf("quarter", "fourth")
        else -> listOf(ordinal(d))
    }

    /**
     * Six of the twelve base ordinals are stem changes and the whole -y family is -ieth
     * (forty → fortieth), so the map is authored rather than suffixed. Only the LAST
     * segment changes, which gives every compound free: "twenty-one" → "twenty-first".
     */
    private val ORDINALS = mapOf(
        "one" to "first", "two" to "second", "three" to "third", "four" to "fourth",
        "five" to "fifth", "six" to "sixth", "seven" to "seventh", "eight" to "eighth",
        "nine" to "ninth", "ten" to "tenth", "eleven" to "eleventh", "twelve" to "twelfth",
        "thirteen" to "thirteenth", "fourteen" to "fourteenth", "fifteen" to "fifteenth",
        "sixteen" to "sixteenth", "seventeen" to "seventeenth", "eighteen" to "eighteenth",
        "nineteen" to "nineteenth", "twenty" to "twentieth", "thirty" to "thirtieth",
        "forty" to "fortieth", "fifty" to "fiftieth", "sixty" to "sixtieth",
        "seventy" to "seventieth", "eighty" to "eightieth", "ninety" to "ninetieth",
        "hundred" to "hundredth", "thousand" to "thousandth",
    )

    private fun ordinal(n: Long): String {
        val cardinal = EnglishNumbers.cardinal(n)
        val cut = cardinal.indexOfLast { it == '-' || it == ' ' } + 1
        val last = cardinal.substring(cut)
        return cardinal.substring(0, cut) + (ORDINALS[last] ?: (last + "th"))
    }
}
