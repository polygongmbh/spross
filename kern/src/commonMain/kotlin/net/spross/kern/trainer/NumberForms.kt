package net.spross.kern.trainer

/**
 * The ways a number can be asked beyond the bare cardinal.
 *
 * Declaration order is LADDER order: the order the forms unlock on the Forms drill,
 * and the order a language's own set is walked when a rung offers nothing it can read.
 * All of this is `internal` on purpose — none of it belongs in the ObjC header,
 * because the app only ever sees the rendered [TrainerTask].
 */
internal enum class NumberForm {
    Negative, Decimal, Percent, Multiplicative, Fraction, Ordinal;

    /** Stable identifier for the app to localize — the [ReferenceSection.key] pattern. */
    val key: String get() = name.lowercase()
}

/** One drawn value, in the shape its reading needs — never a pre-rendered string. */
internal sealed interface NumberValue {
    data class Negative(val magnitude: Long) : NumberValue

    /**
     * [fractionDigits] is a DIGIT STRING, not a number: 3.40 must differ from 3.4
     * (a different reading), and a leading zero must survive — "null Komma null fünf"
     * and "null Komma fünf" are two different values.
     */
    data class Decimal(val whole: Long, val fractionDigits: String) : NumberValue

    data class Percent(val n: Long) : NumberValue

    data class Multiplicative(val n: Long) : NumberValue

    /** Always reduced, 1 ≤ [numerator] < [denominator] ≤ 12. */
    data class Fraction(val numerator: Long, val denominator: Long) : NumberValue

    data class Ordinal(val n: Long) : NumberValue
}

internal val NumberValue.form: NumberForm
    get() = when (this) {
        is NumberValue.Negative -> NumberForm.Negative
        is NumberValue.Decimal -> NumberForm.Decimal
        is NumberValue.Percent -> NumberForm.Percent
        is NumberValue.Multiplicative -> NumberForm.Multiplicative
        is NumberValue.Fraction -> NumberForm.Fraction
        is NumberValue.Ordinal -> NumberForm.Ordinal
    }

/**
 * How far a language reaches. The ladder INTERSECTS its rung with this, so a language
 * that cannot read a form simply never draws it — the registry pattern again, and the
 * default is "nothing", so an unauthored pack offers no Forms drill instead of crashing.
 *
 * The reach defaults are the drill's own — fractions to twelfths, ordinals to 100 —
 * so a pack names a field only where it DEPARTS from them. A restated default reads
 * as a decision the language made, which is exactly what es and sw did make and de,
 * en and uk did not.
 */
internal data class FormLimits(
    val forms: Set<NumberForm> = emptySet(),
    val fractionDenominators: Set<Int> = (2..12).toSet(),
    val ordinalRange: LongRange = 1L..100L,
)

/**
 * U+202F before the sign, as the typographic rule (and RAE) prescribes.
 * Written as an escape, like [GROUP_SEPARATOR]: an invisible space in a literal
 * does not survive editing.
 */
internal const val PERCENT_SUFFIX = "\u202F%"

/** U+00D7 MULTIPLICATION SIGN — never the letter x, which is a word in some readings. */
internal const val TIMES_SUFFIX = "\u00D7"

/**
 * The prompt side of a form.
 *
 * Unlike every other [TrainerKind], a Forms prompt is LANGUAGE-DEPENDENT: `3,7` in German
 * and `3.7` in English. The mark is not decoration — the reading names it ("Komma" vs
 * "point"), so a shared prompt would lie about the answer it grades. Everything else
 * stays neutral: `20.` is the ordinal mark in all five languages for the same reason
 * U+202F is the group separator in all five.
 *
 * [grouped] fills [TrainerTask.promptDisplay]; the integer part is what gets grouped,
 * so a five-digit negative reads `-12 345` and its fraction digits stay one run.
 */
internal fun renderForm(value: NumberValue, decimalMark: Char, grouped: Boolean): String {
    fun digits(n: Long): String = n.toString().let { if (grouped) groupDigits(it) else it }
    return when (value) {
        is NumberValue.Negative -> "-" + digits(value.magnitude)
        is NumberValue.Decimal -> digits(value.whole) + decimalMark + value.fractionDigits
        is NumberValue.Percent -> digits(value.n) + PERCENT_SUFFIX
        is NumberValue.Multiplicative -> digits(value.n) + TIMES_SUFFIX
        is NumberValue.Fraction -> "${value.numerator}/${value.denominator}"
        is NumberValue.Ordinal -> digits(value.n) + "."
    }
}

/**
 * What a REVERSED form task accepts: the learner is shown the reading and types the value,
 * so both renderings grade, plus the spellings the notation makes ambiguous —
 * the number is under test, never the punctuation.
 *
 * Canonical (grouped) first, so it doubles as the reveal.
 */
internal fun formDigitForms(prompt: String, promptDisplay: String): List<String> =
    listOf(promptDisplay, prompt).flatMap(::formSpellings).distinct()

private fun formSpellings(rendered: String): List<String> = when {
    // The ordinal mark first: a decimal never ends on its mark, an ordinal always does.
    rendered.endsWith('.') -> listOf(rendered, rendered.dropLast(1))
    rendered.endsWith(PERCENT_SUFFIX) -> {
        val bare = rendered.removeSuffix(PERCENT_SUFFIX)
        listOf(rendered, "$bare%", bare)
    }
    rendered.endsWith(TIMES_SUFFIX) -> {
        val bare = rendered.removeSuffix(TIMES_SUFFIX)
        listOf(rendered, bare + "x", bare)
    }
    ',' in rendered -> listOf(rendered, rendered.replace(',', '.'))
    '.' in rendered -> listOf(rendered, rendered.replace('.', ','))
    else -> listOf(rendered)
}
