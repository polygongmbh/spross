package net.spross.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import net.spross.app.Chrome
import net.spross.kern.session.TurnFeedback

/**
 * The field both drills type into.
 *
 * The review loop's `AnswerField` claims focus as it mounts, which is exactly wrong in a
 * drill: the letter run hands its focus to the replay button under a screen reader, and
 * both runs give the keyboard back on an amber hold and want it again with the next
 * question. So the focus is the CALLER's here, and everything else — the verdict tint, the
 * checkmark, the state a screen reader hears — is the same.
 *
 * Never read-only, not even after grading: kern ignores text in the states that decide
 * nothing, and a field that locks is a field the keyboard closes under.
 */
@Composable
fun DrillAnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    feedback: TurnFeedback,
    chrome: Chrome,
    focus: FocusRequester,
    onDone: () -> Unit,
    /** A reversed task owes digits — the keyboard is the one thing the direction decides. */
    digits: Boolean = false,
) {
    val palette = Theme.colors
    val tint: Color? = when (feedback) {
        TurnFeedback.Correct -> palette.success
        is TurnFeedback.Almost -> palette.amber
        else -> null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .semantics {
                when (feedback) {
                    TurnFeedback.Correct -> stateDescription = chrome.a11yVerdictCorrect
                    // why: the amber edge is the whole of what tells a near miss from a
                    // clean answer, and a color says nothing to TalkBack (WCAG 1.4.1).
                    is TurnFeedback.Almost -> stateDescription = chrome.a11yVerdictAlmost
                    // why: a reveal leaves the field its amber edge and nothing else — the
                    // one state that has to SAY it was never answered.
                    TurnFeedback.Revealed -> stateDescription = chrome.a11yVerdictNotAnswered
                    else -> {}
                }
            },
        placeholder = { Text(placeholder) },
        // why: correctness is never color alone — the mark says it on screen, the state
        // description says it to TalkBack, and the tint is the third telling.
        trailingIcon = if (feedback == TurnFeedback.Correct) {
            { Icon(SprossIcons.Check, contentDescription = null, tint = palette.success) }
        } else {
            null
        },
        colors = if (tint == null) {
            OutlinedTextFieldDefaults.colors()
        } else {
            OutlinedTextFieldDefaults.colors(focusedBorderColor = tint, unfocusedBorderColor = tint)
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (digits) KeyboardType.Number else KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
    )
}
