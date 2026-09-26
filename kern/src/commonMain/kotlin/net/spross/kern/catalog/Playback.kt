package net.spross.kern.catalog

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

    /**
     * The most the gate ever takes off, in dB: enough to push the hiss under the word,
     * never enough to chop a tail that dips below the threshold.
     */
    const val GATE_MAX_ATTENUATION_DB: Double = 15.0

    /** How fast the gate opens when the word starts, in ms: quick enough to keep its onset. */
    const val GATE_ATTACK_MS: Double = 5.0

    /** How slowly the gate closes after the word, in ms: slow enough that a decay is not clipped. */
    const val GATE_RELEASE_MS: Double = 150.0
}
