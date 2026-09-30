package net.spross.app.audio

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import net.spross.kern.catalog.Playback
import net.spross.kern.listen.fadedGainDb

/**
 * The catalog's ANALYSIS INDEX in the units an Android player takes: a linear volume, a
 * boost in millibels and a noise gate's expander band.
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
 * The volume a recording measured at [gainDb], capped by [capDb], plays at under [fadeDb] of
 * kern's bedtime ramp: everything of kern's [fadedGainDb] total that the enhancer below is
 * not already carrying.
 *
 * The split is what makes the subtraction necessary. Kern's total is one number, and the two
 * halves have to add back up to it — so the boost takes the index where it lifts, and the
 * volume takes the rest: the ramp, whatever attenuating the index asked for, and whatever of
 * the cap the ramp handed back. The rest can only ever be an attenuation, which is all
 * `setVolume` can give.
 */
fun playbackVolume(gainDb: Double, capDb: Double = 0.0, fadeDb: Double = 0.0): Float {
    val boosted = maxOf(0.0, Playback.levelDb(gainDb))
    return 10.0.pow((fadedGainDb(gainDb, capDb, fadeDb) - boosted) / 20).toFloat()
}

/** The same level with no recording under it — what a synthesized utterance plays at. */
fun fadeVolume(fadeDb: Double): Float = Playback.linear(fadedGainDb(0.0, 0.0, fadeDb)).toFloat()

/**
 * What `LoudnessEnhancer.setTargetGain` is handed for a recording measured at [gainDb] —
 * 0 where the volume already carries the correction.
 */
fun playbackBoostMillibels(gainDb: Double): Int =
    (maxOf(0.0, Playback.levelDb(gainDb)) * MILLIBELS_PER_DB).roundToInt()

/**
 * The one band of the downward expander a recording's gate asks for, in the units
 * `DynamicsProcessing.MbcBand` takes: dBFS, a ratio, ms.
 */
data class GateBand(
    val thresholdDb: Float,
    val expanderRatio: Float,
    val attackMs: Float,
    val releaseMs: Float,
)

/** The band for a recording measured at [gate] and played at [volume]; null where [gate] is — no gate. */
fun gateBand(gate: Double?, volume: Float): GateBand? =
    // why: `setVolume` scales the track as AudioFlinger mixes it, before any session effect,
    // so the expander always hears the noise moved by the volume — kern's after-the-gain
    // threshold. Where the LoudnessEnhancer sits against it the platform never promises, so
    // the boost stays out: run first, it only lifts the noise over the threshold and the word
    // plays ungated, where counting it would set the threshold up into the word and cut its tail.
    Playback.gateThresholdDb(gate, 20 * log10(volume.toDouble()))?.let {
        GateBand(
            thresholdDb = it.toFloat(),
            expanderRatio = Playback.GATE_EXPANSION_RATIO.toFloat(),
            attackMs = Playback.GATE_ATTACK_MS.toFloat(),
            releaseMs = Playback.GATE_RELEASE_MS.toFloat(),
        )
    }
