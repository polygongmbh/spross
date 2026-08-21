package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.SessionUi
import net.spross.app.TurnFlow
import net.spross.app.audio.Pronouncer
import net.spross.app.pronounceAction
import net.spross.app.pronounceTarget
import net.spross.kern.model.ProducePrompt
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.TurnFeedback

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
    val heard = ui.producePrompt == ProducePrompt.Sound
    val revealed = flow.answerRevealed

    VocabCard(
        emoji = card.emoji,
        cue = ui.emojiCue,
        revealed = revealed,
        closingLines = if (revealed) targetLines(card.target, chrome) else emptyList(),
        // The note is the card's last line whichever side authored it — a literal
        // gloss belongs to the concept, not to one of its two faces.
        note = if (revealed) card.target.note ?: card.source.note else null,
    ) {
        if (heard) ReplayPrompt(model, ui) else PromptWord(model, ui)
        // The card is what OPENS onto the answer — inline, growing downward, above the
        // field the learner is still typing in. A near miss never reaches here: its
        // correction stands at the field, beside the attempt it is correcting.
        if (revealed) CardReveal { ProduceReveal(model, ui) }
    }

    val step = flow.copyStep
    if (step != null) {
        WriteOutStep(model, flow, step, model.targetName(ui))
        return
    }
    // The blank "Aufdecken" hands the turn to the three verdicts; there is no field left.
    if (!flow.selfGrading) {
        AnswerField(
            value = flow.input,
            onValueChange = flow::type,
            placeholder = chrome.answerPlaceholder.format(model.targetName(ui)),
            feedback = flow.fieldFeedback,
            chrome = chrome,
            onDone = { flow.enter() },
        )
    }
    when (val feedback = flow.feedback) {
        TurnFeedback.Neutral -> if (flow.selfGrading) {
            VerdictButtons(chrome, flow, caption = model.gradeCaption)
        } else {
            // ONE primary action: an empty field reveals, a typed one checks.
            Button(
                onClick = { flow.primary() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (flow.input.isBlank()) chrome.reveal else chrome.check)
            }
        }
        // why: nothing is drawn for a clean answer — it already stands in the learner's
        // own text with the field's own checkmark, and the card is on its way out. Under
        // a screen reader no beat ever armed, so the tap that replaces it is all there is.
        TurnFeedback.Correct -> if (flow.awaitsConfirm) ConfirmButton(chrome) { flow.confirm() }
        is TurnFeedback.Almost -> AlmostHold(model, flow, feedback)
        TurnFeedback.Revealed -> MissedAnswer(model, ui, flow)
    }
}

/**
 * The source word, under the area it is asked within where the prompt would otherwise be
 * ambiguous, and with the feminine marker where the card is a demoted feminine.
 *
 * The area label is the disambiguating cue, in the source language and never graded. It
 * rides the PRODUCE prompt only: on a recognition prompt a cue precise enough to tell the
 * two concepts apart would hand over the answer (kern `Card.promptAmbiguous`).
 */
@Composable
private fun PromptWord(model: AppModel, ui: SessionUi) {
    val card = ui.card ?: return
    if (card.promptAmbiguous) CardCue(model.areaTitle(card.area))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.s),
    ) {
        Headword(card.source.text, modifier = Modifier.weight(1f, fill = false))
        if (card.promptFeminineMarker) FeminineBadge()
    }
}

/**
 * The whole question, where the question is a sound: the meaning is withheld ON PURPOSE,
 * so no cue rides along with it. Autoplay already said the word; this is the way to hear
 * it again.
 */
@Composable
private fun ReplayPrompt(model: AppModel, ui: SessionUi) {
    val card = ui.card ?: return
    val chrome = model.chrome
    Box(
        modifier = Modifier
            .size(72.dp)
            .clickable { model.pronounceTarget(card.target.text, Pronouncer.Trigger.TAP) }
            // why: merged, or the loudspeaker would be a node of its own and TalkBack
            // would read the picture after the button it belongs to.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = chrome.pronounce
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SprossIcons.Speaker,
            contentDescription = null,
            tint = Dl.colors.accent,
            modifier = Modifier.size(40.dp),
        )
    }
}

/**
 * An accepted answer that was not clean pauses on what it owes back — a slip's proper
 * spelling, or the form that actually played where the card accepts the one written.
 * The box IS the correction, so it carries the word and the speaker that says it; the card
 * itself stays closed, and nothing is on screen twice.
 */
@Composable
private fun AlmostHold(model: AppModel, flow: TurnFlow, hold: TurnFeedback.Almost) {
    val chrome = model.chrome
    val caption = when (hold.reason) {
        AlmostReason.Typo -> chrome.almostTypo
        AlmostReason.Heard -> chrome.almostHeard
    }
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.s)) {
        AlmostCorrection(caption, hold.correctForm, chrome, model.pronounceAction(hold.correctForm))
        ConfirmButton(chrome) { flow.confirm() }
    }
}

/**
 * A miss: the answer stands on the card and the field stays OPEN, primed with the whole
 * words that were already right. Finishing the retype is the self-grade — it books
 * recalled-with-help — and giving up is an honest Again. Both are kern's; the way out is
 * always on screen, because a step you cannot leave is a trap.
 */
@Composable
private fun MissedAnswer(model: AppModel, ui: SessionUi, flow: TurnFlow) {
    val card = ui.card ?: return
    val chrome = model.chrome
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.s)) {
        flow.otherWord?.let { other ->
            // why: the line says what the learner DID write; the word it speaks is the
            // one they owed, the same one the card above has opened onto.
            PauseLine(
                chrome.otherWordNote.format(other.word, other.meanings.joinToString(", ")),
                modifier = Modifier.pronounceOnTap(model.pronounceAction(card.target.text), chrome),
            )
        }
        // why: the beat that books a finished retype never arms under a screen reader,
        // so without this a finished retype would have no way on but giving up — which
        // grades Again, not what it just earned.
        if (flow.retryApproved && flow.awaitsConfirm) ConfirmButton(chrome) { flow.confirm() }
        TextButton(
            onClick = { flow.giveUp() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(chrome.skipStep, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * What a produce card shows once it has stopped asking: the meaning it withheld, and the
 * word itself. Its grammar and family close the card (`targetLines`, above). The picture
 * is the card's own slot, which was standing empty and only now fades in — nothing here
 * moves it.
 */
@Composable
private fun ProduceReveal(model: AppModel, ui: SessionUi) {
    val card = ui.card ?: return
    val chrome = model.chrome
    // why: a sound-prompted card never said what the word MEANS, so the reveal owes
    // it back — otherwise a miss teaches nothing but spelling.
    if (ui.producePrompt == ProducePrompt.Sound) {
        Text(
            (listOf(card.source.text) + card.source.synonyms).joinToString(" / "),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
    TargetReveal(
        card.target, chrome,
        pronounce = model.pronounceAction(card.target.text),
    )
}
