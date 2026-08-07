package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The VOCABULARY SPEC for the number forms — authored from the language research
 * (Duden · DWDS · Wikipedia "Zahlwort" for German; Wikipedia "English numerals" ·
 * Wiktionary "per cent" for English), never read back off the generator.
 * A reading that changes here is a claim about the language, not about the code.
 */
class TrainerFormsTests {

    private fun readings(language: String, value: NumberValue): List<String> =
        Trainer.pack(language).formReading(value)

    private fun assertCanonical(language: String, value: NumberValue, expected: String) {
        assertEquals(expected, readings(language, value).first(), "$language $value")
    }

    private fun assertAccepts(language: String, value: NumberValue, vararg forms: String) {
        val all = readings(language, value)
        for (form in forms) assertTrue(form in all, "$language $value: \"$form\" missing from $all")
    }

    private fun assertRejects(language: String, value: NumberValue, vararg forms: String) {
        val all = readings(language, value)
        for (form in forms) assertFalse(form in all, "$language $value: \"$form\" must not grade")
    }

    // German

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

    // English

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
