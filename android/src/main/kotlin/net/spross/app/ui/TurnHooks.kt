package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import net.spross.app.AppModel
import net.spross.kern.session.ToneKind

/**
 * The two acts every asking surface hands its flow, so that kern's effects reach the device.
 *
 * The review loop and all five drills pass this same pair, and it means the same thing on
 * every one of them — which is why neither is ever written out at a call site.
 */
class TurnHooks(
    /** A verdict cue: the chime, and the haptic a miss earns under it ([cueTone]). */
    val tone: (ToneKind) -> Unit,
    /**
     * why: a pause that waits for a tap must not hold the keyboard — it covers the very
     * button the pause is waiting for.
     */
    val releaseFocus: () -> Unit,
)

@Composable
fun rememberTurnHooks(model: AppModel): TurnHooks {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    return remember(view, focusManager, model) {
        TurnHooks(
            tone = { view.cueTone(it, model.cues) },
            releaseFocus = { focusManager.clearFocus() },
        )
    }
}
