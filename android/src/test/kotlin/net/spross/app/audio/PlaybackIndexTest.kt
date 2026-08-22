package net.spross.app.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.listen.LISTENING_FADE_FLOOR_DB

/**
 * The UNIT CHANGE from the catalog's analysis index onto this platform's players: a linear
 * volume for MediaPlayer, millibels for the LoudnessEnhancer. What the index itself may be
 * believed to mean — the ±20 dB bound, where a recording starts — is kern's `Playback`
 * and is tested there.
 *
 * The scheme under test is `ANALYSIS['scheme']` = boost: a positive gain is the
 * enhancer's and a negative one the volume's, never both, because `setVolume` cannot
 * lift a recording and the uk letters need up to +20 dB of lifting.
 */
class PlaybackIndexTest {

    private fun assertVolume(expected: Double, gainDb: Double, fadeDb: Double = 0.0) =
        assertTrue(
            abs(playbackVolume(gainDb, fadeDb) - expected) < 1e-4,
            "$gainDb dB under $fadeDb played at ${playbackVolume(gainDb, fadeDb)}," +
                " expected $expected",
        )

    @Test
    fun theSchemeSplitsTheIndexInTwoAndNeverAppliesBothHalves() {
        assertVolume(1.0, 7.6) // uk «а»: lifted, so the volume stays out of it
        assertEquals(760, playbackBoostMillibels(7.6))

        assertVolume(0.2754, -11.2) // sw `hurt`, the loudest pack: turned down, no boost
        assertEquals(0, playbackBoostMillibels(-11.2))
    }

    /** 0/0 is "play as it is" — a recording with nothing to correct, not an unknown. */
    @Test
    fun anUnmeasuredRecordingPlaysUntouched() {
        assertVolume(1.0, 0.0)
        assertEquals(0, playbackBoostMillibels(0.0))
    }

    @Test
    fun attenuationIsTheDecibelDefinition() {
        assertVolume(0.5, -6.0206)
        assertVolume(0.25, -12.0412)
        assertVolume(0.1, -20.0)
    }

    /** Kern's bound survives the unit change: neither half may carry a wilder number. */
    @Test
    fun aWilderMeasurementThanTheConverterAllowsIsClamped() {
        assertEquals(2000, playbackBoostMillibels(20.0)) // uk «ж», the loudest lift we ship
        assertEquals(2000, playbackBoostMillibels(45.0))
        assertVolume(0.1, -45.0)
    }

    /** Millibels are the platform's unit, tenths of a dB the catalog's resolution. */
    @Test
    fun aTenthOfADecibelSurvivesTheUnitChange() {
        assertEquals(1280, playbackBoostMillibels(12.8)) // uk «й»
        assertEquals(270, playbackBoostMillibels(2.7)) // uk `address`
    }

    /**
     * The bedtime ramp reaches the same place whichever half the index rode: at kern's floor
     * a de word playing as recorded, the loudest sw word and a lifted uk letter all come out
     * at one level — the volume carries the difference the boost is already holding.
     */
    @Test
    fun theRampLandsEveryWordOnKernsFloor() {
        val floor = LISTENING_FADE_FLOOR_DB
        assertVolume(0.1122, 0.0, floor)
        assertVolume(0.1122, -11.2, floor) // held at the floor, not driven 11 dB under it
        assertVolume(0.1122, 7.6, floor) // and the enhancer still holds its own 7.6 dB
        assertEquals(760, playbackBoostMillibels(7.6))
    }

    /** Halfway down the ramp is halfway down for everything, floor or no floor. */
    @Test
    fun aRampShortOfTheFloorIsTheWholeRamp() {
        assertVolume(0.5, 0.0, -6.0206)
        assertVolume(0.25, -6.0206, -6.0206)
    }
}
