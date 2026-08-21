package net.spross.kern.listen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bedtime: where its ramp starts, where it ends, and that it never leaves the level kern chose. */
class ListeningTimerTests {

    private val hour = 60 * 60_000L

    /**
     * Off is the default and asks no arithmetic: a run with no bedtime plays at the level its
     * recordings were measured to, and kern's ramp never touches it.
     */
    @Test
    fun aRunWithoutABedtimePlaysAtFull() {
        assertEquals(0.0, listeningGainDb(hour, totalMs = 0))
        assertEquals(0.0, listeningGainDb(0, totalMs = 0))
        assertEquals(0.0, listeningGainDb(hour, totalMs = -1))
    }

    /**
     * The ramp is the WHOLE bedtime, not a window at the end of it: a fade that starts is a
     * second event, and a listener on the edge of sleep hears a change beginning long before
     * they hear a level continuing. So the first word is already a shade under the last full one.
     */
    @Test
    fun theRampSpansTheWholeBedtimeRatherThanItsEnd() {
        assertEquals(0.0, listeningGainDb(hour, totalMs = hour))
        assertEquals(LISTENING_FADE_FLOOR_DB / 2, listeningGainDb(hour / 2, totalMs = hour), 1e-9)
        assertEquals(LISTENING_FADE_FLOOR_DB / 4, listeningGainDb(hour * 3 / 4, totalMs = hour), 1e-9)
        assertEquals(LISTENING_FADE_FLOOR_DB, listeningGainDb(0, totalMs = hour))
    }

    /** Every length ends in the same place: the ramp is a fraction of the run, never a rate. */
    @Test
    fun everyBedtimeEndsAtTheSameLevel() {
        for (minutes in LISTENING_TIMER_CHOICES_MIN.filter { it > 0 }) {
            val total = minutes * 60_000L
            assertEquals(0.0, listeningGainDb(total, total))
            assertEquals(LISTENING_FADE_FLOOR_DB / 2, listeningGainDb(total / 2, total), 1e-9)
            assertEquals(LISTENING_FADE_FLOOR_DB, listeningGainDb(0, total))
        }
    }

    /** It only ever descends — a bedtime that got louder anywhere would be an event of its own. */
    @Test
    fun theRampNeverRises() {
        var previous = 1.0
        for (step in 0..40) {
            val gain = listeningGainDb(hour - step * (hour / 40), totalMs = hour)
            assertTrue(gain <= previous + 1e-9, "step \$step rose to \$gain")
            previous = gain
        }
    }

    /**
     * Held to the level kern chose, whatever a caller hands in — a clock that overshoots its
     * deadline must quieten the run, never invert the ramp.
     */
    @Test
    fun theRampStaysInsideItsOwnFloor() {
        for (ms in listOf(-hour, -1L, 0L, 1L, 999L, hour, Long.MAX_VALUE)) {
            val gain = listeningGainDb(ms, totalMs = hour)
            assertTrue(gain in LISTENING_FADE_FLOOR_DB..0.0, "\$ms gave \$gain")
        }
        assertEquals(LISTENING_FADE_FLOOR_DB, listeningGainDb(-hour, totalMs = hour))
    }

    /** Off leads the list: a run laps for as long as it is left alone unless asked otherwise. */
    @Test
    fun theChoicesLeadWithOff() {
        assertEquals(0, LISTENING_TIMER_CHOICES_MIN.first())
        assertTrue(LISTENING_TIMER_CHOICES_MIN.drop(1).all { it > 0 })
    }

    /** The bedtime has arrived at zero, and one rule decides it rather than two apps. */
    @Test
    fun theBedtimeArrivesAtZero() {
        assertTrue(listeningExpired(0))
        assertTrue(listeningExpired(-1))
        assertTrue(!listeningExpired(1))
    }
}
