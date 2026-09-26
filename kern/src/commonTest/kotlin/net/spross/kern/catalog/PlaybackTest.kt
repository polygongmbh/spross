package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a player may believe of the ANALYSIS INDEX. The device arithmetic each platform
 * wraps around it — linear volume, millibels, sample frames — is that platform's; these
 * are the bounds all of them share.
 */
class PlaybackTest {

    /** 0 is "play as it is" — a recording with nothing to correct, not an unknown. */
    @Test
    fun anUnmeasuredRecordingPlaysUntouched() {
        assertEquals(0.0, Playback.gainDb(0.0))
        assertEquals(0L, Playback.headMs(0, 60_000))
    }

    @Test
    fun aMeasurementInsideTheLimitIsObeyedToTheTenth() {
        assertEquals(7.6, Playback.gainDb(7.6)) // uk «а»
        assertEquals(-11.2, Playback.gainDb(-11.2)) // sw `hurt`, the loudest pack
        assertEquals(20.0, Playback.gainDb(20.0)) // uk «ж», the loudest lift we ship
    }

    /**
     * The converter clamps at ±20 dB and the player clamps again: a manifest that somehow
     * carried a wilder number is a broken measurement, never a recording to obey.
     */
    @Test
    fun aWilderMeasurementThanTheConverterAllowsIsClamped() {
        assertEquals(20.0, Playback.gainDb(45.0))
        assertEquals(-20.0, Playback.gainDb(-45.0))
    }

    @Test
    fun deadAirIsSkippedWhereTheRecordingOutlastsIt() {
        assertEquals(1285L, Playback.headMs(1285, 2000)) // uk «а», the latest starter
        assertEquals(250L, Playback.headMs(250, 60_000))
    }

    /**
     * A lead that would swallow the whole file is a broken measurement, and the recording
     * is still worth playing whole — as is one whose duration the platform will not say.
     */
    @Test
    fun aLeadThatWouldSwallowTheFileIsNotObeyed() {
        assertEquals(0L, Playback.headMs(2000, 2000))
        assertEquals(0L, Playback.headMs(2001, 2000))
        assertEquals(0L, Playback.headMs(1285, -1)) // an unreported duration
    }

    /** A gate after the gain stage follows the noise to wherever the gain moved it. */
    @Test
    fun aGateAfterTheGainMovesWithTheGain() {
        assertEquals(-52.0, Playback.gateThresholdDb(-60.0, 8.0)) // boosted: the hiss rose
        assertEquals(-65.0, Playback.gateThresholdDb(-60.0, -5.0)) // cut: the hiss fell
        assertEquals(-60.0, Playback.gateThresholdDb(-60.0, 0.0))
        assertNull(Playback.gateThresholdDb(null, 8.0)) // unmeasured: no gate at any gain
    }
}
