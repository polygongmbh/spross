package net.spross.app.ui

import androidx.compose.runtime.Composable
import net.spross.app.AppModel
import net.spross.app.SessionUi
import net.spross.app.TurnFlow
import net.spross.app.isSounding
import net.spross.app.pronounceAction
import net.spross.kern.model.ProducePrompt

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
        flow = flow,
        chrome = chrome,
        // The card asked by ear owes the MEANING, so the field names the source language —
        // kern's `answerLang`; the write-out only ever copies the target.
        placeholder = if (writing) {
            chrome.sessionCopyPlaceholder.format(model.targetName(ui))
        } else {
            chrome.sessionAnswerPlaceholder.format(model.answerName(flow))
        },
        // why: no speaker where the card was asked by ear — the correction is then a
        // SOURCE word, and the target voice would say a German word in Swahili.
        correctionVoice = CorrectionVoice(
            pronounce = { if (heard) null else model.pronounceAction(it) },
            isPlaying = { !heard && model.isSounding(it) },
        ),
        nextSubtitle = model.targetChrome?.commonNext,
        caption = if (writing) chrome.sessionCoachWrite.takeIf { model.coachActive } else model.gradeCaption,
    )
}
