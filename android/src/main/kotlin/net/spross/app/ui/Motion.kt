package net.spross.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput

/**
 * How far a pressed control shrinks. The canonical value — iOS's button styles all press to
 * the same 97 %, which is the point: the app has ONE press, not one per control.
 */
private const val PRESS_SCALE = 0.97f

/**
 * The press a control answers with: a spring-damped shrink under the thumb.
 *
 * Ripple says a tap LANDED; it does not say the control gave way, and that difference is
 * most of what "flat" means next to the iOS cut, where every button style presses.
 * The two run together — this adds the give, M3 keeps the ripple.
 *
 * The spring is iOS's, converted rather than re-picked: `response: 0.25` is a natural
 * frequency of 2π/0.25 ≈ 25 rad/s, so the stiffness is its square (~630) and
 * [Spring.StiffnessMediumLow] lands within a few percent of it. `dampingFraction` carries
 * straight over.
 *
 * The press is read from the pointer directly rather than from an interaction source, so a
 * control keeps whatever source it already owns and this stays one modifier at the call site.
 * The down is taken even once consumed — the button's own click handler sees it first and
 * claims it — and a gesture that turns into a scroll cancels, which releases the scale just
 * as a lift does.
 *
 * Applied LAST in a chain: the scale is drawn, never measured, so it must not resize
 * anything that was laid out against the control's real bounds.
 */
@Composable
fun Modifier.pressSpring(): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "pressSpring",
    )
    return this
        .scale(scale)
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
}
