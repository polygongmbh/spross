package net.spross.app.ui

import androidx.compose.animation.core.SpringSpec
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.pow
import net.spross.kern.design.PressKind

/** A spring given as iOS gives it — seconds to settle and a damping fraction — in Compose's terms. */
fun <T> responseSpring(response: Double, damping: Double): SpringSpec<T> =
    // A spring's response converts to Compose's stiffness as (2π / response)².
    spring(dampingRatio = damping.toFloat(), stiffness = (2 * PI / response).pow(2).toFloat())

/**
 * The press a control answers with: a spring-damped shrink under the thumb.
 *
 * Ripple says a tap LANDED; it does not say the control gave way, and that difference is
 * most of what "flat" means next to the iOS cut, where every button style presses.
 * The two run together — this adds the give, M3 keeps the ripple.
 *
 * How far it gives and the spring it runs are kern's [PressKind].
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
fun Modifier.pressSpring(kind: PressKind = PressKind.Action): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(
        targetValue = if (pressed) kind.scale.toFloat() else 1f,
        animationSpec = responseSpring(PressKind.RESPONSE, PressKind.DAMPING),
        label = "pressSpring",
    )
    return this
        // why: the animated value is read inside the layer block — in the DRAW phase,
        // and by this modifier. Read out here it would land in the CALLING composable's
        // restart scope, and every press would recompose that whole composable once per
        // frame for the length of the spring: on Home, the greeting and the date line
        // rebuilt twenty times because a button was held.
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
}
