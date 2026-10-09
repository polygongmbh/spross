package net.spross.kern.design

/**
 * How a speaker glyph pulses while its word is sounding, timed once for both apps:
 * a swell to [SCALE] and back, over and over until the word ends. Reduced motion drops it.
 */
object SpeakerPulse {
    /** The glyph's scale at the top of a swell. */
    const val SCALE: Double = 1.16

    /** One swell, or one ebb. */
    const val HALF_MS: Int = 350
}
