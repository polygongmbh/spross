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

    private fun assertVolume(
        expected: Double,
        gainDb: Double,
        fadeDb: Double = 0.0,
        capDb: Double = 0.0,
    ) = playbackVolume(gainDb, capDb, fadeDb).let { played ->
        assertTrue(
            abs(played - expected) < 1e-4,
            "$gainDb dB capped by $capDb under $fadeDb played at $played, expected $expected",
        )
    }

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

    /**
     * The ramp hands a capped word back the headroom it just opened, and never more than it
     * opened — the ceiling the converter measured still holds at every point of the run.
     */
    @Test
    fun theRampGivesBackWhatTheCeilingHeldAndNoMore() {
        // 6 dB of ramp on a word held 3 dB back: the deficit is gone and 3 dB of ramp is left.
        assertVolume(0.7063, 0.0, -6.0206, capDb = 3.0)
        // 6 dB of ramp on a word held 9 dB back: only the 6 dB it opened comes back.
        assertVolume(1.0, 0.0, -6.0206, capDb = 9.0)
        // A word the boost lifts spends its cap on the volume; the enhancer is unmoved.
        assertVolume(0.3548, 7.6, -15.0, capDb = 6.0)
        assertEquals(760, playbackBoostMillibels(7.6))
    }

    /** At full volume there is no headroom to spend, so a cap changes nothing at all. */
    @Test
    fun aCapIsInertOutsideAFade() {
        assertVolume(1.0, 0.0, capDb = 12.0)
        assertVolume(0.2754, -11.2, capDb = 12.0)
    }
}
