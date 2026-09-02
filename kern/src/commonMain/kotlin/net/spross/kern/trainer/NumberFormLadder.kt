package net.spross.kern.trainer

import kotlin.random.Random

/**
 * The Forms ladder: ten Sprossen, each ADDING to everything below it.
 *
 * | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
 * |---|---|---|---|---|---|---|---|---|----|
 * | −(1–20) | +decimal 1+1 | +percent (round) | +×(1–12) | +unit fractions d≤4 |
 * | +ordinal 1–12 | −(2–3 digits), decimal 2 places | any reduced n/d, d≤12 |
 * | ordinal 13–100, any percent | −(4 digits), decimal 2–3 whole digits, ×(to 100) |
 *
 * Sprossen 1–6 each introduce one form; 7–10 widen forms already in play, which is why
 * [SprosseForms] runs out after six entries. Every per-form draw is defined at every level,
 * so a form reached through the fallback below still has a range to draw from.
 */
private val LADDER: List<NumberForm> = NumberForm.entries

internal const val FORMS_MAX_LEVEL = 10

/** Which forms Sprosse [level] offers — everything introduced at or below it. */
internal fun sprosseForms(level: Int): Set<NumberForm> =
    LADDER.take(level.coerceIn(1, FORMS_MAX_LEVEL)).toSet()

/**
 * Draws one value for [level] within [limits], or null when the language reads no form
 * at all. The Sprosse's forms are INTERSECTED with the language's; when that intersection is
 * empty — a language that reads fractions but no negatives, asked at Sprosse 1 — the language's
 * own set stands in, walked in ladder order. Deterministic in [rng], with no retry loop:
 * a draw that cannot be satisfied is never attempted, it is filtered out first.
 *
 * [magnitudeDigits] sizes the two forms that HAVE a magnitude — the negative's value and
 * the decimal's whole part — from a Numbers Sprosse instead of this ladder's own gentler one
 * ([DrillModifier.Mix]). Zero, the default, leaves every draw to [level]. A percentage is
 * bounded by its own meaning and a fraction by its denominator, so neither ever grows.
 */
internal fun drawForm(limits: FormLimits, level: Int, rng: Random, magnitudeDigits: Int = 0): NumberValue? {
    val available = LADDER.filter { it in limits.forms && drawable(it, limits) }
    if (available.isEmpty()) return null
    val onSprosse = available.filter { it in sprosseForms(level) }
    val candidates = onSprosse.ifEmpty { available }
    return when (candidates[rng.nextInt(candidates.size)]) {
        NumberForm.Negative -> drawNegative(level, magnitudeDigits, rng)
        NumberForm.Decimal -> drawDecimal(level, magnitudeDigits, rng)
        NumberForm.Percent -> drawPercent(level, rng)
        NumberForm.Multiplicative -> drawMultiplicative(level, rng)
        NumberForm.Fraction -> drawFraction(limits, level, rng)
        NumberForm.Ordinal -> drawOrdinal(limits, level, rng)
    }
}

/** A form whose parameter space this language empties out can never be drawn. */
private fun drawable(form: NumberForm, limits: FormLimits): Boolean = when (form) {
    NumberForm.Fraction -> fractionPool(limits, wide = true).isNotEmpty()
    NumberForm.Ordinal -> !ordinalPool(limits).isEmpty()
    else -> true
}

private fun drawNegative(level: Int, magnitudeDigits: Int, rng: Random): NumberValue.Negative {
    // why: floored at 1 — there is no negative zero to read out.
    if (magnitudeDigits >= 1) return NumberValue.Negative(maxOf(1L, drawNumber(magnitudeDigits, rng)))
    val bound = when {
        level >= 10 -> 10_000L
        level >= 7 -> 1_000L
        else -> 21L
    }
    return NumberValue.Negative(rng.nextLong(1, bound))
}

