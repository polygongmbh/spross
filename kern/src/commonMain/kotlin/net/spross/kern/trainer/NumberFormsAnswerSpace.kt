package net.spross.kern.trainer

/**
 * Every number-form value a pack with these [FormLimits] can be asked — the answer space
 * itself, described once, for [NumberReadingIndex] to enumerate. A form a pack reads that
 * this enumeration does not offer is a reading the index cannot resolve, so the bounds
 * below are the reach of the drill's value check.
 *
 * The bounds are the ladder's own: negatives 0–99, decimals with a whole part 0–9 and one
 * or two fraction digits, percent and multiplicatives over the range the ladder draws
 * (1–100), every reduced fraction the pack allows, and ordinals over the pack's own range.
 * Values the ladder can never draw are left out — an all-zero fraction-digit string is
 * repaired at the source ([NumberFormLadder]), so no reading of "3,0" exists.
 */
internal object NumberFormsAnswerSpace {

    /** Every value the bounded enumeration offers, for the forms this pack reads. */
    fun drawableValues(limits: FormLimits): List<NumberValue> = buildList {
        if (NumberForm.Negative in limits.forms) {
            for (magnitude in 0L..99L) add(NumberValue.Negative(magnitude))
        }
        if (NumberForm.Decimal in limits.forms) {
            for (whole in 0L..9L) for (digits in FRACTION_DIGITS) add(NumberValue.Decimal(whole, digits))
        }
        if (NumberForm.Percent in limits.forms) {
            for (n in 1L..100L) add(NumberValue.Percent(n))
        }
        if (NumberForm.Multiplicative in limits.forms) {
            for (n in 1L..100L) add(NumberValue.Multiplicative(n))
        }
        if (NumberForm.Fraction in limits.forms) {
            for (d in limits.fractionDenominators.filter { it in 2..12 }.sorted()) {
                for (n in 1 until d) {
                    if (gcd(n, d) == 1) add(NumberValue.Fraction(n.toLong(), d.toLong()))
                }
            }
        }
        if (NumberForm.Ordinal in limits.forms) {
            for (n in maxOf(1L, limits.ordinalRange.first)..limits.ordinalRange.last) {
                add(NumberValue.Ordinal(n))
            }
        }
    }

    /** One or two digits, never all zeros — the ladder repairs that draw. */
    val FRACTION_DIGITS: List<String> =
        (1..9).map { it.toString() } +
            (0..99).map { it.toString().padStart(2, '0') }.filter { it != "00" }
}
