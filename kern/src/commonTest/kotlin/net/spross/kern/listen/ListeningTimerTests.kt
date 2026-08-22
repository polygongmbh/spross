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
        for (minutes in listOf(LISTENING_TIMER_STEP_MIN, 15, 45, 120)) {
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

    /** Off is where a run starts and the only place the timer may be reset to. */
    @Test
    fun theTimerStepsInFiveMinuteIncrements() {
        assertEquals(5, LISTENING_TIMER_STEP_MIN)
    }

    /**
     * A tap adds to what is LEFT, so the run gets exactly the five more minutes it was asked
     * for however long it has already been going — the reading a learner reaching for the chip
     * at midnight means.
     */
    @Test
    fun aTapAddsItsMinutesToWhatIsLeft() {
        val step = LISTENING_TIMER_STEP_MIN * 60_000L
        assertEquals(step, listeningTimerStepMs(0, 1))
        assertEquals(2 * step, listeningTimerStepMs(step, 1))
        // A minute left of a five-minute bedtime: six more, never ten.
        assertEquals(step + 60_000L, listeningTimerStepMs(60_000L, 1))
    }

    /** The picker walks back down the ladder it walked up, and past the end there is only OFF. */
    @Test
    fun aStepDownComesOffWhatIsLeftAndStopsAtOff() {
        val step = LISTENING_TIMER_STEP_MIN * 60_000L
        assertEquals(step, listeningTimerStepMs(2 * step, -1))
        assertEquals(0L, listeningTimerStepMs(step, -1))
        assertEquals(0L, listeningTimerStepMs(60_000L, -1))
        assertEquals(0L, listeningTimerStepMs(0, -1))
    }

    /** A clock that overshot its deadline still steps from OFF, never from a negative bedtime. */
    @Test
    fun anOvershotDeadlineStepsFromOff() {
        assertEquals(LISTENING_TIMER_STEP_MIN * 60_000L, listeningTimerStepMs(-hour, 1))
    }

    /** Outside a run there is no ramp, and the total is the clamped index and nothing else. */
    @Test
    fun noRampLeavesTheIndexAlone() {
        assertEquals(0.0, fadedGainDb(0.0, 0.0, 0.0))
        assertEquals(-11.8, fadedGainDb(-11.8, 0.0, 0.0), 1e-9)
        assertEquals(7.6, fadedGainDb(7.6, 0.0, 0.0), 1e-9)
        // Past what a measurement may claim, the index is still held to its own bound.
        assertEquals(-20.0, fadedGainDb(-45.0, 0.0, 0.0), 1e-9)
    }

    /** A word playing at the level it was measured to takes the whole ramp. */
    @Test
    fun anUncorrectedWordTakesTheWholeRamp() {
        assertEquals(LISTENING_FADE_FLOOR_DB, fadedGainDb(0.0, 0.0, LISTENING_FADE_FLOOR_DB), 1e-9)
        assertEquals(-9.5, fadedGainDb(0.0, 0.0, -9.5), 1e-9)
        // A boosted word takes it too — it ends the ramp that far above the floor.
        assertEquals(1.0, fadedGainDb(20.0, 0.0, LISTENING_FADE_FLOOR_DB), 1e-9)
    }

    /**
     * The floor is on the SUM: an sw word already 12 dB down and a de word playing as recorded
     * end the bedtime at the same level, rather than 12 dB apart with one of them inaudible.
     */
    @Test
    fun theRampStopsEveryWordAtTheSameFloor() {
        val sw = fadedGainDb(-11.8, 0.0, LISTENING_FADE_FLOOR_DB)
        val de = fadedGainDb(-0.6, 0.0, LISTENING_FADE_FLOOR_DB)
        assertEquals(LISTENING_FADE_FLOOR_DB, sw, 1e-9)
        assertEquals(LISTENING_FADE_FLOOR_DB, de, 1e-9)
    }

    /** An index already under the floor is left where it is — the ramp may deepen, never undo. */
    @Test
    fun anIndexUnderTheFloorTakesNoRamp() {
        assertEquals(-19.7, fadedGainDb(-19.7, 0.0, LISTENING_FADE_FLOOR_DB), 1e-9)
        assertEquals(-19.7, fadedGainDb(-19.7, 0.0, -5.0), 1e-9)
    }

    /**
     * The ramp opens the very headroom the converter's peak ceiling took away, so a capped
     * word gets it back a decibel at a time — and never a decibel more than the ramp opened.
     */
    @Test
    fun theRampHandsBackTheCapItOpenedRoomFor() {
        assertEquals(-3.0, fadedGainDb(0.0, 3.0, -6.0), 1e-9)
        assertEquals(0.0, fadedGainDb(0.0, 9.0, -6.0), 1e-9) // only the 6 dB it opened
        assertEquals(0.0, fadedGainDb(0.0, 9.0, 0.0), 1e-9) // and nothing at all at full
    }

    /** A word the floor held short of the ramp only ever spends the headroom it truly opened. */
    @Test
    fun aFlooredWordSpendsOnlyWhatItActuallyAttenuated() {
        // -15 dB index: the floor stops the ramp 4 dB in, so 4 dB of cap is all it may take.
        assertEquals(-15.0, fadedGainDb(-15.0, 9.0, LISTENING_FADE_FLOOR_DB), 1e-9)
        // And under the floor there is no ramp, so no headroom and no giveback.
        assertEquals(-19.7, fadedGainDb(-19.7, 9.0, LISTENING_FADE_FLOOR_DB), 1e-9)
    }

    /** Every step of a real ramp only ever moves a word down, and never past the floor. */
    @Test
    fun theRampNeverRisesAndNeverPassesTheFloor() {
        for (index in listOf(-19.7, -11.8, -0.6, 0.0, 7.6, 20.0)) {
            var previous = fadedGainDb(index, 0.0, 0.0)
            for (step in 1..40) {
                val total = fadedGainDb(index, 0.0, listeningGainDb(hour - step * (hour / 40), hour))
                assertTrue(total <= previous + 1e-9, "$index rose at step $step")
                assertTrue(total >= minOf(index, LISTENING_FADE_FLOOR_DB) - 1e-9,
                           "$index passed the floor at step $step")
                previous = total
            }
        }
    }
}
