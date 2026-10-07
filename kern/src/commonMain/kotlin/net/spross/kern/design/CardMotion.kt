package net.spross.kern.design

import kotlin.math.abs

/**
 * How a card on screen moves, timed once for both apps: the switch to the next question,
 * and the reveal growing within one. Each app keeps its native easing curve.
 *
 * The switch is a turn about the vertical axis: the outgoing card turns away to one side
 * while the incoming one turns in from the other, each showing only its front half.
 */
object CardMotion {
    /** The switch between questions. */
    const val FLIP_MS: Int = 300

    /** How far a card turns in or out: edge-on, where it vanishes. */
    const val FLIP_DEGREES: Double = 90.0

    /** A reveal growing onto the answer, a correction box coming in, a field taking its verdict. */
    const val REVEAL_MS: Int = 250

    /**
     * The angle a card stands at, [progress] (0 to 1) through the switch:
     * the incoming one turns in from [FLIP_DEGREES] to flat, the outgoing one from flat to −[FLIP_DEGREES].
     */
    fun flipAngle(progress: Double, incoming: Boolean): Double {
        val p = progress.coerceIn(0.0, 1.0)
        return if (incoming) FLIP_DEGREES * (1 - p) else -FLIP_DEGREES * p
    }

    /** Whether a card at [angle] shows: edge-on and past it, only its mirrored back would, which never shows. */
    fun flipShows(angle: Double): Boolean = abs(angle) < FLIP_DEGREES
}
