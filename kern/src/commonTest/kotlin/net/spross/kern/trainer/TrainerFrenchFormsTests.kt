package net.spross.kern.trainer

import kotlin.test.Test

/**
 * The vocabulary spec for French's number forms, authored from the Académie française,
 * Grevisse and the BDL.
 * It is never read back off the generator: a reading that changes here is a claim about the
 * language, not about the code.
 * The helpers it asserts through are `NumberFormsFixture`'s.
 */
class TrainerFrenchFormsTests {

    @Test
    fun frenchNegativesAreMoinsPlusTheCardinal() {
        assertCanonical("fr", NumberValue.Negative(7), "moins sept")
        assertCanonical("fr", NumberValue.Negative(45), "moins quarante-cinq")
        assertAccepts("fr", NumberValue.Negative(45), "moins quarante cinq")
        assertCanonical("fr", NumberValue.Negative(1234), "moins mille deux cent trente-quatre")
    }

    @Test
    fun frenchDecimalsReadEachDigitAndOfferTheRunTogetherOne() {
        assertCanonical("fr", NumberValue.Decimal(3, "7"), "trois virgule sept")
        assertCanonical("fr", NumberValue.Decimal(3, "45"), "trois virgule quatre cinq")
        assertAccepts(
            "fr", NumberValue.Decimal(3, "45"),
            "trois virgule quarante-cinq", "trois virgule quarante cinq",
        )
        assertCanonical("fr", NumberValue.Decimal(12, "30"), "douze virgule trois zéro")
        assertAccepts("fr", NumberValue.Decimal(12, "30"), "douze virgule trente")
        // The mark is named, never the punctuation's own name.
        assertRejects("fr", NumberValue.Decimal(3, "7"), "trois point sept", "trois comma sept")
    }

    @Test
    fun frenchDecimalWithALeadingZeroHasNoRunTogetherReading() {
        assertCanonical("fr", NumberValue.Decimal(0, "05"), "zéro virgule zéro cinq")
        assertRejects("fr", NumberValue.Decimal(0, "05"), "zéro virgule cinq")
    }

    @Test
    fun frenchPercentIsTwoWords() {
        assertCanonical("fr", NumberValue.Percent(1), "un pour cent")
        assertCanonical("fr", NumberValue.Percent(21), "vingt et un pour cent")
        assertAccepts("fr", NumberValue.Percent(21), "vingt-et-un pour cent")
        assertCanonical("fr", NumberValue.Percent(45), "quarante-cinq pour cent")
        assertCanonical("fr", NumberValue.Percent(70), "soixante-dix pour cent")
        assertAccepts("fr", NumberValue.Percent(70), "septante pour cent")
        assertCanonical("fr", NumberValue.Percent(100), "cent pour cent")
        assertRejects("fr", NumberValue.Percent(45), "quarante-cinq pourcent")
    }

    /** fois is feminine, so the numeral agrees: "vingt et une fois", never "vingt et un fois". */
    @Test
    fun frenchMultiplicativesCountFoisInTheFeminine() {
        assertCanonical("fr", NumberValue.Multiplicative(1), "une fois")
        assertCanonical("fr", NumberValue.Multiplicative(2), "deux fois")
        assertCanonical("fr", NumberValue.Multiplicative(21), "vingt et une fois")
        assertCanonical("fr", NumberValue.Multiplicative(81), "quatre-vingt-une fois")
        assertCanonical("fr", NumberValue.Multiplicative(100), "cent fois")
        assertRejects("fr", NumberValue.Multiplicative(1), "un fois")
        assertRejects("fr", NumberValue.Multiplicative(21), "vingt et un fois")
    }

    /** Thirds and quarters are suppletive; from a fifth the fraction noun IS the ordinal. */
    @Test
    fun frenchFractionsAreOrdinalsWithTiersAndQuartSuppleted() {
        assertCanonical("fr", NumberValue.Fraction(1, 3), "un tiers")
        assertCanonical("fr", NumberValue.Fraction(2, 3), "deux tiers")
        assertCanonical("fr", NumberValue.Fraction(1, 4), "un quart")
        assertCanonical("fr", NumberValue.Fraction(3, 4), "trois quarts")
        assertCanonical("fr", NumberValue.Fraction(4, 5), "quatre cinquièmes")
        assertCanonical("fr", NumberValue.Fraction(5, 8), "cinq huitièmes")
        assertCanonical("fr", NumberValue.Fraction(7, 11), "sept onzièmes")
        assertCanonical("fr", NumberValue.Fraction(5, 12), "cinq douzièmes")
        // The ordinal noun never reaches down to the two suppletive ones.
        assertRejects("fr", NumberValue.Fraction(1, 3), "un troisième")
        assertRejects("fr", NumberValue.Fraction(1, 4), "un quatrième")
    }

    @Test
    fun frenchHalvesAreSuppletive() {
        assertCanonical("fr", NumberValue.Fraction(1, 2), "un demi")
        assertAccepts("fr", NumberValue.Fraction(1, 2), "demi", "la moitié", "une demie")
        assertRejects("fr", NumberValue.Fraction(1, 2), "un deuxième")
    }

    /** -ième lands on the last segment, and only the first is suppletive. */
    @Test
    fun frenchOrdinalsAddIemeToTheLastSegment() {
        assertCanonical("fr", NumberValue.Ordinal(1), "premier")
        assertAccepts("fr", NumberValue.Ordinal(1), "première")
        assertRejects("fr", NumberValue.Ordinal(1), "unième")
        assertCanonical("fr", NumberValue.Ordinal(2), "deuxième")
        assertAccepts("fr", NumberValue.Ordinal(2), "second", "seconde")
        assertCanonical("fr", NumberValue.Ordinal(4), "quatrième")
        assertCanonical("fr", NumberValue.Ordinal(5), "cinquième")
        assertCanonical("fr", NumberValue.Ordinal(9), "neuvième")
        assertCanonical("fr", NumberValue.Ordinal(20), "vingtième")
        assertCanonical("fr", NumberValue.Ordinal(21), "vingt et unième")
        assertAccepts("fr", NumberValue.Ordinal(21), "vingt-et-unième")
        assertCanonical("fr", NumberValue.Ordinal(71), "soixante et onzième")
        // The plural mark of a multiplied vingt goes; the -s of trois is part of the word.
        assertCanonical("fr", NumberValue.Ordinal(80), "quatre-vingtième")
        assertCanonical("fr", NumberValue.Ordinal(90), "quatre-vingt-dixième")
        assertCanonical("fr", NumberValue.Ordinal(3), "troisième")
        assertCanonical("fr", NumberValue.Ordinal(100), "centième")
    }
}
