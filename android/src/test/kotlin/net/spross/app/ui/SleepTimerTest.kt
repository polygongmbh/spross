package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** What the bedtime chip reads while a run plays down to it, and when it next looks. */
class SleepTimerTest {

    /**
     * Rounded UP, so the chip only reaches zero when the run is actually over — a timer that
     * showed no minutes left while the phone was still talking would read as broken.
     */
    @Test
    fun apartMinuteStillCountsAsAMinute() {
        assertEquals(1, sleepTimerMinutes(1L))
        assertEquals(1, sleepTimerMinutes(60_000L))
        assertEquals(2, sleepTimerMinutes(60_001L))
    }

    /** The longest bedtime kern offers is an hour, and it reads as sixty minutes. */
    @Test
    fun theHourReadsAsMinutes() {
        assertEquals(15, sleepTimerMinutes(15 * 60_000L))
        assertEquals(60, sleepTimerMinutes(60 * 60_000L))
    }

    /** A deadline already past has nothing left rather than a negative count. */
    @Test
    fun anExpiredBedtimeReadsZero() {
        assertEquals(0, sleepTimerMinutes(0L))
        assertEquals(0, sleepTimerMinutes(-5_000L))
    }

    /**
     * The chip is woken by the MINUTE turning, never by the second: a bedtime with 15:20 left
     * shows 16 for another twenty seconds, and the last wake lands on the bedtime itself.
     */
    @Test
    fun theWakeLandsOnTheMinuteTurning() {
        assertEquals(20_000L, msUntilTheMinuteTurns(15 * 60_000L + 20_000L))
        assertEquals(60_000L, msUntilTheMinuteTurns(60_000L))
        assertEquals(30_000L, msUntilTheMinuteTurns(30_000L))
    }

    /** Never a zero delay: a bedtime already reached must not spin the loop. */
    @Test
    fun anExpiredBedtimeStillWaits() {
        assertEquals(50L, msUntilTheMinuteTurns(0L))
        assertEquals(50L, msUntilTheMinuteTurns(-1_000L))
    }
}
