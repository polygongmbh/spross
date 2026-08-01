package net.spross.app.audio

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The catalog's ANALYSIS INDEX in the units an Android player takes: a linear volume, a
 * boost in millibels, and the position to start a file at.
 *
 * The scheme is `scripts/audio-catalog.py`'s `ANALYSIS['scheme']` = **boost**. The uk
 * letters sit 14.7 dB under the word packs, so attenuating everything down to them would
 * leave the whole app whispering; the quiet files are lifted instead. `setVolume` only
 * ever attenuates, so the index splits in two — a negative gain is the volume's business
 * and a positive one the `LoudnessEnhancer`'s, and exactly one of the pair is ever
 * anything but neutral.
 *
 * Beside [PronunciationPlayer] rather than inside it because this is the half of the
 * correction that is arithmetic and not a state machine: it is what a unit test without a
 * device can hold to its numbers.
 */

/** Where the converter clamps its own measurement: past 10× amplitude it is likelier broken. */
private const val GAIN_LIMIT_DB = 20.0

/** `LoudnessEnhancer` speaks millibels, the catalog decibels. */
private const val MILLIBELS_PER_DB = 100

/**
 * The volume a recording measured at [gainDb] plays at: the decibel definition where it
 * is to be turned down, and 1.0 where the enhancer will lift it instead.
 */
fun playbackVolume(gainDb: Double): Float =
    if (gainDb >= 0) 1f else 10.0.pow(clamped(gainDb) / 20).toFloat()

/**
 * What `LoudnessEnhancer.setTargetGain` is handed for a recording measured at [gainDb] —
 * 0 where the volume already carries the correction.
 */
fun playbackBoostMillibels(gainDb: Double): Int =
    if (gainDb <= 0) 0 else (clamped(gainDb) * MILLIBELS_PER_DB).roundToInt()

/**
 * Where playback starts in a file of [durationMs] whose measured dead air is [leadMs].
 *
 * A lead that would swallow the whole recording is a broken measurement, and the file is
 * still worth playing whole — as is one whose duration the platform will not say (-1).
 */
fun playbackHeadMs(leadMs: Long, durationMs: Long): Long =
    if (leadMs > 0 && leadMs < durationMs) leadMs else 0

private fun clamped(gainDb: Double) = gainDb.coerceIn(-GAIN_LIMIT_DB, GAIN_LIMIT_DB)
