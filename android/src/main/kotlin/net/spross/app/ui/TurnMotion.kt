package net.spross.app.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * The one spec a turn's own motion shares: the segments bar filling, a choice tile's fill
 * and mark, a scramble chip settling into place, the verdict border and the card reveal. A
 * turn is one thing happening on screen, not four widgets each picking their own curve.
 *
 * Compose's default tween: Material's standard curve over its medium duration (300 ms),
 * which settles a verdict before kern's live beat (`ADVANCE_LIVE_MS`) moves the card on.
 */
fun <T> turnTween(): FiniteAnimationSpec<T> = tween()
