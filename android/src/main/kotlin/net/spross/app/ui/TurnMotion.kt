package net.spross.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * The one easing a turn's own motion shares: the segments bar filling, a choice tile's fill
 * and mark, a scramble chip settling into place, the verdict border and the card reveal. A
 * turn is one thing happening on screen, not four widgets each picking their own curve.
 *
 * M3's standard emphasized curve (`FastOutSlowInEasing`) rather than a spring — these are
 * value transitions (color, size, weight) a spring has no rest state to overshoot toward, and
 * a fixed-duration ease is what M3 itself prescribes for content settling into place.
 */
private val TurnEasing = FastOutSlowInEasing

/** The turn's one duration: the M3 "medium" band for a state a reveal rides on. */
private const val TURN_MOTION_MS = 220

/** The shared spec every turn animation in this package reaches for, over any value type. */
fun <T> turnTween(durationMillis: Int = TURN_MOTION_MS): FiniteAnimationSpec<T> =
    tween(durationMillis = durationMillis, easing = TurnEasing)
