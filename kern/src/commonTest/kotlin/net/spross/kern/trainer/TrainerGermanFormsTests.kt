package net.spross.kern.trainer

import kotlin.test.Test

/**
 * The vocabulary spec for German's number forms, authored from Duden, DWDS and Wikipedia's
 * "Zahlwort".
 * It is never read back off the generator: a reading that changes here is a claim about the
 * language, not about the code.
 * The helpers it asserts through are `NumberFormsFixture`'s.
 */
class TrainerGermanFormsTests {

    @Test
    fun germanNegativesAreMinusPlusTheCardinal() {
        assertCanonical("de", NumberValue.Negative(7), "minus sieben")
        assertCanonical("de", NumberValue.Negative(45), "minus fünfundvierzig")
        assertCanonical(
            "de", NumberValue.Negative(12345),
            "minus zwölftausenddreihundertfünfundvierzig",
        )
    }

    @Test
    fun germanDecimalsReadEachDigitAndOfferTheRunTogetherOne() {
        assertCanonical("de", NumberValue.Decimal(3, "7"), "drei Komma sieben")
        assertCanonical("de", NumberValue.Decimal(3, "45"), "drei Komma vier fünf")
        assertAccepts("de", NumberValue.Decimal(3, "45"), "drei Komma fünfundvierzig")
        assertCanonical("de", NumberValue.Decimal(12, "30"), "zwölf Komma drei null")
        assertAccepts("de", NumberValue.Decimal(12, "30"), "zwölf Komma dreißig")
    }

    /** The standalone "eins" is right here and nowhere else — no noun follows the comma. */
    @Test
    fun germanDecimalWholePartKeepsTheStandaloneOne() {
        assertCanonical("de", NumberValue.Decimal(1, "5"), "eins Komma fünf")
    }

    @Test
    fun germanDecimalWithALeadingZeroHasNoRunTogetherReading() {
        assertCanonical("de", NumberValue.Decimal(0, "05"), "null Komma null fünf")
        assertRejects("de", NumberValue.Decimal(0, "05"), "null Komma fünf")
    }

    @Test
    fun germanPercentTakesTheAttributiveOne() {
        assertCanonical("de", NumberValue.Percent(1), "ein Prozent")
        assertRejects("de", NumberValue.Percent(1), "eins Prozent")
        assertCanonical("de", NumberValue.Percent(45), "fünfundvierzig Prozent")
        assertCanonical("de", NumberValue.Percent(100), "einhundert Prozent")
        assertAccepts("de", NumberValue.Percent(100), "hundert Prozent")
    }

    @Test
    fun germanMultiplicativesAreOneLowercaseWord() {
        assertCanonical("de", NumberValue.Multiplicative(1), "einmal")
        assertAccepts("de", NumberValue.Multiplicative(1), "ein Mal")
        assertCanonical("de", NumberValue.Multiplicative(3), "dreimal")
        assertAccepts("de", NumberValue.Multiplicative(3), "drei Mal")
        assertCanonical("de", NumberValue.Multiplicative(20), "zwanzigmal")
        assertCanonical("de", NumberValue.Multiplicative(100), "einhundertmal")
        assertAccepts("de", NumberValue.Multiplicative(100), "hundertmal", "hundert Mal")
    }

    @Test
    fun germanFractionsBuildTheDenominatorFromTheOrdinalStem() {
        assertCanonical("de", NumberValue.Fraction(1, 3), "ein Drittel")
        assertCanonical("de", NumberValue.Fraction(2, 3), "zwei Drittel")
        assertCanonical("de", NumberValue.Fraction(3, 4), "drei Viertel")
        assertCanonical("de", NumberValue.Fraction(5, 6), "fünf Sechstel")
        assertCanonical("de", NumberValue.Fraction(1, 7), "ein Siebtel")
        assertAccepts("de", NumberValue.Fraction(1, 7), "ein Siebentel")
        assertCanonical("de", NumberValue.Fraction(3, 8), "drei Achtel")
        assertCanonical("de", NumberValue.Fraction(7, 11), "sieben Elftel")
        assertCanonical("de", NumberValue.Fraction(5, 12), "fünf Zwölftel")
    }

    /** Zweitel is veraltet; a half is suppletive, and 1 ≤ n < d makes it always "ein halb". */
    @Test
    fun germanHalvesAreSuppletive() {
        assertCanonical("de", NumberValue.Fraction(1, 2), "ein halb")
        assertAccepts("de", NumberValue.Fraction(1, 2), "einhalb", "die Hälfte", "eine Hälfte")
        assertRejects("de", NumberValue.Fraction(1, 2), "ein Zweitel")
    }

    @Test
    fun germanOrdinalsSwitchFromTeToSteAtTwenty() {
        assertCanonical("de", NumberValue.Ordinal(1), "erste")
        assertAccepts("de", NumberValue.Ordinal(1), "erster", "ersten", "erstes")
        assertCanonical("de", NumberValue.Ordinal(3), "dritte")
        assertCanonical("de", NumberValue.Ordinal(6), "sechste")
        assertCanonical("de", NumberValue.Ordinal(7), "siebte")
        assertAccepts("de", NumberValue.Ordinal(7), "siebente", "siebenter")
        assertCanonical("de", NumberValue.Ordinal(8), "achte")
        assertCanonical("de", NumberValue.Ordinal(12), "zwölfte")
        assertCanonical("de", NumberValue.Ordinal(16), "sechzehnte")
        assertCanonical("de", NumberValue.Ordinal(19), "neunzehnte")
        assertCanonical("de", NumberValue.Ordinal(20), "zwanzigste")
        assertCanonical("de", NumberValue.Ordinal(21), "einundzwanzigste")
        assertCanonical("de", NumberValue.Ordinal(30), "dreißigste")
        assertCanonical("de", NumberValue.Ordinal(100), "einhundertste")
        assertAccepts("de", NumberValue.Ordinal(100), "hundertste")
    }
}
