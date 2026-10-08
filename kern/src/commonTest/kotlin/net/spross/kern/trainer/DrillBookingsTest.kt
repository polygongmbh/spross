package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a closed run files: one rule set, whichever drill it was. */
class DrillBookingsTest {

    private fun typed(summary: DrillRunSummary?) = DrillBookings.typed(
        Drill.Countries, "countries.en-de", "de", reverse = false, summary, bestSprosse = 3, cleared = setOf(1, 2),
    )

    @Test
    fun anUnansweredRunStillStoodOnItsSprosseButCountsForNoDay() {
        val bookings = typed(summary = null)
        assertEquals(mapOf("countries.en-de" to 3), bookings.sprossen)
        assertNull(bookings.lastRun)
        assertEquals(0, bookings.dayAnswers)
    }

    @Test
    fun anAnsweredRunStampsItsDayAndItsLength() {
        val bookings = typed(DrillRunSummary(done = 9, bestAnswerStreak = 4, newRecord = false))
        assertEquals(DrillSuggestion.lastRunKey(Drill.Countries, "de"), bookings.lastRun)
        assertEquals(9, bookings.dayAnswers)
        assertEquals(mapOf("countries.en-de" to 9), bookings.answers)
        assertTrue(bookings.records.isEmpty())
    }

    @Test
    fun aRecordIsFiledOnlyOnceBeaten() {
        val bookings = typed(DrillRunSummary(done = 9, bestAnswerStreak = 7, newRecord = true))
        assertEquals(mapOf("countries.en-de" to 7), bookings.records)
    }
}
