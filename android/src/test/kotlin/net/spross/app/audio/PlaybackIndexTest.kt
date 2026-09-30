package net.spross.app.audio

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.catalog.Playback
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
        assertVolume(1.0, playingAt(7.6)) // uk «а»: lifted, so the volume stays out of it
        assertEquals(760, playbackBoostMillibels(playingAt(7.6)))

        assertVolume(0.2754, playingAt(-11.2)) // sw `hurt`, the loudest pack: turned down, no boost
        assertEquals(0, playbackBoostMillibels(playingAt(-11.2)))
    }

    /** 0/0 is "play at the output level" — a recording with nothing to correct, not an unknown. */
    @Test
    fun anUnmeasuredRecordingPlaysAtTheOutputLevel() {
        assertVolume(10.0.pow(Playback.OUTPUT_DB / 20), 0.0)
        assertEquals(0, playbackBoostMillibels(0.0))
    }

    /** Kern's bound survives the unit change: neither half may carry a wilder number. */
    @Test
    fun aWilderMeasurementThanTheConverterAllowsIsClamped() {
        assertEquals(playbackBoostMillibels(20.0), playbackBoostMillibels(45.0))
        assertEquals(playbackVolume(-20.0), playbackVolume(-45.0))
    }

    /**
     * The bedtime ramp reaches the same place whichever half the index rode: at kern's floor
     * a de word playing as recorded, the loudest sw word and a lifted uk letter all come out
     * at one level — the volume carries the difference the boost is already holding.
     */
    @Test
    fun theRampLandsEveryWordOnKernsFloor() {
        val floor = LISTENING_FADE_FLOOR_DB
        assertVolume(0.1122, playingAt(0.0), floor)
        assertVolume(0.1122, playingAt(-11.2), floor) // held at the floor, not driven 11 dB under it
        assertVolume(0.1122, playingAt(7.6), floor) // and the enhancer still holds its own 7.6 dB
        assertEquals(760, playbackBoostMillibels(playingAt(7.6)))
    }

    /** Halfway down the ramp is halfway down for everything, floor or no floor. */
    @Test
    fun aRampShortOfTheFloorIsTheWholeRamp() {
        assertVolume(0.5, playingAt(0.0), -6.0206)
        assertVolume(0.25, playingAt(-6.0206), -6.0206)
    }

    /**
     * The ramp hands a capped word back the headroom it just opened, and never more than it
     * opened — the ceiling the converter measured still holds at every point of the run.
     */
    @Test
    fun theRampGivesBackWhatTheCeilingHeldAndNoMore() {
        // 6 dB of ramp on a word held 3 dB back: the deficit is gone and 3 dB of ramp is left.
        assertVolume(0.7063, playingAt(0.0), -6.0206, capDb = 3.0)
        // 6 dB of ramp on a word held 9 dB back: only the 6 dB it opened comes back.
        assertVolume(1.0, playingAt(0.0), -6.0206, capDb = 9.0)
        // A word the boost lifts spends its cap on the volume; the enhancer is unmoved.
        assertVolume(0.3548, playingAt(7.6), -15.0, capDb = 6.0)
        assertEquals(760, playbackBoostMillibels(playingAt(7.6)))
    }

    /** At full volume there is no headroom to spend, so a cap changes nothing at all. */
    @Test
    fun aCapIsInertOutsideAFade() {
        assertVolume(1.0, playingAt(0.0), capDb = 12.0)
        assertVolume(0.2754, playingAt(-11.2), capDb = 12.0)
    }

    /** No measured noise, no expander: the word plays exactly as it did. */
    @Test
    fun anUngatedRecordingGetsNoBand() {
        assertNull(gateBand(null, 1f))
        assertNull(gateBand(null, 0.5f))
    }

    /**
     * The expander hears the noise after the volume and nothing else: a word turned down
     * 6 dB has its threshold moved 6 dB with it, a boosted one (volume 1) keeps the raw gate.
     */
    @Test
    fun theThresholdFollowsTheVolumeAlone() {
        assertEquals(-60f, gateBand(-60.0, 1f)!!.thresholdDb)
        assertEquals(-66.0206f, gateBand(-60.0, 0.5f)!!.thresholdDb, 1e-3f)
    }

    /** The band carries kern's shape as it stands, in the effect's own units. */
    @Test
    fun theBandIsKernsExpander() {
        val band = gateBand(-60.0, 1f)!!
        assertEquals(Playback.GATE_EXPANSION_RATIO.toFloat(), band.expanderRatio)
        assertEquals(Playback.GATE_ATTACK_MS.toFloat(), band.attackMs)
        assertEquals(Playback.GATE_RELEASE_MS.toFloat(), band.releaseMs)
    }

    /** The index that plays at [level] once [Playback.OUTPUT_DB] is under it. */
    private fun playingAt(level: Double) = level - Playback.OUTPUT_DB
}
