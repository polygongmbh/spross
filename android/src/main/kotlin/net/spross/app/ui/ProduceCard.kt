package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.SessionUi
import net.spross.app.TurnFlow
import net.spross.app.areaTitle
import net.spross.app.pronounceAction
import net.spross.kern.model.ProducePrompt
import net.spross.kern.session.TurnFeedback
import net.spross.kern.session.question

/**
 * PRODUCE half of the session screen: typing-first controls over kern's turn.
 *
 * Every keystroke and button here is a `TurnIntent` — what each one is worth, which beat
 * it earns and what a miss opens are `TurnMachine`'s. Writing the word out exactly IS the
 * answer, so a word you know never asks for a confirming tap; a miss keeps the field open,
 * because the retype is the answer too.
 */
@Composable
fun ProduceCard(model: AppModel, ui: SessionUi, flow: TurnFlow) {
    val card = ui.card ?: return
    val chrome = model.chrome
    // The TURN's own fact, never a re-reading of the device: audibility can change under a
    // card — a volume key, headphones out — and a card face that flipped mid-turn would
    // show the source word while kern still grades the meaning.
    val heard = flow.state.prompt == ProducePrompt.Sound
    ReportableCard(model, card, flow.answerOut, typed = { flow.answerForReport }) {
        QuestionCard(
            flow.state.question,
            chrome,
            surface = QuestionSurface.Review,
            voice = model.cardVoice,
            areaTitle = model::areaTitle,
        )
    }

    val step = flow.copyStep
    if (step != null) {
        WriteOutStep(model, flow, step, model.targetName(ui))
        return
    }
    // The blank "Aufdecken" hands the turn to the three verdicts, and a miss by ear is never
    // retyped (kern's `retypes`); either way there is no field left.
    val missedByEar = flow.feedback == TurnFeedback.Revealed && !flow.state.retypes
    if (!flow.selfGrading && !missedByEar) {
        AnswerField(
            value = flow.input,
            onValueChange = flow::type,
            // The card asked by ear owes the MEANING, so the field names the source
            // language — kern's `answerLang`, never this screen's reading of the prompt.
            placeholder = chrome.sessionAnswerPlaceholder.format(model.answerName(flow)),
            feedback = flow.fieldFeedback,
            chrome = chrome,
            onDone = { flow.enter() },
        )
    }
    when (val feedback = flow.feedback) {
        TurnFeedback.Neutral -> if (flow.selfGrading) {
            VerdictButtons(chrome, flow, caption = model.gradeCaption)
        } else {
            PrimaryAction(flow.input, chrome, flow::primary)
        }
        else -> AnswerVerdict(
            feedback,
            flow.awaitsConfirm,
            chrome,
            flow::confirm,
            // why: no speaker where the card was asked by ear — the correction is then a
            // SOURCE word, and the target voice would say a German word in Swahili.
            speakCorrection = { if (heard) null else model.pronounceAction(it) },
            missed = { MissedAnswer(model, flow) },
        )
    }
    // why: this card's whole content is a sound, and a learner who cannot listen to it
    // would otherwise answer blind. Under the primary action, because it is the way out
    // and not the way through — and only while the card is still asking.
    if (heard && !flow.promptInText && flow.feedback == TurnFeedback.Neutral && !flow.selfGrading) {
        TextButton(onClick = { flow.showPromptText() }, modifier = Modifier.fillMaxWidth()) {
            Text(chrome.sessionHearCantListen, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * A miss: the answer stands on the card and the field stays OPEN, primed with the whole
 * words that were already right. Finishing the retype is the self-grade — it books
 * recalled-with-help — and giving up is an honest Again. Both are kern's; the way out is
 * always on screen, because a step you cannot leave is a trap.
 */
@Composable
private fun MissedAnswer(model: AppModel, flow: TurnFlow) {
    val chrome = model.chrome
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        // why: the beat that books a finished retype never arms under a screen reader,
        // so without this a finished retype would have no way on but giving up — which
        // grades Again, not what it just earned.
        if (flow.retryApproved && flow.awaitsConfirm) ConfirmButton(chrome) { flow.confirm() }
        if (!flow.state.retypes) {
            // A miss by ear has no field to retype into: the reveal is the whole of it.
            ConfirmButton(chrome) { flow.giveUp() }
        } else {
            TextButton(
                onClick = { flow.giveUp() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(chrome.sessionSkip, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
