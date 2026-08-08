package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reference page is generated, which makes it the app's most public claim about
 * a language. The invariant that keeps that safe is asserted here: every reading is
 * the reading the drill itself would grade, for every authored language.
 */
class NumberReferenceTests {

    private val cardinalKeys =
        listOf("ones", "teens", "tens", "twenties", "compounds", "hundreds", "places")

    @Test
    fun everyAuthoredLanguageGetsTheSameSections() {
        for (language in Trainer.languages) {
            val table = Trainer.reference(language)
            assertEquals(cardinalKeys, table.map { it.key }.filterNot { it == "forms" }, language)
            for (section in table) {
                assertTrue(section.entries.isNotEmpty(), "$language ${section.key}")
                for (entry in section.entries) {
                    assertTrue(entry.reading.isNotBlank(), "$language ${entry.value}")
                }
            }
        }
    }

    /**
     * The forms band exists exactly where the Forms drill does, and never offers a form
     * the language cannot read — the reach that keeps a form out of the drill keeps it
     * off the page, so nothing is written down that could not also be asked.
     */
    @Test
    fun theFormsBandFollowsTheLanguagesOwnReach() {
        for (language in Trainer.languages) {
            val band = Trainer.reference(language).firstOrNull { it.key == "forms" }
            if (!Trainer.supportsForms(language)) {
                assertNull(band, language)
                continue
            }
            val rows = assertNotNull(band, language).entries
            val reads = NumberForm.entries.count { it in Trainer.pack(language).formLimits.forms }
            assertTrue(rows.isNotEmpty() && rows.size <= reads, "$language: ${rows.size} of $reads")
            for (row in rows) assertTrue(row.reading.isNotBlank(), "$language ${row.value}")
        }
    }

    /**
     * What a learner actually reads there — one worked example per mark, and the mark is
     * the point: the decimal row carries the language's own separator, so German's comma
     * and English's point are two different rows of the same band.
     */
    @Test
    fun theFormsBandNamesEveryMarkOnce() {
        val de = Trainer.reference("de").first { it.key == "forms" }.entries
        assertEquals(listOf("-7", "3,5", "25 %", "3×", "1/2", "1."), de.map { it.value })
        assertEquals(
            listOf("minus sieben", "drei Komma fünf", "fünfundzwanzig Prozent",
                   "dreimal", "ein halb", "erste"),
            de.map { it.reading },
        )
        val en = Trainer.reference("en").first { it.key == "forms" }.entries
        assertEquals("3.5", en[1].value)
        assertEquals("three point five", en[1].reading)
        // Swahili ranks nothing without the noun it ranks, so it gets no ordinal row.
        val sw = Trainer.reference("sw").first { it.key == "forms" }.entries
        assertTrue(sw.none { it.value.endsWith(".") }, sw.joinToString { it.value })
    }

    /** The table cannot drift from the generator, because it IS the generator. */
    @Test
    fun everyReadingIsWhatTheDrillWouldAsk() {
        for (language in Trainer.languages) {
            for (section in Trainer.reference(language).filterNot { it.key == "forms" }) {
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
