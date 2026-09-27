package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How each language writes and reads a phone number (`docs/phone-readings.md`).
 * The expectations are the sources' readings, never read back off the generator.
 */
class PhoneReadingsTests {

    private fun task(language: String, digits: String) = Numbers.phone(digits, language)

    private fun assertReads(language: String, digits: String, grouped: String, canonical: String, vararg also: String) {
        val task = task(language, digits)
        assertEquals(grouped, task.promptDisplay.replace(' ', ' '), language)
        assertEquals(canonical, task.display, language)
        for (reading in also) assertTrue(reading in task.accepted, "$language: \"$reading\" missing from ${task.accepted}")
    }

    @Test
    fun germanReadsDigitByDigitOrTheSubscriberInPairs() {
        assertReads(
            "de", "017612345678", "0176 12345678",
            "null eins sieben sechs, eins zwei drei vier fünf sechs sieben acht",
            "null eins sieben sechs, eins zwo drei vier fünf sechs sieben acht",
            "null eins sieben sechs, zwölf, vierunddreißig, sechsundfünfzig, achtundsiebzig",
        )
        assertTrue("null eins sieben sechs, null fünf, zwölf, vierunddreißig, sechsundfünfzig" in
            task("de", "017605123456").accepted)
    }

    @Test
    fun frenchReadsInPairs() {
        assertReads(
            "fr", "0612345678", "06 12 34 56 78",
            "zéro six, douze, trente-quatre, cinquante-six, soixante-dix-huit",
            "zéro six, douze, trente-quatre, cinquante-six, septante-huit",
        )
        assertEquals("zéro six, zéro zéro, zéro cinq, dix, onze", task("fr", "0600051011").display)
    }

    @Test
    fun englishReadsEveryDigitWithZeroOrOh() {
        assertReads(
            "en", "2125550198", "212 555 0198",
            "two one two, five five five, zero one nine eight",
            "two one two, five five five, oh one nine eight",
        )
    }

    @Test
    fun spanishReadsEachGroupAsItsNumber() {
        assertReads(
            "es", "612345678", "612 345 678",
            "seiscientos doce, trescientos cuarenta y cinco, seiscientos setenta y ocho",
            "seis uno dos, tres cuatro cinco, seis siete ocho",
        )
        assertEquals("seiscientos doce, cero cuarenta y cinco, cero cero cinco", task("es", "612045005").display)
    }

    @Test
    fun italianReadsEveryDigit() {
        assertReads("it", "3471234567", "347 123 4567", "tre quattro sette, uno due tre, quattro cinque sei sette")
    }

    @Test
    fun ukrainianReadsEachGroupAsItsNumber() {
        assertReads(
            "uk", "0671234567", "067 123 45 67",
            "нуль шістдесят сім, сто двадцять три, сорок п'ять, шістдесят сім",
            "нуль шість сім, один два три, чотири п'ять, шість сім",
        )
        assertEquals("нуль шістдесят сім, нуль сорок п'ять, нуль п'ять, нуль нуль", task("uk", "0670450500").display)
    }

    @Test
    fun swahiliAndEsperantoReadEveryDigit() {
        assertReads("sw", "0712345678", "0712 345 678", "sifuri saba moja mbili, tatu nne tano, sita saba nane")
        assertReads(
            "eo", "0612345678", "0612 345 678",
            "nul ses unu du, tri kvar kvin, ses sep ok",
            "nulo ses unu du, tri kvar kvin, ses sep ok",
        )
    }

    /** A style is never mixed inside one number: every zero is "zero", or every one is "oh". */
    @Test
    fun oneNumberIsReadInOneStyle() {
        assertFalse("two one two, five five five, zero one oh eight" in task("en", "2125550108").accepted)
    }

    @Test
    fun everyLanguageDrawsANumberOfItsOwnPlan() {
        val rng = Random(0xF0E)
        for (language in Numbers.languages) {
            assertTrue(Numbers.supportsSlot(NumbersReading.Phone, language), language)
            repeat(50) {
                val slot = Numbers.sample(NumbersReading.Phone, language, 1, rng)
                assertEquals(NumbersReading.Phone, slot.kind)
                assertTrue(slot.prompt.all(Char::isDigit), "$language ${slot.prompt}")
                assertEquals(slot.prompt, slot.promptDisplay.filter(Char::isDigit), language)
                assertTrue(slot.display in slot.accepted, language)
            }
        }
    }
}
