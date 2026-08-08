package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.LetterCorrection
import net.spross.app.LetterDrillFlow
import net.spross.app.LetterFeedback
import net.spross.kern.trainer.LetterDrillTask

/**
 * The stage bodies of the letter drill: the glyph tiles, the typed and dictated field,
 * and the line that says what an answer earned. State lives in `LetterDrillFlow`; this
 * renders it and hands taps back.
 */

/** 2×2 of glyph tiles in kern's shuffled order — both platforms render the same draw. */
@Composable
fun ChoiceStage(flow: LetterDrillFlow, chrome: Chrome, screenReader: Boolean) {
    val task = flow.task ?: return
    val choices = task.choices.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in choices.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (glyph in row) {
                    ChoiceTile(glyph, task.display, flow.chosen, chrome, Modifier.weight(1f)) {
                        flow.choose(glyph)
                    }
                }
                // why: an odd last row keeps the grid's column width instead of
                // stretching one tile across the screen.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        AnswerLine(flow, chrome, screenReader)
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
    // why: correctness is never colour alone — the mark carries it on screen, the state
    // description carries it to TalkBack, and a bare Cyrillic glyph read by a German
    // engine is a guess where "Buchstabe ч" is not.
    val mark = when {
        answered && isAnswer -> "✓"
        answered && isChosen -> "✗"
        else -> null
    }
    val palette = Dl.colors
    val fill = when {
        answered && isAnswer -> palette.wash(palette.success)
        answered && isChosen -> palette.wash(palette.wrong)
        // A tile is a recessed slot, not a card: it takes the chip fill, so an
        // unanswered one still reads as a tile against the paper behind it.
        else -> palette.surfaceTint
    }
    OutlinedButton(
        onClick = onClick,
        enabled = !answered,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.heightIn(min = 72.dp).semantics {
            contentDescription = chrome.letterChoice.format(glyph)
            if (answered && isAnswer) stateDescription = chrome.answerCorrect
            if (answered && isChosen && !isAnswer) stateDescription = chrome.answerWrong
        },
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = fill,
            disabledContainerColor = fill,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(glyph, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        mark?.let { Text("  $it", fontSize = 20.sp) }
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
    screenReader: Boolean,
    inputFocus: FocusRequester,
) {
    val language = model.catalog?.languages?.get(task.language)?.name ?: task.language
    val idle = flow.feedback == LetterFeedback.Idle
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = flow.input,
            onValueChange = { flow.input = it },
            modifier = Modifier.fillMaxWidth().focusRequester(inputFocus),
            // why: readOnly, not disabled — the keyboard stays up, so Enter still moves
            // past the answer.
            readOnly = !idle,
            placeholder = { Text(chrome.answerPlaceholder.format(language)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (idle) flow.submit() else flow.next() }),
            singleLine = true,
        )
        if (idle) {
            Button(
                onClick = { if (flow.input.isBlank()) flow.reveal() else flow.submit() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (flow.input.isBlank()) chrome.reveal else chrome.check)
            }
        }
        // why: the meaning is a REVEAL, never a cue — a dictation that shows what the
        // word means is no longer taken from the sound.
        if (flow.feedback is LetterFeedback.Revealed) {
            task.gloss?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnswerLine(flow, chrome, screenReader)
    }
}

/**
 * What the answer earned, and the tap that books it. A miss always waits; a clean hit
 * waits only where a timed screen change would talk over the announcement it just made.
 */
@Composable
private fun AnswerLine(flow: LetterDrillFlow, chrome: Chrome, screenReader: Boolean) {
    val feedback = flow.feedback
    if (feedback == LetterFeedback.Idle) return
    val note = when (feedback) {
        is LetterFeedback.Revealed -> chrome.correctAnswer.format(feedback.answer)
        is LetterFeedback.Correct -> feedback.correction?.let {
            when (it.kind) {
                LetterCorrection.Kind.Typo -> chrome.typoCorrection.format(it.form)
                LetterCorrection.Kind.Heard -> chrome.heardInstead.format(it.form)
            }
        }
        LetterFeedback.Idle -> null
    }
    val palette = Dl.colors
    val tone = when (feedback) {
        is LetterFeedback.Revealed -> palette.wrong
        else -> if ((feedback as LetterFeedback.Correct).correction == null) {
            palette.success
        } else {
            palette.amber
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        note?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = tone,
                // why: TalkBack has no autoplay to tell it what happened — the verdict
                // announces itself where the learner's focus already is.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val waits = feedback is LetterFeedback.Revealed ||
            (feedback as? LetterFeedback.Correct)?.correction != null ||
            screenReader
        if (waits) {
            Button(
                onClick = { flow.next() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(chrome.next)
            }
        }
    }
}
