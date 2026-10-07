package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class GreetingPlanTests {

    private fun at(hour: Int) = LocalDateTime(2026, 7, 1, hour, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()

    @Test
    fun aKnownNameIsAddressedAtAnyHour() {
        assertEquals(Addressee.Learner, GreetingPlan(at(23), "UTC", "sw", learnerNamed = true).address)
    }

    @Test
    fun withoutANameOnlyMorningAndNightLendAWord() {
        assertEquals(Addressee.MorningWord, GreetingPlan(at(7), "UTC", "sw", learnerNamed = false).address)
        assertEquals(Addressee.NightWord, GreetingPlan(at(23), "UTC", "sw", learnerNamed = false).address)
        assertEquals(Addressee.Nobody, GreetingPlan(at(13), "UTC", "sw", learnerNamed = false).address)
    }

    @Test
    fun withNoSpokenLinesTheChromeAlwaysSpeaks() {
        val line = GreetingPlan(at(13), "UTC", "sw", learnerNamed = false).pick(spoken = 0, chrome = 2)
        assertTrue(!line.spoken && line.index in 0..1)
    }
}
