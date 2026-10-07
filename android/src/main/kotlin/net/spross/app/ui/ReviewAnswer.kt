package net.spross.app.ui

import androidx.compose.runtime.Composable
import net.spross.app.AppModel
import net.spross.app.SessionUi
import net.spross.app.TurnFlow
import net.spross.app.pronounceAction
import net.spross.kern.model.ProducePrompt
import net.spross.kern.session.controls

/**
 * What stands under the review card: kern's `TurnState.controls` on the shared [AnswerArea].
 *
 * Every keystroke and button here is a `TurnIntent` — what each one is worth, which beat it
 * earns and what a miss opens are `TurnMachine`'s. The answer field and the write-out keep
 * their own text; only one is ever mounted.
 */
@Composable
fun ReviewAnswer(model: AppModel, ui: SessionUi, flow: TurnFlow) {
    val chrome = model.chrome
    val writing = flow.copyStep != null
    // The TURN's own fact, never a re-reading of the device: audibility can change under a
    // card — a volume key, headphones out.
    val heard = flow.state.prompt == ProducePrompt.Sound
    AnswerArea(
        controls = flow.state.controls,
        text = if (writing) flow.copyInput else flow.input,
        chrome = chrome,
        // The card asked by ear owes the MEANING, so the field names the source language —
        // kern's `answerLang`; the write-out only ever copies the target.
        placeholder = if (writing) {
            chrome.sessionCopyPlaceholder.format(model.targetName(ui))
        } else {
            chrome.sessionAnswerPlaceholder.format(model.answerName(flow))
        },
        awaitsConfirm = flow.awaitsConfirm,
        // why: no speaker where the card was asked by ear — the correction is then a
        // SOURCE word, and the target voice would say a German word in Swahili.
        correctionVoice = CorrectionVoice { if (heard) null else model.pronounceAction(it) },
        caption = if (writing) chrome.sessionCoachWrite.takeIf { model.coachActive } else model.gradeCaption,
        actions = AnswerActions(
            submit = { if (writing) flow.submitCopy() else flow.enter() },
            type = { if (writing) flow.writeCopy(it) else flow.type(it) },
            reveal = flow::reveal,
            confirm = flow::confirm,
            // why: giving up on a retype ends the card — that field already is the one
            // write-out the word gets, so nothing hands it a second.
            giveUp = { if (writing) flow.skipCopy() else flow.giveUp() },
            selfGrade = flow::selfGrade,
            cantListen = flow::showPromptText,
        ),
    )
}
