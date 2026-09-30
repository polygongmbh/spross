package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the bedtime chip reads while a run plays down to it, and when it next looks. */
class SleepTimerTest {

    /**
     * Rounded UP, so the chip only reaches zero when the run is actually over — a timer that
     * showed no minutes left while the phone was still talking would read as broken.
     */
    @Test
    fun apartMinuteStillCountsAsAMinute() {
        assertEquals(2, sleepTimerMinutes(90_000L))
    }

    /** A deadline already past has nothing left rather than a negative count. */
    @Test
    fun anExpiredBedtimeReadsZero() {
        assertEquals(0, sleepTimerMinutes(-5_000L))
    }

    /**
     * The chip is woken by the MINUTE turning, never by the second: a bedtime with 15:20 left
     * shows 16 for another twenty seconds, and the last wake lands on the bedtime itself.
     */
    @Test
    fun theWakeLandsOnTheMinuteTurning() {
        assertEquals(20_000L, msUntilTheMinuteTurns(15 * 60_000L + 20_000L))
    }

    /** Never a zero delay: a bedtime already reached must not spin the loop. */
    @Test
    fun anExpiredBedtimeStillWaits() {
        assertTrue(msUntilTheMinuteTurns(-1_000L) > 0)
    }
}
