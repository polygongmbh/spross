package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.LetterDrillFlow
import net.spross.app.letterSpeaker
import net.spross.kern.session.TurnFeedback
import net.spross.kern.trainer.LetterDrillTask

/**
 * The format bodies of the letter drill: the glyph tiles and the typed and dictated field.
 *
 * Every rule is kern's `LetterDrillRun`, reached through [LetterDrillFlow] — which tile is
 * the answer, what a typed word earns, which pause waits for a tap. This renders that and
 * hands taps back.
 */

/**
 * 2×2 of glyph tiles in kern's shuffled order — both platforms render the same draw. The
 * grid is [DrillChoiceGrid], shared with the calendar's warm-up Sprosse.
 */
@Composable
fun ChoiceFormat(flow: LetterDrillFlow, task: LetterDrillTask, chrome: Chrome) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        // The ramp's glyph slot rather than a ramp entry: a letterform is the thing being
        // READ here, so it is set at picture size the way an emoji face is — and a bare
        // Cyrillic glyph read by a German engine is a guess where "Buchstabe ч" is not.
        DrillChoiceGrid(
            options = task.choices.orEmpty(),
            answer = task.display,
            chosen = flow.state.chosen,
            optionStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            chrome = chrome,
            describe = { chrome.a11yGlyphLetter.format(it) },
            onPick = flow::choose,
        )
        // The tiles mark the answer themselves, so nothing is drawn under them: a miss waits
        // for the tap that books it, and a clean hit waits only where no beat can run.
        when (flow.state.feedback) {
            TurnFeedback.Neutral -> {}
            TurnFeedback.Correct -> if (flow.awaitsConfirm) ConfirmButton(chrome, flow::confirm)
            // why: tiles grade exact-only, so an almost cannot arise here — it books like any
            // other accepted answer if it ever does.
            is TurnFeedback.Almost, TurnFeedback.Revealed -> ConfirmButton(chrome, flow::confirm)
        }
    }
}

/**
 * Typed glyph and dictation: the same field, one primary action. A finished answer approves
 * itself as it is typed; an empty field reveals (and books the question as a miss), a filled
 * one checks — the sibling drills' contract.
 */
@Composable
fun TypedFormat(
    model: AppModel,
    flow: LetterDrillFlow,
    task: LetterDrillTask,
    chrome: Chrome,
    inputFocus: FocusRequester,
) {
    TypedAnswerControls(
        input = flow.input,
        onType = flow::type,
        placeholder = chrome.sessionAnswerPlaceholder.format(model.languageName(task.language)),
        feedback = flow.state.feedback,
        awaitsConfirm = flow.awaitsConfirm,
        chrome = chrome,
        focus = inputFocus,
        onPrimary = flow::primary,
        onEnter = flow::enter,
        onConfirm = flow::confirm,
        speakCorrection = { model.letterSpeaker(task, it) },
    )
}
