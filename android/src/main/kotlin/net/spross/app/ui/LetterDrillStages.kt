package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.LetterDrillFlow
import net.spross.app.letterSpeaker
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.TurnFeedback
import net.spross.kern.trainer.LetterDrillTask

/**
 * The stage bodies of the letter drill: the glyph tiles, the typed and dictated field, and
 * the line that says what an answer earned.
 *
 * Every rule is kern's `LetterDrillRun`, reached through [LetterDrillFlow] — which tile is
 * the answer, what a typed word earns, which pause waits for a tap. This renders that and
 * hands taps back.
 */

/** 2×2 of glyph tiles in kern's shuffled order — both platforms render the same draw. */
@Composable
fun ChoiceStage(model: AppModel, flow: LetterDrillFlow, task: LetterDrillTask, chrome: Chrome) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        for (row in task.choices.orEmpty().chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                for (glyph in row) {
                    ChoiceTile(glyph, task.display, flow.state.chosen, chrome, Modifier.weight(1f)) {
                        flow.choose(glyph)
                    }
                }
                // why: an odd last row keeps the grid's column width instead of stretching
                // one tile across the screen.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        AnswerLine(model, flow, task, chrome)
    }
}

@Composable
private fun ChoiceTile(
    glyph: String,
    answer: String,
    chosen: String?,
    chrome: Chrome,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val answered = chosen != null
    val isAnswer = glyph == answer
    val isChosen = glyph == chosen
    // why: correctness is never color alone — the mark carries it on screen, the state
    // description carries it to TalkBack, and a bare Cyrillic glyph read by a German engine
    // is a guess where "Buchstabe ч" is not.
    val mark = when {
        answered && isAnswer -> "✓"
        answered && isChosen -> "✗"
        else -> null
    }
    val palette = Theme.colors
    val fill = when {
        answered && isAnswer -> palette.wash(palette.success)
        answered && isChosen -> palette.wash(palette.wrong)
        // A tile is a recessed slot, not a card: it takes the chip fill, so an unanswered
        // one still reads as a tile against the paper behind it.
        else -> palette.surfaceTint
    }
    OutlinedButton(
        onClick = onClick,
        enabled = !answered,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.heightIn(min = Theme.reserve.tile).semantics {
            contentDescription = chrome.a11yGlyphLetter.format(glyph)
            if (answered && isAnswer) stateDescription = chrome.a11yVerdictCorrect
            if (answered && isChosen && !isAnswer) stateDescription = chrome.a11yVerdictWrong
        }.pressSpring(),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = fill,
            disabledContainerColor = fill,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        // The ramp's glyph slot rather than a ramp entry: a letterform is the thing being
        // READ here, so it is set at picture size the way an emoji face is.
        Text(glyph, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        mark?.let { Text("  $it", style = MaterialTheme.typography.titleLarge) }
    }
}

/**
 * Typed glyph and dictation: the same field, one primary action. An empty field reveals
 * (and books the question as a miss), a filled one checks — the sibling drill's contract.
 */
@Composable
fun TypedStage(
    model: AppModel,
    flow: LetterDrillFlow,
    task: LetterDrillTask,
    chrome: Chrome,
    inputFocus: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        DrillAnswerField(
            value = flow.input,
            onValueChange = flow::type,
            placeholder = chrome.sessionAnswerPlaceholder.format(model.languageName(task.language)),
            feedback = flow.state.feedback,
            chrome = chrome,
            focus = inputFocus,
            onDone = { flow.enter() },
        )
        if (flow.state.owesAnswer) {
            Button(
                onClick = { flow.primary() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (flow.input.isBlank()) chrome.sessionReveal else chrome.commonCheck)
            }
        }
        AnswerLine(model, flow, task, chrome)
    }
}

/**
 * What the answer earned, and the tap that books it.
 *
 * The answer itself is on the CARD — both amber holds and a miss open it there, so nothing
 * repeats it here; what this adds is which of the two ambers it was. A miss and both holds
 * always wait, because kern arms no beat on any of them; a clean hit waits only where a
 * timed screen change would talk over the announcement it just made.
 */
@Composable
private fun AnswerLine(
    model: AppModel,
    flow: LetterDrillFlow,
    task: LetterDrillTask,
    chrome: Chrome,
) {
    val feedback = flow.state.feedback
    if (feedback == TurnFeedback.Neutral) return
    val hold = feedback as? TurnFeedback.Almost
    val waits = feedback != TurnFeedback.Correct || flow.awaitsConfirm
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        hold?.let {
            val caption = when (it.reason) {
                AlmostReason.Typo -> chrome.sessionAlmostTypo
                AlmostReason.Heard -> chrome.sessionAlmostHeard
                AlmostReason.Merged -> chrome.sessionAlmostMerged
            }
            AlmostCorrection(
                caption, it.correctForm, chrome,
                model.letterSpeaker(task, it.correctForm),
            )
        }
        if (waits) ConfirmButton(chrome) { flow.confirm() }
    }
}
