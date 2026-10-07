package net.spross.app.ui

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
import net.spross.app.typableOnNumberPad
import net.spross.app.speakFormOnTap

/**
 * The field and the one primary action under it. A reversed task owes DIGITS, so its
 * placeholder names those rather than the language — "auf Spanisch" over a number pad asks
 * for the wrong thing.
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
    val placeholder = if (state.currentReversed) {
        chrome.numbersAnswerPlaceholder
    } else {
        chrome.sessionAnswerPlaceholder.format(model.languageName(state.mode.language))
    }
    TypedAnswerControls(
        input = flow.input,
        onType = flow::type,
        placeholder = placeholder,
        feedback = state.feedback,
        awaitsConfirm = flow.awaitsConfirm,
        chrome = chrome,
        focus = inputFocus,
        onPrimary = flow::primary,
        onEnter = flow::enter,
        onConfirm = flow::confirm,
        speakCorrection = { model.speakFormOnTap(it, state.mode.language) },
        numberPad = state.currentReversed && typableOnNumberPad(state.currentTask.accepted),
    ) {
        if (state.offersFinish) DrillStopOffer(chrome, onFinish)
        // Outside the verdict: a miss is exactly when a learner wants to look the word up,
        // and the "?" raises the very table the numbers page shows.
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
