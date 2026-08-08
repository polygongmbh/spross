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
     * The Swahili tens — the one look-up the app used to offer, and the reason the
     * table exists at all — read exactly as they always did, now inside a page every
     * language gets. 10 sits one band up, which is where its own generator puts it.
     */
    @Test
    fun swahilisTensLookUpSurvivesInsideTheTable() {
        val rows = Trainer.reference("sw").flatMap { it.entries }.associate { it.value to it.reading }
        val expected = listOf(
            "10" to "kumi", "20" to "ishirini", "30" to "thelathini", "40" to "arobaini",
            "50" to "hamsini", "60" to "sitini", "70" to "sabini", "80" to "themanini",
            "90" to "tisini",
        )
        for ((value, reading) in expected) {
            assertEquals(reading, rows[value], "tens row $value")
        }
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
