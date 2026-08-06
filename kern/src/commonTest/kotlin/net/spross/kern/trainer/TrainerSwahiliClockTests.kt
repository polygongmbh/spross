package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Swahili clock readings beyond what `trainer-golden.json` pins: the quarter words, the
 * additive reading past the half hour, `kamili`, and the parts of the day.
 */
class TrainerSwahiliClockTests {

    private fun clock(hour: Int, minute: Int) = Trainer.clock(hour, minute, "sw")

    /** Half of everything a level-2 learner sees is a quarter hour. */
    @Test
    fun theQuarterHoursHaveTheirOwnWords() {
        assertEquals("Saa tisa na robo mchana", clock(15, 15).display)
        assertEquals("Saa kumi kasorobo mchana", clock(15, 45).display)
        // The spelt-out register stays accepted beside them.
        assertTrue("Saa tisa na dakika kumi na tano" in clock(15, 15).accepted)
        assertTrue("Saa kumi kasoro dakika kumi na tano" in clock(15, 45).accepted)
        assertTrue("Saa kumi kasoro robo" in clock(15, 45).accepted)
    }

    /**
     * Past the half hour the additive reading counts on the CURRENT saa hour — one hour
     * before the countdown's. Getting this backwards is a one-hour error in 720 times.
     */
    @Test
    fun theAdditiveReadingKeepsTheCurrentSaaHourPastTheHalf() {
        val quarterToTwelve = clock(23, 45)
        assertTrue("Saa tano na dakika arobaini na tano" in quarterToTwelve.accepted)
        assertTrue("Saa sita kasorobo" in quarterToTwelve.accepted)
        assertTrue("Saa sita kasoro tano" in clock(23, 55).accepted)
        assertTrue("Saa saba na dakika thelathini" in clock(13, 30).accepted)
    }

    @Test
    fun theExactHourTakesKamiliBeforeTheDayPart() {
        val eight = clock(20, 0)
        assertEquals("Saa mbili usiku", eight.display)
        assertTrue("Saa mbili kamili" in eight.accepted)
        assertTrue("Saa mbili kamili usiku" in eight.accepted)
        assertTrue("Saa mbili usiku kamili" !in eight.accepted)
    }

    @Test
    fun theDayPartsCoverDawnAndTheDeadOfNight() {
        // Four in the morning is still the night; dawn is alfajiri, not yet asubuhi.
        assertEquals("Saa kumi usiku", clock(4, 0).display)
        assertTrue("Saa kumi alfajiri" in clock(4, 0).accepted)
        assertEquals("Saa kumi na moja alfajiri", clock(5, 0).display)
        assertTrue("Saa sita usiku wa manane" in clock(0, 0).accepted)
        // alasiri is the late afternoon only, and never leads.
        assertTrue("Saa kumi alasiri" in clock(16, 0).accepted)
        assertTrue(SwahiliClock.dayParts(13).none { it == "alasiri" })
        assertEquals("jioni", SwahiliClock.dayParts(16)[0])
        // Every day part the reveal names is one word, so the golden test can strip it.
        for (hour in 0..23) assertTrue(" " !in SwahiliClock.dayParts(hour)[0], "hour $hour")
    }
}
