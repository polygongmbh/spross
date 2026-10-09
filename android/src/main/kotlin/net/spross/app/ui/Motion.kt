package net.spross.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.pow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    val scale = remember { Animatable(1f) }
    val spec = remember { responseSpring<Float>(PressKind.RESPONSE, PressKind.DAMPING) }
    return this
        // why: driven from the pointer coroutine and read only in the DRAW phase, so a press
        // never recomposes the calling composable — on Home that rebuilt the greeting per
        // frame of the spring, and mid-gesture it rebuilt a component that tracks the press
        // itself (ExposedDropdownMenuBox), which then lost the click.
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(kind) {
            coroutineScope {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    launch { scale.animateTo(kind.scale.toFloat(), spec) }
                    waitForUpOrCancellation()
                    launch { scale.animateTo(1f, spec) }
                }
            }
        }
}
