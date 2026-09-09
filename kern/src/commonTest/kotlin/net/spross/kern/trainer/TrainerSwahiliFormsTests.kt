package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The vocabulary spec for Swahili's number forms, authored from Almasi's grammar and the TIE
 * Hisabati series.
 * It is never read back off the generator: a reading that changes here is a claim about the
 * language, not about the code.
 * The helpers it asserts through are `NumberFormsFixture`'s.
 */
class TrainerSwahiliFormsTests {

    @Test
    fun swahiliNegativesLeadWithHasi() {
        assertCanonical("sw", NumberValue.Negative(7), "hasi saba")
        assertCanonical("sw", NumberValue.Negative(14), "hasi kumi na nne")
        assertCanonical("sw", NumberValue.Negative(45), "hasi arobaini na tano")
        assertCanonical("sw", NumberValue.Negative(365), "hasi mia tatu na sitini na tano")
        assertAccepts("sw", NumberValue.Negative(7), "saba hasi", "minus saba")
    }

    /** kasoro is subtractive "less" (saa tatu kasorobo), never the sign of a value. */
    @Test
    fun swahiliNegativesRefuseKasoro() {
        assertRejects("sw", NumberValue.Negative(7), "kasoro saba", "kutoa saba")
    }

    /** The na-less spelling grades behind the form word exactly as it does in the drill. */
    @Test
    fun swahiliFormsInheritTheNaLessSpelling() {
        assertAccepts("sw", NumberValue.Negative(365), "hasi mia tatu sitini tano")
        assertAccepts("sw", NumberValue.Percent(45), "asilimia arobaini tano")
    }

    @Test
    fun swahiliDecimalsReadEachDigitAfterNukta() {
        assertCanonical("sw", NumberValue.Decimal(3, "7"), "tatu nukta saba")
        assertAccepts("sw", NumberValue.Decimal(3, "7"), "tatu pointi saba")
        assertCanonical("sw", NumberValue.Decimal(0, "01"), "sifuri nukta sifuri moja")
        assertCanonical("sw", NumberValue.Decimal(3, "40"), "tatu nukta nne sifuri")
        assertCanonical(
            "sw", NumberValue.Decimal(27, "3145"),
            "ishirini na saba nukta tatu moja nne tano",
        )
        assertCanonical("sw", NumberValue.Decimal(880, "9"), "mia nane na themanini nukta tisa")
    }

    /** desimali is the noun for a decimal number, so it would read "3 decimal 7". */
    @Test
    fun swahiliDecimalsRefuseDesimaliAsTheMark() {
        assertRejects("sw", NumberValue.Decimal(3, "7"), "tatu desimali saba")
    }

    @Test
    fun swahiliPercentPutsAsilimiaFirst() {
        assertCanonical("sw", NumberValue.Percent(1), "asilimia moja")
        assertCanonical("sw", NumberValue.Percent(45), "asilimia arobaini na tano")
        assertCanonical("sw", NumberValue.Percent(68), "asilimia sitini na nane")
        assertCanonical("sw", NumberValue.Percent(72), "asilimia sabini na mbili")
        assertAccepts("sw", NumberValue.Percent(1), "moja kwa mia")
        assertAccepts("sw", NumberValue.Percent(70), "sabini kwa mia")
    }

    @Test
    fun swahiliMultiplicativesPutMaraFirst() {
        assertCanonical("sw", NumberValue.Multiplicative(1), "mara moja")
        assertCanonical("sw", NumberValue.Multiplicative(2), "mara mbili")
        assertAccepts("sw", NumberValue.Multiplicative(2), "maradufu")
        assertCanonical("sw", NumberValue.Multiplicative(3), "mara tatu")
        assertCanonical("sw", NumberValue.Multiplicative(10), "mara kumi")
        assertCanonical("sw", NumberValue.Multiplicative(12), "mara kumi na mbili")
    }

    @Test
    fun swahiliFractionsAreTheThreeArabicNounsWithThePostposedNumerator() {
        assertCanonical("sw", NumberValue.Fraction(1, 2), "nusu")
        assertCanonical("sw", NumberValue.Fraction(1, 3), "theluthi")
        assertAccepts("sw", NumberValue.Fraction(1, 3), "thuluthi", "theluthi moja")
        assertCanonical("sw", NumberValue.Fraction(2, 3), "theluthi mbili")
        assertAccepts("sw", NumberValue.Fraction(2, 3), "thuluthi mbili")
        assertCanonical("sw", NumberValue.Fraction(1, 4), "robo")
        assertAccepts("sw", NumberValue.Fraction(1, 4), "robo moja", "sehemu moja ya nne")
        assertCanonical("sw", NumberValue.Fraction(3, 4), "robo tatu")
    }

    /** The periphrasis takes N-class concord from the numerator: ya for one part, za for more. */
    @Test
    fun swahiliFractionPeriphrasisAgreesWithTheNumerator() {
        assertAccepts("sw", NumberValue.Fraction(1, 3), "sehemu moja ya tatu")
        assertAccepts("sw", NumberValue.Fraction(2, 3), "sehemu mbili za tatu")
        assertAccepts("sw", NumberValue.Fraction(3, 4), "sehemu tatu za nne")
    }

    /**
     * Past a quarter the sources give three incompatible systems, so the drill stops there
     * — and there is no citable bare ordinal at all: the concord slot needs a noun.
     */
    @Test
    fun swahiliDrawsNeitherLargerDenominatorsNorOrdinals() {
        val limits = Trainer.pack("sw").formLimits
        assertEquals(setOf(2, 3, 4), limits.fractionDenominators)
        assertTrue(limits.ordinalRange.isEmpty(), "sw must reach no ordinal")
        assertFalse(NumberForm.Ordinal in limits.forms)
        assertEquals(emptyList(), readings("sw", NumberValue.Ordinal(3)))
        assertEquals(emptyList(), readings("sw", NumberValue.Fraction(1, 5)))
    }
}
