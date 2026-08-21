package net.spross.kern.catalog

/**
 * How far a player may trust a recording's ANALYSIS INDEX.
 *
 * The index (`gain`, `lead`) is a MEASUREMENT of the shipped bytes and never an edit to
 * them (`kern/docs/audio.md`), so both numbers can only ever be as good as the measurement:
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
}
