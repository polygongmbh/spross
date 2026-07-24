package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertAccepts(task, "punkt sechs")
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
    fun nonRoundMinutesAccept24HourReadingSpelledOut() {
        val task = clock(21, 17)
        assertEquals("neun Uhr 17", task.display)
        assertAccepts(task, "einundzwanzig uhr siebzehn")
    }
}
