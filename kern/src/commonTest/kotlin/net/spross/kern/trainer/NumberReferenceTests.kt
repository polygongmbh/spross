package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reference page is generated, which makes it the app's most public claim about
 * a language. The invariant that keeps that safe is asserted here: every reading is
 * the reading the drill itself would grade, for every authored language.
 */
class NumberReferenceTests {

    private val expectedKeys =
        listOf("ones", "teens", "tens", "twenties", "compounds", "hundreds", "places")

    @Test
    fun everyAuthoredLanguageGetsTheSameSections() {
        for (language in Trainer.languages) {
            val table = Trainer.reference(language)
            assertEquals(expectedKeys, table.map { it.key }, language)
            for (section in table) {
                assertTrue(section.entries.isNotEmpty(), "$language ${section.key}")
                for (entry in section.entries) {
                    assertTrue(entry.reading.isNotBlank(), "$language ${entry.value}")
                }
            }
        }
    }

    /** The table cannot drift from the generator, because it IS the generator. */
    @Test
    fun everyReadingIsWhatTheDrillWouldAsk() {
        for (language in Trainer.languages) {
            for (section in Trainer.reference(language)) {
                for (entry in section.entries) {
                    val value = entry.value.filter { it.isDigit() }.toLong()
                    assertEquals(
                        Trainer.number(value, language).display,
                        entry.reading,
                        "$language ${section.key} ${entry.value}",
                    )
                }
            }
        }
    }

    @Test
    fun theBandsCoverTheValuesTheLadderReaches() {
        val values = Trainer.reference("de").associate { it.key to it.entries.map { e -> e.value } }
        assertEquals((0..9).map { it.toString() }, values["ones"])
        assertEquals((10..19).map { it.toString() }, values["teens"])
        assertEquals((2..9).map { "${it}0" }, values["tens"])
        assertEquals((21..29).map { it.toString() }, values["twenties"])
        assertEquals(listOf("31", "45", "99"), values["compounds"])
        assertEquals(listOf("100", "101") + (2..9).map { "${it}00" }, values["hundreds"])
        // Long values are written the way a prompt writes them.
        assertEquals(
            listOf("1000", "2000", "5000", "1\u202F000\u202F000", "2\u202F000\u202F000", "1\u202F000\u202F000\u202F000"),
            values["places"],
        )
    }

    /**
     * The Swahili tens look-up survives whole, one section up for 10 — which is where
     * its own generator puts it. Nothing is lost, so the old API has no reason to stay.
     */
    @Test
    fun swahilisTensLookUpSurvivesInsideTheTable() {
        val rows = Trainer.reference("sw").flatMap { it.entries }.associate { it.value to it.reading }
        for (line in SwahiliNumbers.tensReference) {
            val (value, reading) = line.split(" ", limit = 2)
            assertEquals(reading, rows[value], "tensReference row \"$line\"")
        }
        assertEquals("kumi", rows["10"])
        assertEquals("tisini", rows["90"])
    }

    /** Values that would otherwise never be seen: 0 and the one non-round hundred. */
    @Test
    fun theTableCarriesTheWordsTheDrillHides() {
        val de = Trainer.reference("de").flatMap { it.entries }.associate { it.value to it.reading }
        assertEquals("null", de["0"])
        assertEquals("einhunderteins", de["101"])
        val es = Trainer.reference("es").flatMap { it.entries }.associate { it.value to it.reading }
        assertEquals("cien", es["100"])
        assertTrue(es["101"]!!.startsWith("ciento"), es["101"]!!)
        assertEquals("treinta y uno", es["31"])
    }
}
