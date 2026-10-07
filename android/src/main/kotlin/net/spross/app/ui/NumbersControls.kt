package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.NumbersFlow
import net.spross.app.speakFormOnTap

/**
 * Kern's controls on the shared answer area, with the look-up link under them — outside the
 * verdict, because a miss is exactly when a learner wants to look the word up, and the "?"
 * raises the very table the numbers page shows.
 */
@Composable
fun NumbersControls(
    model: AppModel,
    flow: NumbersFlow,
    chrome: Chrome,
    inputFocus: FocusRequester,
    onFinish: () -> Unit,
) {
    val state = flow.state
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        DrillAnswerArea(
            model, state.controls, flow.input, flow.awaitsConfirm, inputFocus,
            onType = flow::type, onEnter = flow::enter, onConfirm = flow::confirm, onStop = onFinish,
            speakCorrection = { model.speakFormOnTap(it, state.mode.language) },
        )
        if (state.offersLookUp) {
            TextButton(onClick = { flow.lookUp() }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "? ${chrome.numbersLookup}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Theme.colors.textSecondary,
                )
            }
        }
    }
}
