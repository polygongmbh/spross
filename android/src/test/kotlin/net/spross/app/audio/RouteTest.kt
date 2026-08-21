package net.spross.app.audio

import android.media.AudioDeviceInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The route → plane decision: the built-in speaker and earpiece get the catalog's
 * phone-speaker gain (`gainPhone`), everything else — and an unknown/empty route — the
 * full-range gain. `AudioDeviceInfo` only contributes compile-time constants here, so the
 * test stays a plain JVM test.
 */
class RouteTest {

    @Test
    fun theBuiltInOutputsAreThePhonePlane() {
        assertEquals(PlaybackPlane.PHONE, playbackPlane(setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)))
        assertEquals(PlaybackPlane.PHONE, playbackPlane(setOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)))
        assertEquals(PlaybackPlane.PHONE, playbackPlane(setOf(AudioDeviceInfo.TYPE_TELEPHONY)))
    }

    @Test
    fun anyPhoneOutputWinsOverFullRangeOnes() {
        // Headphones remain plugged in while the earpiece answers a call — still the phone.
        assertEquals(
            PlaybackPlane.PHONE,
            playbackPlane(setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_TELEPHONY)),
        )
        assertEquals(
            PlaybackPlane.PHONE,
            playbackPlane(
                setOf(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                ),
            ),
        )
    }

    @Test
    fun fullRangeOutputsAndNothingElse() {
        assertEquals(PlaybackPlane.FULL_RANGE, playbackPlane(setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)))
        assertEquals(PlaybackPlane.FULL_RANGE, playbackPlane(setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)))
        assertEquals(PlaybackPlane.FULL_RANGE, playbackPlane(setOf(AudioDeviceInfo.TYPE_USB_HEADSET)))
        assertEquals(
            PlaybackPlane.FULL_RANGE,
            playbackPlane(
                setOf(
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_HDMI,
                ),
            ),
        )
    }

    /** An unknown or empty route falls back to the full-range figure the packs have always shipped. */
    @Test
    fun anEmptyRouteIsTheFullRangePlane() {
        assertEquals(PlaybackPlane.FULL_RANGE, playbackPlane(emptySet()))
    }
}
