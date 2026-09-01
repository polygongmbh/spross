package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The VOCABULARY SPEC for the day of the month, authored from `docs/date-readings.md`
 * (Duden and DWDS for German; the Chicago Manual's date forms for English; the
 * Fundamenta's `-a` for Esperanto; RAE's DPD on `el primero`/`el uno` for Spanish; the
 * Académie française and the BDL on `le premier` for French; the Accademia della Crusca on
 * `il primo` for Italian; TUKI and the TIE Kiswahili series for `tarehe mosi`; Український
 * правопис 2019 on the genitive date for Ukrainian), never read back off the generator.
 *
 * Canonical is what a reveal teaches; every other element only grades. A reading that
 * changes here is a claim about the language, not about the code.
 */
class TrainerDateReadingTests {

    private fun readings(language: String, day: Int): List<String> = Trainer.pack(language).dateDay(day)

    private fun assertCanonical(language: String, day: Int, expected: String) {
        assertEquals(expected, readings(language, day).firstOrNull(), "$language $day")
    }

    private fun assertAccepts(language: String, day: Int, vararg forms: String) {
        val all = readings(language, day)
        for (form in forms) assertTrue(form in all, "$language $day: \"$form\" missing from $all")
    }

    private fun assertRefuses(language: String, day: Int, vararg forms: String) {
        val all = readings(language, day)
        for (form in forms) assertTrue(form !in all, "$language $day: \"$form\" must not grade")
    }

    /** `Montag, der 3. März` reads *der dritte März* — the weak -e, the accusative along. */
    @Test
    fun germanReadsTheWeakOrdinal() {
        assertCanonical("de", 1, "erste")
        assertCanonical("de", 3, "dritte")
        assertAccepts("de", 3, "dritter", "dritten", "drittes")
        assertCanonical("de", 7, "siebte")
        assertAccepts("de", 7, "siebente")
        assertCanonical("de", 20, "zwanzigste")
        assertCanonical("de", 31, "einunddreißigste")
    }

    /** `March 3rd` is spoken *March third*; the reading is the skill, so no digits grade. */
    @Test
    fun englishReadsTheOrdinalInWordsAlone() {
        assertEquals(listOf("first"), readings("en", 1))
        assertEquals(listOf("third"), readings("en", 3))
        assertCanonical("en", 21, "twenty-first")
        assertAccepts("en", 21, "twenty first")
        assertCanonical("en", 31, "thirty-first")
        assertRefuses("en", 3, "3rd", "three")
    }

    /** Fully regular `-a`, no first-day exception, and the x-system twin behind every one. */
    @Test
    fun esperantoReadsTheOrdinalWithItsXSystemTwin() {
        assertEquals(listOf("unua"), readings("eo", 1))
        assertCanonical("eo", 3, "tria")
        assertCanonical("eo", 9, "naŭa")
        assertAccepts("eo", 9, "nauxa")
        assertCanonical("eo", 21, "dudek-unua")
    }

    /** ⚠ The reveal teaches `primero` (most of Latin America); Spain's `uno` grades. */
    @Test
    fun spanishCountsTheDayAndSplitsOnTheFirst() {
        assertEquals(listOf("primero", "uno"), readings("es", 1))
        assertEquals(listOf("tres"), readings("es", 3))
        assertCanonical("es", 31, "treinta y uno")
        assertRefuses("es", 3, "tercero")
    }

    /** `le premier mars`, never `le un mars` — and a date day is masculine. */
    @Test
    fun frenchTakesPremierForTheFirstAndCountsTheRest() {
        assertEquals(listOf("premier"), readings("fr", 1))
        assertEquals(listOf("trois"), readings("fr", 3))
        assertCanonical("fr", 21, "vingt et un")
        assertRefuses("fr", 1, "un", "première")
    }

    /** `il primo marzo` is the one exception; the feminine belongs to no date. */
    @Test
    fun italianTakesPrimoForTheFirstAndCountsTheRest() {
        assertEquals(listOf("primo"), readings("it", 1))
        assertCanonical("it", 3, "tre")
        assertCanonical("it", 21, "ventuno")
        assertRefuses("it", 1, "uno", "prima")
    }

    /** ⚠ `tarehe mosi` is the conventional 1st; the plain cardinal grades beside it. */
    @Test
    fun swahiliCountsTheDayAndKeepsMosiForTheFirst() {
        assertEquals(listOf("mosi", "moja"), readings("sw", 1))
        assertCanonical("sw", 3, "tatu")
        assertCanonical("sw", 21, "ishirini na moja")
        assertAccepts("sw", 21, "ishirini moja")
    }

    /** `3 березня` reads *третього березня*: the genitive, and only the genitive. */
    @Test
    fun ukrainianReadsTheGenitiveOrdinal() {
        assertEquals(listOf("першого"), readings("uk", 1))
        assertEquals(listOf("третього"), readings("uk", 3))
        assertEquals(listOf("двадцять першого"), readings("uk", 21))
        assertEquals(listOf("двадцять третього"), readings("uk", 23))
        assertEquals(listOf("тридцять першого"), readings("uk", 31))
        assertRefuses("uk", 3, "третій", "третя", "три")
    }

    /**
     * Every authored pack reads every day of every month. The ladder asks for one of these
     * thirty-one and nothing else, so a pack falling through would offer a blank answer to
     * a question it had already posed.
     */
    @Test
    fun everyPackReadsEveryDayOfTheMonth() {
        for (language in Trainer.languages) {
            for (day in 1..31) {
                val all = readings(language, day)
                assertTrue(all.isNotEmpty(), "$language: no reading for day $day")
                assertTrue(all.all { it.isNotBlank() && it.trim() == it }, "$language $day: $all")
                assertEquals(all.distinct(), all, "$language $day: repeated reading")
            }
        }
    }
}
