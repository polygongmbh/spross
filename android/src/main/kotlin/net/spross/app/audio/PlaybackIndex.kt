package net.spross.app.audio

import kotlin.math.pow
import kotlin.math.roundToInt
import net.spross.kern.catalog.Playback
import net.spross.kern.listen.fadedGainDb

/**
 * The catalog's ANALYSIS INDEX in the units an Android player takes: a linear volume and a
 * boost in millibels.
 *
 * The scheme is `scripts/audio-catalog.py`'s `ANALYSIS['scheme']` = **boost**. The uk
 * letters sit 14.7 dB under the word packs, so attenuating everything down to them would
 * leave the whole app whispering; the quiet files are lifted instead. `setVolume` only
 * ever attenuates, so the index splits in two — a negative gain is the volume's business
 * and a positive one the `LoudnessEnhancer`'s, and exactly one of the pair is ever
 * anything but neutral.
 *
 * What a player may BELIEVE of the index — the bound a wild measurement is held to, and
 * where a recording starts — is kern's [Playback], shared with the iOS equalizer. What is
 * left here is the unit change onto MediaPlayer and LoudnessEnhancer.
 */

/** `LoudnessEnhancer` speaks millibels, the catalog decibels. */
private const val MILLIBELS_PER_DB = 100

/**
 * The volume a recording measured at [gainDb] plays at under [fadeDb] of kern's bedtime ramp:
 * everything of kern's [fadedGainDb] total that the enhancer below is not already carrying.
 *
 * The split is what makes the subtraction necessary. Kern's total is one number, and the two
 * halves have to add back up to it — so the boost takes the index where it lifts, and the
 * volume takes the rest, which is the ramp plus whatever attenuating the index asked for.
 */
fun playbackVolume(gainDb: Double, fadeDb: Double = 0.0): Float {
    val boosted = maxOf(0.0, Playback.gainDb(gainDb))
    return 10.0.pow((fadedGainDb(gainDb, fadeDb) - boosted) / 20).toFloat()
}

/** The same ramp with no recording under it — what a synthesized utterance is attenuated by. */
fun fadeVolume(fadeDb: Double): Float = 10.0.pow(fadeDb / 20).toFloat()

/**
 * What `LoudnessEnhancer.setTargetGain` is handed for a recording measured at [gainDb] —
 * 0 where the volume already carries the correction.
 */
fun playbackBoostMillibels(gainDb: Double): Int =
    if (gainDb <= 0) 0 else (Playback.gainDb(gainDb) * MILLIBELS_PER_DB).roundToInt()
