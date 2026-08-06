package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * German clock accepted-set behavior: the display stays the 12-hour standard
 * ("sechs Uhr", "Viertel nach eins"), while the accepted set additionally
 * carries the colloquial "um <hour>" full-hour reading and the formal
 * 24-hour reading ("achtzehn Uhr fünfunddreißig").
 */
class TrainerGermanClockTests {

    private fun clock(hour: Int, minute: Int) = Trainer.clock(hour, minute, "de")

    /** The UI compares normalize-insensitively; lowercase mirrors its fallback. */
    private fun assertAccepts(task: TrainerTask, typed: String) {
        val normalized = typed.lowercase()
        assertTrue(
            task.accepted.any { it.lowercase() == normalized },
            "\"$typed\" not accepted for ${task.prompt}: ${task.accepted}",
        )
    }

    @Test
    fun fullHourAccepts24HourReadingDisplayStays12Hour() {
        val task = clock(18, 0)
        assertEquals("sechs Uhr", task.display)
        assertAccepts(task, "achtzehn uhr")
        // The colloquial "um sechs" full-hour reading stays accepted.
        assertAccepts(task, "um sechs")
    }

    @Test
    fun minutesAppendTheirCardinalTo24HourReading() {
        val task = clock(18, 35)
        assertEquals("fünf nach halb sieben", task.display)
        assertAccepts(task, "achtzehn uhr fünfunddreißig")
    }

    @Test
    fun midnightAcceptsNullUhrAndVierundzwanzigUhr() {
        val task = clock(0, 0)
        assertEquals("Mitternacht", task.display)
        assertAccepts(task, "null uhr")
        assertAccepts(task, "vierundzwanzig uhr")
        // The colloquial full-hour readings were dropped at exactly the two hours a
        // speaker says them out loud, because the standard reading is a name there.
        assertAccepts(task, "zwölf uhr")
        assertAccepts(task, "um zwölf")
        assertAccepts(task, "zwölf uhr nachts")
        val noon = clock(12, 0)
        assertEquals("Mittag", noon.display)
        assertAccepts(noon, "zwölf uhr mittags")
    }

    /** Where the hour sits in the day, at the full hour — and the apocope holds. */
    @Test
    fun theFullHourAcceptsTheDayPart() {
        assertAccepts(clock(1, 0), "ein uhr nachts")
        assertAccepts(clock(13, 0), "ein uhr nachmittags")
        assertAccepts(clock(9, 0), "neun uhr vormittags")
        assertAccepts(clock(21, 0), "neun uhr abends")
        assertAccepts(clock(5, 0), "fünf uhr früh")
        assertAccepts(clock(18, 0), "sechs uhr abends")
        assertAccepts(clock(6, 0), "sechs uhr morgens")
        for (hour in 0..23) {
            assertFalse(clock(hour, 0).accepted.any { "eins Uhr" in it }, "hour $hour")
        }
    }

    /** The half hour counts from ten out either side, and "Minuten" is optional. */
    @Test
    fun tenEitherSideOfHalfAndTheOptionalMinuten() {
        assertAccepts(clock(18, 20), "zehn vor halb sieben")
        assertAccepts(clock(18, 40), "zehn nach halb sieben")
        assertAccepts(clock(18, 5), "fünf minuten nach sechs")
        assertAccepts(clock(18, 40), "zwanzig minuten vor sieben")
        assertAccepts(clock(18, 25), "fünf minuten vor halb sieben")
        // "Viertel Minuten nach" is not a reading.
        assertFalse(clock(18, 15).accepted.any { "Minuten" in it }, clock(18, 15).accepted.toString())
    }

    /** The reveal names alternatives, not every way the time can be said. */
    @Test
    fun theGlossStaysShort() {
        for (hour in 0..23) {
            for (minute in listOf(0, 15, 20, 30, 45)) {
                val gloss = clock(hour, minute).gloss ?: continue
                assertTrue(gloss.split(" oder ").size <= 3, "$hour:$minute → $gloss")
            }
        }
    }

    @Test
    fun quarterPastKeepsConversationalFormsNextTo24HourReading() {
        val task = clock(13, 15)
        assertEquals("Viertel nach eins", task.display)
        assertAccepts(task, "dreizehn uhr fünfzehn")
        // Existing conversational forms still pass.
        assertAccepts(task, "viertel nach eins")
        assertAccepts(task, "viertel zwei")
    }

    @Test
    fun morningFullHourDoesNotDuplicateItsOwn24HourReading() {
        val task = clock(6, 0)
        assertEquals("sechs Uhr", task.display)
        assertEquals(task.accepted.size, task.accepted.toSet().size, "duplicates in ${task.accepted}")
        assertAccepts(task, "sechs uhr")
    }

    @Test
    fun oneOClockApocopatesTheHourWordBeforeUhr() {
        val task = clock(1, 0)
        assertEquals("ein Uhr", task.display)
        assertAccepts(task, "ein uhr")
        // Bare "eins" (no "Uhr" following) keeps its full form.
        assertAccepts(task, "um eins")
        assertFalse(task.accepted.any { it.lowercase() == "eins uhr" }, "wrong 'eins Uhr' in ${task.accepted}")
    }

    @Test
    fun thirteenOClockAcceptsDreizehnUhrNextToEinUhr() {
        val task = clock(13, 0)
        assertEquals("ein Uhr", task.display)
        assertAccepts(task, "ein uhr")
        assertAccepts(task, "dreizehn uhr")
        assertFalse(task.accepted.any { it.lowercase() == "eins uhr" }, "wrong 'eins Uhr' in ${task.accepted}")
    }

    @Test
    fun fivePastOneKeepsBareEinsButReads24HourEinUhrFuenf() {
        val task = clock(1, 5)
        assertEquals("fünf nach eins", task.display)
        assertAccepts(task, "ein uhr fünf")
        val nonRound = clock(13, 17)
        assertEquals("ein Uhr siebzehn", nonRound.display)
        assertAccepts(nonRound, "dreizehn uhr siebzehn")
    }

    @Test
    fun nonRoundMinutesAccept24HourReadingSpelledOut() {
        val task = clock(21, 17)
        assertEquals("neun Uhr siebzehn", task.display)
        assertAccepts(task, "einundzwanzig uhr siebzehn")
        // No reading anywhere in the drill answers a digital prompt with digits.
        assertFalse(task.accepted.any { it.any(Char::isDigit) }, task.accepted.toString())
    }
}
