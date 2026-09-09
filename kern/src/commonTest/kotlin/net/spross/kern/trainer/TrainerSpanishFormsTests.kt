package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The vocabulary spec for Spanish's number forms, authored from the RAE's DPD, Ortografía and
 * Nueva gramática.
 * It is never read back off the generator: a reading that changes here is a claim about the
 * language, not about the code.
 * The helpers it asserts through are `NumberFormsFixture`'s.
 */
class TrainerSpanishFormsTests {

    @Test
    fun spanishNegativesAreMenosPlusTheCardinal() {
        assertCanonical("es", NumberValue.Negative(7), "menos siete")
        assertCanonical("es", NumberValue.Negative(45), "menos cuarenta y cinco")
        assertCanonical("es", NumberValue.Negative(1234), "menos mil doscientos treinta y cuatro")
    }

    @Test
    fun spanishDecimalsTakeBothSeparatorWordsAndBothReadings() {
        assertCanonical("es", NumberValue.Decimal(3, "7"), "tres coma siete")
        assertAccepts("es", NumberValue.Decimal(3, "7"), "tres punto siete")
        assertCanonical("es", NumberValue.Decimal(3, "45"), "tres coma cuatro cinco")
        assertAccepts(
            "es", NumberValue.Decimal(3, "45"),
            "tres coma cuarenta y cinco", "tres punto cuatro cinco", "tres punto cuarenta y cinco",
        )
        assertCanonical("es", NumberValue.Decimal(1, "5"), "uno coma cinco")
    }

    @Test
    fun spanishDecimalWithALeadingZeroHasNoRunTogetherReading() {
        assertCanonical("es", NumberValue.Decimal(0, "05"), "cero coma cero cinco")
        assertRejects("es", NumberValue.Decimal(0, "05"), "cero coma cinco", "cero punto cinco")
    }

    /** RAE: uno apocopates only immediately before a noun, and «por ciento» is not one. */
    @Test
    fun spanishPercentRefusesTheApocopatedNumeral() {
        assertCanonical("es", NumberValue.Percent(1), "uno por ciento")
        assertCanonical("es", NumberValue.Percent(21), "veintiuno por ciento")
        assertCanonical("es", NumberValue.Percent(31), "treinta y uno por ciento")
        assertCanonical("es", NumberValue.Percent(45), "cuarenta y cinco por ciento")
        assertRejects(
            "es", NumberValue.Percent(21),
            "veintiún por ciento", "veintiuno porciento",
        )
        assertRejects("es", NumberValue.Percent(1), "un por ciento", "uno porciento")
    }

    @Test
    fun spanishHundredPercentKeepsItsTwoIdioms() {
        assertCanonical("es", NumberValue.Percent(100), "cien por ciento")
        assertAccepts("es", NumberValue.Percent(100), "cien por cien", "ciento por ciento")
    }

    /** vez is feminine, so the numeral agrees: "veintiuna veces", never "veintiún veces". */
    @Test
    fun spanishMultiplicativesCountVecesInTheFeminine() {
        assertCanonical("es", NumberValue.Multiplicative(1), "una vez")
        assertCanonical("es", NumberValue.Multiplicative(2), "dos veces")
        assertCanonical("es", NumberValue.Multiplicative(12), "doce veces")
        assertCanonical("es", NumberValue.Multiplicative(21), "veintiuna veces")
        assertCanonical("es", NumberValue.Multiplicative(31), "treinta y una veces")
        assertCanonical("es", NumberValue.Multiplicative(100), "cien veces")
        assertRejects("es", NumberValue.Multiplicative(21), "veintiún veces", "veintiuno veces")
        assertRejects("es", NumberValue.Multiplicative(1), "un vez", "uno vez")
    }

    @Test
    fun spanishFractionsApocopateTheNumeratorAndOfferTheParteForm() {
        assertCanonical("es", NumberValue.Fraction(1, 3), "un tercio")
        assertAccepts("es", NumberValue.Fraction(1, 3), "una tercera parte", "la tercera parte")
        assertCanonical("es", NumberValue.Fraction(2, 3), "dos tercios")
        assertAccepts("es", NumberValue.Fraction(2, 3), "dos terceras partes")
        assertCanonical("es", NumberValue.Fraction(3, 4), "tres cuartos")
        assertAccepts("es", NumberValue.Fraction(3, 4), "tres cuartas partes")
        assertCanonical("es", NumberValue.Fraction(1, 7), "un séptimo")
        assertAccepts("es", NumberValue.Fraction(1, 7), "una séptima parte")
        assertCanonical("es", NumberValue.Fraction(5, 8), "cinco octavos")
    }

    @Test
    fun spanishHalvesAreSuppletive() {
        assertCanonical("es", NumberValue.Fraction(1, 2), "un medio")
        assertAccepts("es", NumberValue.Fraction(1, 2), "medio", "la mitad", "media parte")
    }

    /** From 11 the productive -avo leads and the etymological ordinal grades beside it. */
    @Test
    fun spanishFractionsPastTenLeadWithTheAvoSuffix() {
        assertCanonical("es", NumberValue.Fraction(1, 11), "un onceavo")
        assertAccepts(
            "es", NumberValue.Fraction(1, 11),
            "un undécimo", "una onceava parte", "la undécima parte",
        )
        assertCanonical("es", NumberValue.Fraction(5, 12), "cinco doceavos")
        assertAccepts(
            "es", NumberValue.Fraction(5, 12),
            "cinco duodécimos", "cinco doceavas partes",
        )
    }

    @Test
    fun spanishOrdinalsAreAnAuthoredTableThroughTwelve() {
        assertCanonical("es", NumberValue.Ordinal(1), "primero")
        assertAccepts("es", NumberValue.Ordinal(1), "primera")
        assertCanonical("es", NumberValue.Ordinal(2), "segundo")
        assertCanonical("es", NumberValue.Ordinal(3), "tercero")
        assertAccepts("es", NumberValue.Ordinal(3), "tercera")
        assertCanonical("es", NumberValue.Ordinal(7), "séptimo")
        assertCanonical("es", NumberValue.Ordinal(9), "noveno")
        assertCanonical("es", NumberValue.Ordinal(10), "décimo")
        assertCanonical("es", NumberValue.Ordinal(11), "undécimo")
        assertAccepts(
            "es", NumberValue.Ordinal(11),
            "undécima", "decimoprimero", "décimo primero",
        )
        assertCanonical("es", NumberValue.Ordinal(12), "duodécimo")
        assertAccepts("es", NumberValue.Ordinal(12), "duodécima", "decimosegundo", "décimo segundo")
    }

    /** -avo forms are exclusively fractional (DPD: «el onceavo aniversario» is wrong), */
    /** and the apocope is what an ordinal drill is about, so neither grades as an ordinal. */
    @Test
    fun spanishOrdinalsRefuseTheFractionSuffixAndTheApocope() {
        assertRejects("es", NumberValue.Ordinal(11), "onceavo")
        assertRejects("es", NumberValue.Ordinal(12), "doceavo")
        assertRejects("es", NumberValue.Ordinal(1), "primer")
        assertRejects("es", NumberValue.Ordinal(3), "tercer")
    }

    /** Past the second or third decade Spanish reaches for the cardinal, so the drill stops. */
    @Test
    fun spanishOrdinalsStopAtTwelve() {
        assertEquals(1L..12L, Trainer.pack("es").formLimits.ordinalRange)
        assertEquals(emptyList(), readings("es", NumberValue.Ordinal(20)))
    }
}