private fun drawDecimal(level: Int, magnitudeDigits: Int, rng: Random): NumberValue.Decimal {
    val whole = when {
        magnitudeDigits >= 1 -> drawNumber(magnitudeDigits, rng)
        level >= 10 -> rng.nextLong(0, 1_000)
        else -> rng.nextLong(0, 10)
    }
    val places = when {
        level >= 10 -> 1 + rng.nextInt(3)
        level >= 7 -> 1 + rng.nextInt(2)
        else -> 1
    }
    return NumberValue.Decimal(whole, fractionDigits(places, rng))
}

/**
 * A digit string that is never all zeros. A trailing zero is worth drawing — "3,40" and
 * "3,4" are different readings — but "3,00" is degenerate in every language that names the
 * place it lands on ("три цілих нуль сотих"), so an all-zero draw is repaired rather than
 * retried: the ladder promises no retry loops.
 */
private fun fractionDigits(places: Int, rng: Random): String {
    val digits = CharArray(places) { '0' + rng.nextInt(10) }
    if (digits.all { it == '0' }) digits[places - 1] = '1' + rng.nextInt(9)
    return digits.concatToString()
}

/**
 * Round percentages are the ones a learner meets on a label or a discount sign;
 * the arbitrary ones wait for Sprosse 9, where the drill is about the reading and not
 * about recognizing 45 as a percentage.
 */
private val ROUND_PERCENTS = listOf(1L, 5L, 10L, 20L, 25L, 30L, 40L, 50L, 60L, 70L, 75L, 80L, 90L, 100L)

private fun drawPercent(level: Int, rng: Random): NumberValue.Percent = NumberValue.Percent(
    if (level >= 9) rng.nextLong(1, 101) else ROUND_PERCENTS[rng.nextInt(ROUND_PERCENTS.size)],
)

private fun drawMultiplicative(level: Int, rng: Random): NumberValue.Multiplicative =
    NumberValue.Multiplicative(if (level >= 10) rng.nextLong(1, 101) else rng.nextLong(1, 13))

private fun drawFraction(limits: FormLimits, level: Int, rng: Random): NumberValue.Fraction {
    val pool = fractionPool(limits, wide = level >= 8).ifEmpty { fractionPool(limits, wide = true) }
    return pool[rng.nextInt(pool.size)]
}

/**
 * Every fraction the language allows, REDUCED. Unreduced draws are excluded at the source
 * rather than filtered later: 2/4 would legitimately read both "zwei Viertel" and "ein halb",
 * and no pack should have to carry that equivalence to grade its own drill.
 *
 * [minDenominator] is what a phrase slot raises to 3 — see [drawFractionSlot], where a half
 * would have to agree with the noun it stands before.
 */
internal fun fractionPool(
    limits: FormLimits,
    wide: Boolean,
    minDenominator: Int = 2,
): List<NumberValue.Fraction> {
    val denominators = limits.fractionDenominators.filter { it in minDenominator..12 }.sorted()
    val allowed = if (wide) denominators else denominators.filter { it <= 4 }.ifEmpty { denominators }
    return allowed.flatMap { d ->
        val numerators = if (wide) (1 until d).filter { gcd(it, d) == 1 } else listOf(1)
        numerators.map { NumberValue.Fraction(it.toLong(), d.toLong()) }
    }
}

private fun drawOrdinal(limits: FormLimits, level: Int, rng: Random): NumberValue.Ordinal {
    val pool = ordinalPool(limits)
    val narrowed = pool.first..minOf(pool.last, 12L)
    val range = if (level >= 9 || narrowed.isEmpty()) pool else narrowed
    return NumberValue.Ordinal(rng.nextLong(range.first, range.last + 1))
}

/** There is no zeroth of anything: the pack's range is floored at 1. */
private fun ordinalPool(limits: FormLimits): LongRange =
    maxOf(1L, limits.ordinalRange.first)..limits.ordinalRange.last

internal tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
