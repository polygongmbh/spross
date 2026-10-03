package net.spross.kern.catalog

import kotlin.math.pow

/**
 * How far a player may trust a recording's ANALYSIS INDEX.
 *
 * The index (`gain`, `lead`, `gate`) is a MEASUREMENT of the shipped bytes and never an edit to
 * them (`kern/docs/audio.md`), so every number can only ever be as good as the measurement:
 * the bounds below are what a broken one is held to, stated once so the manifest parser,
 * an iOS equalizer and an Android loudness enhancer cannot drift apart about them.
 *
 * Everything in device units — linear volume, millibels, sample frames — stays app-side;
 * this is the arithmetic that is the same on every device.
 */
object Playback {

    /** Where the converter clamps its own measurement: past 10× amplitude the index is likelier wrong than the file. */
    const val GAIN_LIMIT_DB: Double = 20.0

    /**
     * [measured] held to ±[GAIN_LIMIT_DB].
     *
     * The manifest parser already rejects a wilder value, so this is defense in depth:
     * whatever reaches a player, a number past the limit is a broken measurement and
     * never a recording to obey.
     */
    fun gainDb(measured: Double): Double = measured.coerceIn(-GAIN_LIMIT_DB, GAIN_LIMIT_DB)

    /**
     * Where everything the app plays sits, in dB against the one loudness target the recordings
     * are indexed to and the chimes are leveled to ([Chime]): recordings, synthesized
     * speech and chimes all take it, so turning it moves them together and keeps them level.
     */
    const val OUTPUT_DB: Double = -8.0

    /** The level a player applies for a recording [measured] at: the clamped index at [OUTPUT_DB]. */
    fun levelDb(measured: Double): Double = gainDb(measured) + OUTPUT_DB

    /** [db] as the linear amplitude factor a platform's volume takes. */
    fun linear(db: Double): Double = 10.0.pow(db / 20)

    /**
     * Where playback starts in a recording of [durationMs] whose measured dead air is [leadMs]:
     * the lead itself, or the front of the file.
     *
     * A lead that would swallow the whole recording is a broken measurement,
     * and the recording is still worth playing whole — as is one whose duration
     * the platform will not report (a negative [durationMs]).
     */
    fun headMs(leadMs: Long, durationMs: Long): Long =
        if (leadMs > 0 && leadMs < durationMs) leadMs else 0

    /** The lowest noise level a measurement may claim: below it the file is digital silence. */
    const val GATE_FLOOR_DB: Double = -100.0

    /**
     * The threshold, in dBFS, for a noise gate that sits AFTER the gain stage:
     * the raw [gate] moved by the [appliedGainDb] the player actually applies,
     * so the threshold follows the noise wherever the gain put it.
     * A gate before the gain stage takes [gate] as it stands.
     * Null where [gate] is: no gate.
     */
    fun gateThresholdDb(gate: Double?, appliedGainDb: Double): Double? =
        gate?.let { it + appliedGainDb }

    /**
     * Below the threshold, each dB the signal falls is played as this many dB of fall:
     * a downward expander rather than a hard gate, so the word's own quiet tail fades
     * instead of cutting off.
     */
    const val GATE_EXPANSION_RATIO: Double = 2.0

    /** How fast the gate opens when the word starts, in ms: quick enough to keep its onset. */
    const val GATE_ATTACK_MS: Double = 5.0

    /** How slowly the gate closes after the word, in ms: slow enough that a decay is not clipped. */
    const val GATE_RELEASE_MS: Double = 150.0
}

/**
 * The feedback chimes, each with the gain that places its full-scale file (`scripts/sounds.py`)
 * at its loudness; a player applies [levelDb], so a chime is retuned here without re-rendering.
 *
 * The words land at -16.7 LUFS, and the chimes sit at or a little above that:
 * K-weighting counts energy, and a near-sine spends all of its in one critical band
 * where speech spreads across many, so a chime metered level with a word is heard under it.
 * A right answer comes all the time, so its chime sits well under the miss's — by ear, not meter:
 * a phone speaker rolls off the miss's low third, so the higher correct one sounds louder than its LUFS.
 */
enum class Chime(private val gainDb: Double) {
    /** -19.7 LUFS. */
    Correct(-7.5),

    /** -19.7 LUFS: a slip, level with the correct sound it begins. */
    Almost(-7.3),

    /** -13.7 LUFS. */
    Wrong(-1.2),

    /** -16.2 LUFS: it plays on every card, but at a tenth of a second any softer is missed. */
    Reveal(-2.4),

    /** -13.7 LUFS: once, at the finish. */
    Cheer(-1.3);

    /** The level a player applies, at [Playback.OUTPUT_DB]. */
    val levelDb: Double get() = gainDb + Playback.OUTPUT_DB
}
