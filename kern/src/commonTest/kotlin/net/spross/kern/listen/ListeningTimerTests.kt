package net.spross.kern.listen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The bedtime a run can be given: what it offers, and how it stops. */
class ListeningTimerTests {

    /**
     * RULE: the choices lead with off, and off is 0.
     * WHY: a playlist that laps until it is closed is the default — the timer is asked for at
     * bedtime and nowhere else, and a cycling chip has to start where the run already is.
     */
    @Test
    fun theTimerChoicesLeadWithOff() {
        assertEquals(0, LISTENING_TIMER_CHOICES_MIN.first())
        assertEquals(LISTENING_TIMER_CHOICES_MIN.sorted(), LISTENING_TIMER_CHOICES_MIN)
        assertTrue(LISTENING_TIMER_CHOICES_MIN.drop(1).all { it > 0 })
    }

    /**
     * RULE: the run plays at full level until the fade window opens.
     * WHY: the fade is the ending, not the run — a timer that quietened the whole hour would
     * be a volume slider nobody asked for.
     */
    @Test
    fun theGainIsFlatOutsideTheFadeWindow() {
        assertEquals(0.0, listeningGainDb(LISTENING_FADE_MS))
        assertEquals(0.0, listeningGainDb(LISTENING_FADE_MS + 1))
        assertEquals(0.0, listeningGainDb(60 * 60_000))
    }

    /**
     * RULE: inside the window the level only ever falls, and it reaches the floor at zero.
     * WHY: a fade that stepped back up would be a change loud enough to notice, which is what
     * the fade exists to avoid — and it has to actually arrive at silence, or the timer never
     * ends.
     */
    @Test
    fun theGainFallsMonotonicallyToTheFloor() {
        var previous = 0.0
        for (step in 0..20) {
            val remaining = LISTENING_FADE_MS - step * (LISTENING_FADE_MS / 20)
            val gain = listeningGainDb(remaining)
            assertTrue(gain <= previous, "the fade rose at ${remaining}ms: $gain > $previous")
            previous = gain
        }
        assertEquals(LISTENING_FADE_FLOOR_DB, listeningGainDb(0))
        assertEquals(LISTENING_FADE_FLOOR_DB / 2, listeningGainDb(LISTENING_FADE_MS / 2))
    }

    /**
     * RULE: the gain never leaves [LISTENING_FADE_FLOOR_DB]..0.0, whatever it is handed.
     * WHY: a player is handed this number directly, and an overshoot is either a boost at
     * bedtime or an amplifier asked for a value it cannot make. A negative remainder is an
     * ordinary late tick, not an error.
     */
    @Test
    fun theGainStaysInsideItsClamp() {
        for (ms in listOf(-60_000L, -1L, 0L, 1L, 999L, LISTENING_FADE_MS, Long.MAX_VALUE)) {
            val gain = listeningGainDb(ms)
            assertTrue(gain in LISTENING_FADE_FLOOR_DB..0.0, "gain $gain out of range at ${ms}ms")
        }
        assertEquals(LISTENING_FADE_FLOOR_DB, listeningGainDb(-60_000))
    }

    /**
     * RULE: the bedtime has arrived at zero, not after it.
     * WHY: one rule in one place — `<= 0` and `< 0` read the same until the two apps pick
     * differently and one of them plays a word past the fade's own silence.
     */
    @Test
    fun theTimerIsOverAtZero() {
        assertTrue(listeningExpired(0))
        assertTrue(listeningExpired(-1))
        assertFalse(listeningExpired(1))
    }
}
