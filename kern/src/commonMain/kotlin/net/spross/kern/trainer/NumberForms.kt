package net.spross.kern.trainer

/**
 * The ways a number can be asked beyond the bare cardinal.
 *
 * Declaration order is LADDER order: the order the forms unlock on the Forms drill,
 * and the order a language's own set is walked when a Sprosse offers nothing it can read.
 * All of this is `internal` on purpose — none of it belongs in the ObjC header,
 * because the app only ever sees the rendered [NumbersTask].
 */
internal enum class NumberForm {
    Negative, Decimal, Percent, Multiplicative, Fraction, Ordinal, Price;

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

    /**
     * An amount as a price tag writes it, in the currency the language prices in —
     * carried on the value because the tag is part of the prompt ("3,50 €", "$3.50").
     * [cents] is 0 for a whole amount and always 0 where the currency has no minor unit.
     */
    data class Price(val units: Long, val cents: Long, val currency: Currency) : NumberValue
}

/**
 * The money a language's prices are drawn in, and how its tag writes one.
 *
 * [step] is the smallest amount a tag shows in whole units, and [minorUnit] whether cents
 * are drawn at all: a Tanzanian shilling price is a round sum with no coin below it in use,
 * so a Swahili price is never "TSh 3,50".
 */
internal enum class Currency(
    val prefix: String,
    val suffix: String,
    val minorUnit: Boolean,
    val step: Long = 1,
) {
    Euro("", "\u00A0€", minorUnit = true),
    Dollar("$", "", minorUnit = true),
    Hryvnia("", "\u00A0грн", minorUnit = true),
    Shilling("TSh\u00A0", "", minorUnit = false, step = 50),
    ;

    /** The worked example the numbers page shows — a tag with cents wherever there are any. */
    val example: NumberValue.Price
        get() = if (minorUnit) NumberValue.Price(3, 50, this) else NumberValue.Price(500, 0, this)

    fun marks(rendered: String): Boolean =
        rendered.startsWith(prefix) && rendered.endsWith(suffix) && (prefix + suffix).isNotEmpty()
}

internal val NumberValue.form: NumberForm
    get() = when (this) {
        is NumberValue.Negative -> NumberForm.Negative
        is NumberValue.Decimal -> NumberForm.Decimal
        is NumberValue.Percent -> NumberForm.Percent
        is NumberValue.Multiplicative -> NumberForm.Multiplicative
        is NumberValue.Fraction -> NumberForm.Fraction
        is NumberValue.Ordinal -> NumberForm.Ordinal
        is NumberValue.Price -> NumberForm.Price
    }

/**
 * How far a language reaches. The ladder INTERSECTS its Sprosse with this, so a language
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
    /** What a price is drawn in; null reads no price, whatever [forms] says. */
    val currency: Currency? = null,
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
 * Unlike every other [NumbersReading], a Form prompt is LANGUAGE-DEPENDENT: `3,7` in German
 * and `3.7` in English. The mark is not decoration — the reading names it ("Komma" vs
 * "point"), so a shared prompt would lie about the answer it grades — and a price wears
 * its own language's tag (`3,50 €` · `$3.50`), whose currency the reading names. Everything else
 * stays neutral: `20.` is the ordinal mark in all five languages for the same reason
 * U+202F is the group separator in all five.
 *
 * [grouped] fills [NumbersTask.promptDisplay]; the integer part is what gets grouped,
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
        is NumberValue.Price -> value.currency.let { currency ->
            val cents = if (value.cents == 0L) "" else decimalMark + value.cents.toString().padStart(2, '0')
            currency.prefix + digits(value.units) + cents + currency.suffix
        }
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
    // A price first: its amount may carry either mark, and the tag is not the number.
    Currency.entries.any { it.marks(rendered) } -> {
        val currency = Currency.entries.first { it.marks(rendered) }
        val bare = rendered.removePrefix(currency.prefix).removeSuffix(currency.suffix)
        val spaced = rendered.replace('\u00A0', ' ')
        listOf(rendered, spaced, spaced.replace(" ", "")) + formSpellings(bare)
    }
    // Then the ordinal mark: a decimal never ends on its mark, an ordinal always does.
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
