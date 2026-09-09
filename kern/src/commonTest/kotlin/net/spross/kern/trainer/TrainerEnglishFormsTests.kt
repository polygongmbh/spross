package net.spross.kern.trainer

import kotlin.test.Test

/**
 * The vocabulary spec for English's number forms, authored from Wikipedia's "English numerals"
 * and Wiktionary's "per cent".
 * It is never read back off the generator: a reading that changes here is a claim about the
 * language, not about the code.
 * The helpers it asserts through are `NumberFormsFixture`'s.
 */
class TrainerEnglishFormsTests {

    @Test
    fun englishNegativesReadMinusAndNegative() {
        assertCanonical("en", NumberValue.Negative(7), "minus seven")
        assertAccepts("en", NumberValue.Negative(7), "negative seven")
        assertCanonical("en", NumberValue.Negative(45), "minus forty-five")
        assertAccepts("en", NumberValue.Negative(45), "minus forty five", "negative forty-five")
    }

    @Test
    fun englishDecimalsSpellEveryDigitAfterThePoint() {
        assertCanonical("en", NumberValue.Decimal(3, "7"), "three point seven")
        assertCanonical("en", NumberValue.Decimal(3, "45"), "three point four five")
        assertCanonical("en", NumberValue.Decimal(99, "3"), "ninety-nine point three")
        assertCanonical("en", NumberValue.Decimal(3, "40"), "three point four zero")
        assertAccepts("en", NumberValue.Decimal(3, "40"), "three point four oh")
    }

    /** No run-together reading in English: "three point forty-five" is not standard. */
    @Test
    fun englishDecimalsHaveNoRunTogetherReading() {
        assertRejects("en", NumberValue.Decimal(3, "45"), "three point forty-five")
    }

    @Test
    fun englishZeroBeforeThePointAlsoReadsNoughtOrNothing() {
        assertCanonical("en", NumberValue.Decimal(0, "05"), "zero point zero five")
        assertAccepts("en", NumberValue.Decimal(0, "05"), "nought point oh five", "point oh five")
    }

    @Test
    fun englishPercentTakesBothSpellingsOfTheWord() {
        assertCanonical("en", NumberValue.Percent(45), "forty-five percent")
        assertAccepts(
            "en", NumberValue.Percent(45),
            "forty-five per cent", "forty five percent", "forty five per cent",
        )
        assertCanonical("en", NumberValue.Percent(1), "one percent")
        assertCanonical("en", NumberValue.Percent(100), "one hundred percent")
        assertAccepts("en", NumberValue.Percent(100), "a hundred percent", "hundred percent")
    }

    @Test
    fun englishMultiplicativesAreOnceTwiceThenTimes() {
        assertCanonical("en", NumberValue.Multiplicative(1), "once")
        assertAccepts("en", NumberValue.Multiplicative(1), "one time")
        assertCanonical("en", NumberValue.Multiplicative(2), "twice")
        assertAccepts("en", NumberValue.Multiplicative(2), "two times")
        assertCanonical("en", NumberValue.Multiplicative(3), "three times")
        assertAccepts("en", NumberValue.Multiplicative(3), "thrice")
        assertCanonical("en", NumberValue.Multiplicative(12), "twelve times")
        assertCanonical("en", NumberValue.Multiplicative(100), "one hundred times")
        assertAccepts("en", NumberValue.Multiplicative(100), "a hundred times")
    }

    @Test
    fun englishFractionsAreOrdinalsWithHalfAndQuarterSuppleted() {
        assertCanonical("en", NumberValue.Fraction(1, 2), "one half")
        assertAccepts("en", NumberValue.Fraction(1, 2), "a half", "one-half", "half")
        assertCanonical("en", NumberValue.Fraction(1, 4), "one quarter")
        assertAccepts("en", NumberValue.Fraction(1, 4), "a quarter", "one fourth", "one-quarter")
        assertCanonical("en", NumberValue.Fraction(3, 4), "three quarters")
        assertAccepts("en", NumberValue.Fraction(3, 4), "three fourths", "three-quarters")
        assertCanonical("en", NumberValue.Fraction(2, 3), "two thirds")
        assertAccepts("en", NumberValue.Fraction(2, 3), "two-thirds")
        assertCanonical("en", NumberValue.Fraction(5, 8), "five eighths")
        assertCanonical("en", NumberValue.Fraction(1, 9), "one ninth")
        assertCanonical("en", NumberValue.Fraction(7, 11), "seven elevenths")
        assertCanonical("en", NumberValue.Fraction(5, 12), "five twelfths")
    }

    @Test
    fun englishOrdinalsChangeOnlyTheLastSegment() {
        assertCanonical("en", NumberValue.Ordinal(1), "first")
        assertCanonical("en", NumberValue.Ordinal(5), "fifth")
        assertCanonical("en", NumberValue.Ordinal(8), "eighth")
        assertCanonical("en", NumberValue.Ordinal(9), "ninth")
        assertCanonical("en", NumberValue.Ordinal(12), "twelfth")
        assertCanonical("en", NumberValue.Ordinal(13), "thirteenth")
        assertCanonical("en", NumberValue.Ordinal(20), "twentieth")
        assertCanonical("en", NumberValue.Ordinal(21), "twenty-first")
        assertAccepts("en", NumberValue.Ordinal(21), "twenty first")
        assertCanonical("en", NumberValue.Ordinal(40), "fortieth")
        assertCanonical("en", NumberValue.Ordinal(58), "fifty-eighth")
        assertCanonical("en", NumberValue.Ordinal(99), "ninety-ninth")
        assertCanonical("en", NumberValue.Ordinal(100), "one hundredth")
        assertAccepts("en", NumberValue.Ordinal(100), "hundredth", "a hundredth")
    }
}
