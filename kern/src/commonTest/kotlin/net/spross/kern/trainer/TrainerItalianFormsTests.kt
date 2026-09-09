package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The vocabulary spec for Italian's number forms, authored from the language research.
 * It is never read back off the generator: a reading that changes here is a claim about the
 * language, not about the code.
 * The helpers it asserts through are `NumberFormsFixture`'s.
 */
class TrainerItalianFormsTests {

    @Test
    fun italianNegativesAreMenoPlusTheCardinal() {
        assertCanonical("it", NumberValue.Negative(7), "meno sette")
        assertCanonical("it", NumberValue.Negative(45), "meno quarantacinque")
        assertCanonical("it", NumberValue.Negative(1234), "meno milleduecentotrentaquattro")
    }

    @Test
    fun italianDecimalsReadEachDigitAndOfferTheRunTogetherOne() {
        assertCanonical("it", NumberValue.Decimal(3, "7"), "tre virgola sette")
        assertCanonical("it", NumberValue.Decimal(3, "45"), "tre virgola quattro cinque")
        assertAccepts("it", NumberValue.Decimal(3, "45"), "tre virgola quarantacinque")
        assertCanonical("it", NumberValue.Decimal(12, "30"), "dodici virgola tre zero")
        assertAccepts("it", NumberValue.Decimal(12, "30"), "dodici virgola trenta")
        assertCanonical("it", NumberValue.Decimal(1, "5"), "uno virgola cinque")
    }

    /** punto names the thousands dot in Italian, so reading it would say another number. */
    @Test
    fun italianDecimalsTakeNoOtherMarkAndNoRunTogetherOnALeadingZero() {
        assertRejects("it", NumberValue.Decimal(3, "7"), "tre punto sette")
        assertCanonical("it", NumberValue.Decimal(0, "05"), "zero virgola zero cinque")
        assertRejects("it", NumberValue.Decimal(0, "05"), "zero virgola cinque")
    }

    /** «per cento» is a preposition plus a noun, so uno neither apocopates nor welds. */
    @Test
    fun italianPercentKeepsTheNumeralWholeAndTheTwoWordsApart() {
        assertCanonical("it", NumberValue.Percent(1), "uno per cento")
        assertCanonical("it", NumberValue.Percent(21), "ventuno per cento")
        assertCanonical("it", NumberValue.Percent(25), "venticinque per cento")
        assertCanonical("it", NumberValue.Percent(100), "cento per cento")
        assertRejects("it", NumberValue.Percent(1), "un per cento", "uno percento")
        assertRejects("it", NumberValue.Percent(21), "ventun per cento", "ventuno percento")
    }

    /** volta is feminine, and from 21 the numeral apocopates before the noun it counts. */
    @Test
    fun italianMultiplicativesCountVolteAndApocopateBeforeThem() {
        assertCanonical("it", NumberValue.Multiplicative(1), "una volta")
        assertCanonical("it", NumberValue.Multiplicative(2), "due volte")
        assertCanonical("it", NumberValue.Multiplicative(12), "dodici volte")
        assertCanonical("it", NumberValue.Multiplicative(21), "ventun volte")
        assertAccepts("it", NumberValue.Multiplicative(21), "ventuno volte")
        assertCanonical("it", NumberValue.Multiplicative(31), "trentun volte")
        assertCanonical("it", NumberValue.Multiplicative(100), "cento volte")
        assertRejects("it", NumberValue.Multiplicative(1), "uno volta", "un volta")
        assertRejects("it", NumberValue.Multiplicative(2), "due volta", "doppio")
    }

    @Test
    fun italianFractionsApocopateTheNumeratorAndPluralizeTheDenominator() {
        assertCanonical("it", NumberValue.Fraction(1, 3), "un terzo")
        assertCanonical("it", NumberValue.Fraction(2, 3), "due terzi")
        assertCanonical("it", NumberValue.Fraction(3, 4), "tre quarti")
        assertCanonical("it", NumberValue.Fraction(1, 7), "un settimo")
        assertCanonical("it", NumberValue.Fraction(5, 8), "cinque ottavi")
        assertRejects("it", NumberValue.Fraction(1, 3), "uno terzo", "un terzi")
    }

    @Test
    fun italianHalvesAreSuppletive() {
        assertCanonical("it", NumberValue.Fraction(1, 2), "un mezzo")
        assertAccepts("it", NumberValue.Fraction(1, 2), "mezzo", "la metà", "metà")
    }

    /** Past ten the fraction noun IS the -esimo ordinal, so the two tables never diverge. */
    @Test
    fun italianFractionsPastTenAreTheEsimoOrdinal() {
        assertCanonical("it", NumberValue.Fraction(1, 11), "un undicesimo")
        assertCanonical("it", NumberValue.Fraction(5, 12), "cinque dodicesimi")
        assertCanonical("it", NumberValue.Fraction(11, 12), "undici dodicesimi")
    }

    @Test
    fun italianOrdinalsAreSuppletiveToTenAndProductiveAfterIt() {
        assertCanonical("it", NumberValue.Ordinal(1), "primo")
        assertAccepts("it", NumberValue.Ordinal(1), "prima")
        assertCanonical("it", NumberValue.Ordinal(3), "terzo")
        assertCanonical("it", NumberValue.Ordinal(9), "nono")
        assertCanonical("it", NumberValue.Ordinal(10), "decimo")
        assertCanonical("it", NumberValue.Ordinal(11), "undicesimo")
        assertCanonical("it", NumberValue.Ordinal(20), "ventesimo")
        assertCanonical("it", NumberValue.Ordinal(21), "ventunesimo")
        assertCanonical("it", NumberValue.Ordinal(28), "ventottesimo")
        assertCanonical("it", NumberValue.Ordinal(40), "quarantesimo")
        assertCanonical("it", NumberValue.Ordinal(100), "centesimo")
        assertAccepts("it", NumberValue.Ordinal(100), "centesima")
        // The suffix eats the cardinal's last vowel — except where that vowel is stressed
        // (ventitré) or part of a diphthong (ventisei), which both survive it.
        assertCanonical("it", NumberValue.Ordinal(23), "ventitreesimo")
        assertCanonical("it", NumberValue.Ordinal(26), "ventiseiesimo")
        assertRejects("it", NumberValue.Ordinal(23), "ventitresimo")
        assertRejects("it", NumberValue.Ordinal(26), "ventisesimo")
    }

    /** -esimo is productive, so nothing bars the drill from reaching its own ceiling. */
    @Test
    fun italianOrdinalsRunTheWholeDefaultRange() {
        assertEquals(1L..100L, Trainer.pack("it").formLimits.ordinalRange)
        assertEquals((2..12).toSet(), Trainer.pack("it").formLimits.fractionDenominators)
    }
}
