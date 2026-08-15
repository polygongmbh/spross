package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The VOCABULARY SPEC for the number forms — authored from the language research
 * (Duden · DWDS · Wikipedia "Zahlwort" for German; Wikipedia "English numerals" ·
 * Wiktionary "per cent" for English; RAE's DPD, Ortografía and Nueva gramática for
 * Spanish; Український правопис 2019 and the УДХТУ «Числівник» booklet for Ukrainian;
 * Almasi's grammar and the TIE Hisabati series for Swahili; the Académie française,
 * Grevisse and the BDL for French), never read back off the generator.
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

    // Spanish

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

    // Italian

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

    // Ukrainian

    @Test
    fun ukrainianNegativesCarryTheCardinalsOwnVariants() {
        assertCanonical("uk", NumberValue.Negative(7), "мінус сім")
        assertCanonical("uk", NumberValue.Negative(21), "мінус двадцять один")
        assertAccepts("uk", NumberValue.Negative(21), "мінус двадцять одна")
        assertCanonical("uk", NumberValue.Negative(1000), "мінус одна тисяча")
        assertAccepts("uk", NumberValue.Negative(1000), "мінус тисяча")
    }

    @Test
    fun ukrainianDecimalsNameThePlaceAndTakeTheFeminineWholePart() {
        assertCanonical("uk", NumberValue.Decimal(3, "7"), "три цілих сім десятих")
        assertCanonical("uk", NumberValue.Decimal(1, "5"), "одна ціла п'ять десятих")
        assertCanonical("uk", NumberValue.Decimal(0, "9"), "нуль цілих дев'ять десятих")
        assertCanonical("uk", NumberValue.Decimal(2, "34"), "дві цілих тридцять чотири сотих")
        assertCanonical("uk", NumberValue.Decimal(21, "1"), "двадцять одна ціла одна десята")
        assertCanonical("uk", NumberValue.Decimal(91, "01"), "дев'яносто одна ціла одна сота")
        assertAccepts("uk", NumberValue.Decimal(3, "7"), "три цілих і сім десятих")
    }

    /** The place comes from the digit COUNT, so a leading or trailing zero survives it. */
    @Test
    fun ukrainianDecimalPlacesFollowTheDigitStringNotItsValue() {
        assertCanonical("uk", NumberValue.Decimal(0, "05"), "нуль цілих п'ять сотих")
        assertCanonical("uk", NumberValue.Decimal(3, "40"), "три цілих сорок сотих")
        assertCanonical("uk", NumberValue.Decimal(11, "002"), "одинадцять цілих дві тисячних")
        assertAccepts("uk", NumberValue.Decimal(11, "002"), "одинадцять цілих дві тисячні")
    }

    /** Ukrainian has one decimal register, and "кома" is the mark's name, not a reading. */
    @Test
    fun ukrainianDecimalsNeverReadTheCommaAloud() {
        assertRejects(
            "uk", NumberValue.Decimal(3, "7"),
            "три кома сім", "три крапка сім",
        )
    }

    @Test
    fun ukrainianPercentAgreesWithTheLastDigit() {
        assertCanonical("uk", NumberValue.Percent(1), "один відсоток")
        assertCanonical("uk", NumberValue.Percent(2), "два відсотки")
        assertCanonical("uk", NumberValue.Percent(5), "п'ять відсотків")
        assertCanonical("uk", NumberValue.Percent(11), "одинадцять відсотків")
        assertCanonical("uk", NumberValue.Percent(14), "чотирнадцять відсотків")
        assertCanonical("uk", NumberValue.Percent(21), "двадцять один відсоток")
        assertCanonical("uk", NumberValue.Percent(45), "сорок п'ять відсотків")
        assertCanonical("uk", NumberValue.Percent(100), "сто відсотків")
        assertAccepts("uk", NumberValue.Percent(45), "сорок п'ять процентів")
        assertRejects("uk", NumberValue.Percent(1), "одна відсоток")
    }

    @Test
    fun ukrainianMultiplicativesCountRaziv() {
        assertCanonical("uk", NumberValue.Multiplicative(1), "один раз")
        assertAccepts("uk", NumberValue.Multiplicative(1), "раз")
        assertCanonical("uk", NumberValue.Multiplicative(2), "два рази")
        assertAccepts("uk", NumberValue.Multiplicative(2), "двічі")
        assertCanonical("uk", NumberValue.Multiplicative(3), "три рази")
        assertAccepts("uk", NumberValue.Multiplicative(3), "тричі")
        assertCanonical("uk", NumberValue.Multiplicative(5), "п'ять разів")
        assertCanonical("uk", NumberValue.Multiplicative(11), "одинадцять разів")
        assertCanonical("uk", NumberValue.Multiplicative(21), "двадцять один раз")
        assertCanonical("uk", NumberValue.Multiplicative(100), "сто разів")
    }

    /** "N-fold" is a factor, not a count of occasions, and "раза" belongs after півтора. */
    @Test
    fun ukrainianMultiplicativesRefuseTheFoldAdverbs() {
        assertRejects("uk", NumberValue.Multiplicative(2), "удвічі", "два раза")
        assertRejects("uk", NumberValue.Multiplicative(3), "утричі")
        assertRejects("uk", NumberValue.Multiplicative(4), "учетверо")
    }

    @Test
    fun ukrainianFractionsPutTheDenominatorInTheGenitivePlural() {
        assertCanonical("uk", NumberValue.Fraction(1, 2), "одна друга")
        assertCanonical("uk", NumberValue.Fraction(1, 8), "одна восьма")
        assertCanonical("uk", NumberValue.Fraction(1, 12), "одна дванадцята")
        assertCanonical("uk", NumberValue.Fraction(2, 3), "дві третіх")
        assertCanonical("uk", NumberValue.Fraction(3, 4), "три четвертих")
        assertCanonical("uk", NumberValue.Fraction(2, 7), "дві сьомих")
        assertCanonical("uk", NumberValue.Fraction(5, 6), "п'ять шостих")
        assertCanonical("uk", NumberValue.Fraction(7, 12), "сім дванадцятих")
        assertCanonical("uk", NumberValue.Fraction(9, 10), "дев'ять десятих")
    }

    /** The 2007 edition's nominative plural for numerators 2–4 is still in wide print. */
    @Test
    fun ukrainianFractionsAlsoTakeTheOlderNominativePlural() {
        assertAccepts("uk", NumberValue.Fraction(2, 3), "дві треті")
        assertAccepts("uk", NumberValue.Fraction(3, 4), "три четверті")
        assertAccepts("uk", NumberValue.Fraction(2, 7), "дві сьомі")
        assertRejects("uk", NumberValue.Fraction(5, 6), "п'ять шості")
    }

    /** The -ин suffix already means one, so the noun grades bare and never after "одна". */
    @Test
    fun ukrainianUnitFractionNounsGradeOnlyBare() {
        assertAccepts("uk", NumberValue.Fraction(1, 2), "половина")
        assertAccepts("uk", NumberValue.Fraction(1, 3), "третина")
        assertAccepts("uk", NumberValue.Fraction(1, 4), "чверть")
        assertRejects("uk", NumberValue.Fraction(1, 3), "одна третина")
        assertRejects("uk", NumberValue.Fraction(1, 4), "одна чверть")
        assertRejects("uk", NumberValue.Fraction(1, 2), "пів")
    }

    @Test
    fun ukrainianOrdinalsInflectOnlyTheLastWord() {
        assertCanonical("uk", NumberValue.Ordinal(1), "перший")
        assertAccepts("uk", NumberValue.Ordinal(1), "перша", "перше")
        assertCanonical("uk", NumberValue.Ordinal(3), "третій")
        assertAccepts("uk", NumberValue.Ordinal(3), "третя", "третє")
        assertCanonical("uk", NumberValue.Ordinal(4), "четвертий")
        assertCanonical("uk", NumberValue.Ordinal(6), "шостий")
        assertCanonical("uk", NumberValue.Ordinal(7), "сьомий")
        assertCanonical("uk", NumberValue.Ordinal(8), "восьмий")
        assertCanonical("uk", NumberValue.Ordinal(13), "тринадцятий")
        assertCanonical("uk", NumberValue.Ordinal(20), "двадцятий")
        assertCanonical("uk", NumberValue.Ordinal(21), "двадцять перший")
        assertAccepts("uk", NumberValue.Ordinal(21), "двадцять перша", "двадцять перше")
        assertCanonical("uk", NumberValue.Ordinal(40), "сороковий")
        assertCanonical("uk", NumberValue.Ordinal(50), "п'ятдесятий")
        assertCanonical("uk", NumberValue.Ordinal(90), "дев'яностий")
        assertCanonical("uk", NumberValue.Ordinal(99), "дев'яносто дев'ятий")
        assertCanonical("uk", NumberValue.Ordinal(100), "сотий")
    }

    // Swahili

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

    // French

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

    /**
     * A forms task names the form it asks, so the app can introduce a mark the first time
     * it appears instead of leaving it to be discovered in a failure. Every other kind
     * leaves the key null — nothing else has a notation to introduce.
     */
    @Test
    fun everyFormsTaskNamesItsForm() {
        val rng = Random(41)
        val keys = mutableSetOf<String>()
        repeat(400) {
            val task = Trainer.sample(TrainerKind.Forms, "de", FORMS_MAX_LEVEL, rng)
            keys += assertNotNull(task.formKey, task.prompt)
        }
        assertEquals(NumberForm.entries.map { it.key }.toSet(), keys)
        for (kind in listOf(TrainerKind.Numbers, TrainerKind.Years, TrainerKind.Clock)) {
            assertNull(Trainer.sample(kind, "de", 1, Random(7)).formKey, "$kind")
        }
    }
}
