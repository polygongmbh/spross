package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** What the bedtime chip reads while a run plays down to it. */
class SleepTimerClockTest {

    /**
     * Rounded UP, so the chip only reaches 0:00 when the run is actually over — a timer that
     * showed zero while the phone was still talking would read as broken.
     */
    @Test
    fun aPartSecondStillCountsAsASecond() {
        assertEquals("0:01", sleepTimerClock(1))
        assertEquals("0:01", sleepTimerClock(1_000))
        assertEquals("0:02", sleepTimerClock(1_001))
    }

    /** Seconds are two digits under the minutes, so the label never changes width. */
    @Test
    fun secondsArePaddedAndMinutesAreNot() {
        assertEquals("1:05", sleepTimerClock(65_000))
        assertEquals("15:00", sleepTimerClock(15 * 60_000L))
    }

    /** The longest bedtime kern offers is an hour, and it reads as sixty minutes, not 1:00:00. */
    @Test
    fun theHourIsReadAsMinutes() {
        assertEquals("60:00", sleepTimerClock(60 * 60_000L))
    }

    /** A deadline already past is 0:00 rather than a negative clock. */
    @Test
    fun anExpiredBedtimeReadsZero() {
        assertEquals("0:00", sleepTimerClock(0))
        assertEquals("0:00", sleepTimerClock(-5_000))
    }
}
