package net.spross.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import net.spross.app.AppModel
import net.spross.app.LetterDrillFlow
import net.spross.app.letterSpeaker
import net.spross.kern.trainer.LetterDrillTask

/**
 * What stands under the letter card: kern's controls on the shared answer area — four glyph
 * tiles, or the field typed and dictated formats share. Every keystroke is offered to kern,
 * so a finished answer approves itself.
 *
 * Every rule is kern's `LetterDrillRun`, reached through [LetterDrillFlow] — which tile is
 * the answer, what a typed word earns, which pause waits for a tap.
 */
@Composable
fun LetterAnswer(
    model: AppModel,
    flow: LetterDrillFlow,
    task: LetterDrillTask,
    inputFocus: FocusRequester,
    onFinish: () -> Unit,
) {
    val chrome = model.chrome
    val controls = flow.state.controls ?: return
    DrillAnswerArea(
        model, controls, flow.input, flow.awaitsConfirm, inputFocus,
        onType = flow::type, onEnter = flow::enter, onConfirm = flow::confirm, onStop = onFinish,
        speakCorrection = { model.letterSpeaker(task, it) },
    ) { options, answer ->
        // 2×2 in kern's shuffled order — both platforms render the same draw. A prompt size
        // rather than a ramp entry: a letterform is the thing being READ here, so it is set at
        // picture size the way an emoji face is — and a bare Cyrillic glyph read by a German
        // engine is a guess where "Buchstabe ч" is not.
        DrillChoiceGrid(
            options = options,
            answer = answer,
            chosen = flow.state.chosen,
            optionStyle = MaterialTheme.typography.displaySmall.copy(fontSize = Theme.prompt.letter, fontWeight = FontWeight.Bold),
            chrome = chrome,
            describe = { chrome.a11yGlyphLetter.format(it) },
            onPick = flow::choose,
        )
    }
}
