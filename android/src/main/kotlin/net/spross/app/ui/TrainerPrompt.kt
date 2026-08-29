package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.TrainerFlow
import net.spross.app.speakFormOnTap
import net.spross.kern.session.TurnFeedback

/**
 * What a slot run puts on screen: the prompt card and the controls under it.
 *
 * A drill card is a review card — the same face ([CardFace]) and the same reveal
 * ([CardReveal]) — and it carries NOTHING but the prompt: the run's header line already
 * names what is drilled and how far the ramp has come, and the field's placeholder names
 * what is owed, so a badge here would be the third telling of what one tap said.
 */

/**
 * One big prompt on the app's own card face, and the same reveal a vocabulary card grows.
 *
 * A prompt made of WORDS is laid out like one — smaller and wrapped — where a numeral gets
 * the one big line. Asked of the PROMPT rather than of the run, so a composed sentence and
 * a reversed reading are both read as what they are.
 */
@Composable
fun TrainerPromptCard(model: AppModel, flow: TrainerFlow, chrome: Chrome) {
    val state = flow.state
    val task = state.currentTask
    val wordy = task.promptDisplay.any { it.isLetter() }
    CardFace(Modifier.heightIn(min = DlReserve.drillCard)) {
        Text(
            task.promptDisplay,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = if (wordy) DlPrompt.sentence else DlPrompt.digits,
                fontWeight = FontWeight.Bold,
                fontFamily = if (wordy) FontFamily.Default else FontFamily.Monospace,
            ),
            textAlign = TextAlign.Center,
            maxLines = if (wordy) 4 else 1,
        )
        if (state.showsAnswer) {
            CardReveal(note = task.gloss) {
                SpokenWord(model.speakFormOnTap(task.display, state.mode.language), chrome) {
                    Text(
                        localizedTarget(task.display, state.mode.language),
                        style = MaterialTheme.typography.titleLarge,
                        color = Dl.colors.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            state.otherWord?.let { other ->
                // why: same line as the review session's — both explain what became
                // of the answer, so they read alike.
                PauseLine(chrome.otherWordNote.format(other.word, other.meanings.joinToString(", ")))
            }
            return@CardFace
        }
        // why: the reveal TAKES this slot rather than stacking under it — the hint is
        // scaffolding for a prompt still unanswered, and a fact about THIS number.
        state.placeValueHint?.let {
            Text(
                chrome.newPlace.format(it),
                style = MaterialTheme.typography.bodySmall,
                color = Dl.colors.accent,
                modifier = Modifier
                    .background(Dl.colors.surfaceTint, RoundedCornerShape(percent = 50))
                    .padding(horizontal = DlSpace.m, vertical = DlSpace.s),
            )
        }
    }
}

/**
 * The field and the one primary action under it. A reversed task owes DIGITS, so its
 * placeholder names those rather than the language — "auf Spanisch" over a number pad asks
 * for the wrong thing.
 */
@Composable
fun TrainerControls(
    model: AppModel,
    flow: TrainerFlow,
    chrome: Chrome,
    inputFocus: FocusRequester,
    onFinish: () -> Unit,
) {
    val state = flow.state
    val placeholder = if (state.currentReversed) {
        chrome.answerDigits
    } else {
        chrome.answerPlaceholder.format(model.languageName(state.mode.language))
    }
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.m)) {
        DrillAnswerField(
            value = flow.input,
            onValueChange = flow::type,
            placeholder = placeholder,
            feedback = state.feedback,
            chrome = chrome,
            focus = inputFocus,
            onDone = { flow.enter() },
            digits = state.currentReversed,
        )
        when (val feedback = state.feedback) {
            TurnFeedback.Neutral -> Button(
                onClick = { flow.primary() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (flow.input.isBlank()) chrome.reveal else chrome.check)
            }
            // why: nothing is drawn for a clean answer — it already stands in the learner's
            // own text with the field's checkmark, and the card is on its way out.
            TurnFeedback.Correct -> if (flow.awaitsConfirm) ConfirmButton(chrome) { flow.confirm() }
            is TurnFeedback.Almost -> AlmostLine(model, flow, feedback.correctForm, chrome)
            // why: no "Wusste ich" in a drill — the tasks are generated, so self-reporting
            // after seeing the answer proves nothing; revealed simply counts as a miss.
            TurnFeedback.Revealed -> ConfirmButton(chrome) { flow.confirm() }
        }
        if (state.offersFinish) DrillStopOffer(chrome, onFinish)
        // Outside the feedback switch: a miss is exactly when a learner wants to look the
        // word up, and the "?" raises the very table the numbers page shows.
        if (state.offersLookUp) {
            TextButton(onClick = { flow.lookUp() }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "? ${chrome.lookUp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dl.colors.textSecondary,
                )
            }
        }
    }
}

/** A slip: the box spells the word out, and the tap that ends the pause books it amber. */
@Composable
private fun AlmostLine(model: AppModel, flow: TrainerFlow, form: String, chrome: Chrome) {
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.s)) {
        AlmostCorrection(
            chrome.almostTypo,
            form,
            chrome,
            model.speakFormOnTap(form, flow.state.mode.language),
        )
        ConfirmButton(chrome) { flow.confirm() }
    }
}
