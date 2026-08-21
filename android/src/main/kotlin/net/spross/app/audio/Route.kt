package net.spross.app.audio

import android.media.AudioDeviceInfo

/**
 * Which of the catalog's two playback planes the current output route deserves — the twin of
 * iOS's `AudioSession.plane()`. The built-in speaker and earpiece radiate almost none of a
 * word's low end, so a recording measured for full-range output plays too loud there and too
 * quiet through anything that can actually radiate it. The manifest carries one gain per
 * plane (`gain` full-range, `gainPhone` the built-in speaker) and the caller picks; this only
 * reads the device types.
 *
 * The decision is a pure function of the device-type set so a unit test can hold it without
 * an `AudioManager`.
 */
enum class PlaybackPlane { PHONE, FULL_RANGE }

/** The plane the output [devices] deserve; empty or unknown falls back to the full range. */
fun playbackPlane(devices: Set<Int>): PlaybackPlane =
    if (devices.any { it in PHONE_DEVICE_TYPES }) PlaybackPlane.PHONE else PlaybackPlane.FULL_RANGE

private val PHONE_DEVICE_TYPES = setOf(
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
    AudioDeviceInfo.TYPE_TELEPHONY,
)
