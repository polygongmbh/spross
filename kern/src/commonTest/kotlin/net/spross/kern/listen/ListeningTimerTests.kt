package net.spross.kern.listen

import net.spross.kern.catalog.Playback
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
    }

    /**
     * The ramp is the WHOLE bedtime, not a window at the end of it: a fade that starts is a
     * second event, and a listener on the edge of sleep hears a change beginning long before
     * they hear a level continuing. And every length ends in the same place: the ramp is a
     * fraction of the run, never a rate.
     */
    @Test
    fun everyBedtimeRampsAcrossItsWholeLengthToTheSameLevel() {
        for (minutes in listOf(LISTENING_TIMER_STEP_MIN, 120)) {
            val total = minutes * 60_000L
            assertEquals(0.0, listeningGainDb(total, total))
            assertEquals(LISTENING_FADE_FLOOR_DB / 2, listeningGainDb(total / 2, total), 1e-9)
            assertEquals(LISTENING_FADE_FLOOR_DB, listeningGainDb(0, total))
        }
    }

    /**
     * Held to the level kern chose, whatever a caller hands in — a clock that overshoots its
     * deadline must quieten the run, never invert the ramp.
     */
    @Test
    fun theRampStaysInsideItsOwnFloor() {
        for (ms in listOf(-hour, hour / 3, Long.MAX_VALUE)) {
            val gain = listeningGainDb(ms, totalMs = hour)
            assertTrue(gain in LISTENING_FADE_FLOOR_DB..0.0, "\$ms gave \$gain")
        }
        assertEquals(LISTENING_FADE_FLOOR_DB, listeningGainDb(-hour, totalMs = hour))
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
        assertEquals(0L, listeningTimerStepMs(60_000L, -1))
        assertEquals(0L, listeningTimerStepMs(0, -1))
    }

    /** A clock that overshot its deadline still steps from OFF, never from a negative bedtime. */
    @Test
    fun anOvershotDeadlineStepsFromOff() {
        assertEquals(LISTENING_TIMER_STEP_MIN * 60_000L, listeningTimerStepMs(-hour, 1))
    }

    /** Outside a run there is no ramp, and the total is the recording's level and nothing else. */
    @Test
    fun noRampLeavesTheLevelAlone() {
        for (index in listOf(-11.8, 7.6)) {
            assertEquals(Playback.levelDb(index), fadedGainDb(index, 0.0, 0.0), 1e-9)
        }
    }

    /** A word playing at the level it was measured to takes the whole ramp. */
    @Test
    fun anUncorrectedWordTakesTheWholeRamp() {
        assertEquals(-9.5, fadedGainDb(playingAt(0.0), 0.0, -9.5), 1e-9)
        // A boosted word takes it too — it ends the ramp that far above the floor.
        assertEquals(-11.4, fadedGainDb(playingAt(7.6), 0.0, LISTENING_FADE_FLOOR_DB), 1e-9)
    }

    /**
     * The floor is on the SUM: an sw word already 12 dB down and a de word playing as recorded
     * end the bedtime at the same level, rather than 12 dB apart with one of them inaudible.
     */
    @Test
    fun theRampStopsEveryWordAtTheSameFloor() {
        val sw = fadedGainDb(playingAt(-11.8), 0.0, LISTENING_FADE_FLOOR_DB)
        val de = fadedGainDb(playingAt(-0.6), 0.0, LISTENING_FADE_FLOOR_DB)
        assertEquals(LISTENING_FADE_FLOOR_DB, sw, 1e-9)
        assertEquals(LISTENING_FADE_FLOOR_DB, de, 1e-9)
    }

    /** An index already under the floor is left where it is — the ramp may deepen, never undo. */
    @Test
    fun anIndexUnderTheFloorTakesNoRamp() {
        assertEquals(-19.7, fadedGainDb(playingAt(-19.7), 0.0, LISTENING_FADE_FLOOR_DB), 1e-9)
        assertEquals(-19.7, fadedGainDb(playingAt(-19.7), 0.0, -5.0), 1e-9)
    }

    /**
     * The ramp opens the very headroom the converter's peak ceiling took away, so a capped
     * word gets it back a decibel at a time — and never a decibel more than the ramp opened.
     */
    @Test
    fun theRampHandsBackTheCapItOpenedRoomFor() {
        assertEquals(-3.0, fadedGainDb(playingAt(0.0), 3.0, -6.0), 1e-9)
        assertEquals(0.0, fadedGainDb(playingAt(0.0), 9.0, -6.0), 1e-9) // only the 6 dB it opened
        assertEquals(0.0, fadedGainDb(playingAt(0.0), 9.0, 0.0), 1e-9) // and nothing at all at full
    }

    /** A word the floor held short of the ramp only ever spends the headroom it truly opened. */
    @Test
    fun aFlooredWordSpendsOnlyWhatItActuallyAttenuated() {
        // A word at -15 dB: the floor stops the ramp 4 dB in, so 4 dB of cap is all it may take.
        assertEquals(-15.0, fadedGainDb(playingAt(-15.0), 9.0, LISTENING_FADE_FLOOR_DB), 1e-9)
    }

    /** Every step of a real ramp only ever moves a word down, and never past the floor. */
    @Test
    fun theRampNeverRisesAndNeverPassesTheFloor() {
        for (index in listOf(-19.7, -11.8, -0.6, 0.0, 7.6, 14.0)) {
            var previous = fadedGainDb(playingAt(index), 0.0, 0.0)
            for (step in 1..40) {
                val total = fadedGainDb(playingAt(index), 0.0, listeningGainDb(hour - step * (hour / 40), hour))
                assertTrue(total <= previous + 1e-9, "$index rose at step $step")
                assertTrue(total >= minOf(index, LISTENING_FADE_FLOOR_DB) - 1e-9,
                           "$index passed the floor at step $step")
                previous = total
            }
        }
    }

    /**
     * The chip reads whole minutes rounded UP, so it only reaches zero once the bedtime has
     * arrived, and it is woken by the minute turning rather than by the second.
     */
    @Test
    fun theChipCountsWholeMinutesAndWakesWhenOneTurns() {
        assertEquals(2, listeningTimerMinutes(90_000L))
        assertEquals(0, listeningTimerMinutes(-5_000L))
        assertEquals(20_000L, listeningTimerWakeMs(15 * 60_000L + 20_000L))
        assertTrue(listeningTimerWakeMs(-1_000L) > 0, "a bedtime already reached must not spin")
    }

    /** The index that plays at [level] once [Playback.OUTPUT_DB] is under it. */
    private fun playingAt(level: Double) = level - Playback.OUTPUT_DB
}
