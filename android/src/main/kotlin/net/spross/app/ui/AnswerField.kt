package net.spross.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import net.spross.app.Chrome
import net.spross.app.audio.Pronouncer
import net.spross.kern.session.TurnFeedback

/**
 * The one field every asking surface types into — the review loop's and all four drills'.
 *
 * The verdict tint, the checkmark and the state a screen reader hears are the whole of what
 * it renders, and they are the same wherever the answer is written: a near miss runs amber
 * throughout — field edge, checkmark and box agree (`docs/design.md`).
 *
 * Never read-only, not even after grading: a miss keeps typing, because the retype IS the
 * answer. Kern ignores text in the states that decide nothing, and a field that locks is a
 * field the keyboard closes under.
 */
@Composable
fun AnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    feedback: TurnFeedback,
    chrome: Chrome,
    onDone: () -> Unit,
    /**
     * Who holds the focus. The review loop leaves this null and the field claims the
     * keyboard as it MOUNTS, which is what makes the write-out step usable the moment
     * "Unbekannt" opens it. A drill hands its own requester instead: the letter run gives
     * the focus to the replay button under a screen reader, and every run gives the keyboard
     * back on an amber hold and wants it again with the next question ([QuestionFocus]).
     */
    focus: FocusRequester? = null,
    /** A task that owes digits the number pad can type — the keyboard is the one thing this decides. */
    numberPad: Boolean = false,
) {
    val palette = Theme.colors
    // The edge says how the answer landed: green where it was clean, amber for a near miss
    // and for a reveal alike — on a reveal it is the whole of the mark, since nothing about
    // an unanswered question was accepted and no checkmark may claim otherwise.
    val tint: Color? = when (feedback) {
        TurnFeedback.Correct -> palette.success
        is TurnFeedback.Almost, TurnFeedback.Revealed -> palette.amber
        else -> null
    }
    val owned = remember { FocusRequester() }
    val requester = focus ?: owned
    LaunchedEffect(Unit) { if (focus == null) requester.claimWhenPlaced() }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
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
        // description says it to TalkBack, and the tint is the third telling of the same
        // thing. The mark says ACCEPTED and rides both accepted states, its color saying
        // how cleanly; a reveal gets none, because nothing about it was accepted.
        trailingIcon = when (feedback) {
            TurnFeedback.Correct -> {
                { Icon(SprossIcons.Check, contentDescription = null, tint = palette.success) }
            }
            is TurnFeedback.Almost -> {
                { Icon(SprossIcons.Check, contentDescription = null, tint = palette.amber) }
            }
            else -> null
        },
        colors = if (tint == null) {
            OutlinedTextFieldDefaults.colors()
        } else {
            OutlinedTextFieldDefaults.colors(focusedBorderColor = tint, unfocusedBorderColor = tint)
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numberPad) KeyboardType.Number else KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
    )
}

/**
 * Who holds the keyboard as a question arrives, on every run that asks one.
 *
 * The field takes it back with every question — an amber hold gives it up so the button it
 * waits for is not covered, and the next prompt is typed into rather than tapped. Both
 * targets are the CALLER's to name, because what deserves the focus is what this question
 * is asked with: [field] is null where the question is tapped or heard rather than written,
 * and [screenReader] is what TalkBack is handed instead — the letter drill's replay button,
 * since its whole question is the sound; null everywhere else, which leaves the focus where
 * the screen reader already put it rather than dragging it off the card.
 */
@Composable
fun QuestionFocus(
    question: Any?,
    pronouncer: Pronouncer,
    field: FocusRequester?,
    screenReader: FocusRequester? = null,
) {
    LaunchedEffect(question) {
        val target = if (pronouncer.readsScreenAloud) screenReader else field
        target?.claimWhenPlaced()
    }
}

/**
 * why: a requester answers only once its node has been placed; one frame is what that
 * takes, and a request fired inside the same composition lands on nothing.
 */
suspend fun FocusRequester.claimWhenPlaced() {
    withFrameNanos { }
    runCatching { requestFocus() }
}
