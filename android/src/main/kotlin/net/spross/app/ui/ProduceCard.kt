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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.CardDisplay
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
    // The TURN's own fact, never a re-reading of the device: audibility can change under a
    // card — a volume key, headphones out — and a card face that flipped mid-turn would
    // show the source word while kern still grades the meaning.
    val heard = flow.state.prompt == ProducePrompt.Sound
    val revealed = flow.answerRevealed
    // The word takes the replay glyph's place once there is nothing left to withhold:
    // the learner said they cannot listen, or the answer is out and the spelling is
    // what the reveal owes.
    val written = heard && (flow.promptInText || revealed)

    ReportableCard(model, card, revealed, typed = { flow.input }) {
        VocabCard(
            emoji = card.emoji,
            cue = ui.emojiCue,
            revealed = revealed,
            closingLines = when {
                !revealed -> emptyList()
                // The word stands in the prompt slot with its plural under it (`WrittenPrompt`),
                // exactly as a recognition prompt does, so only the family closes the card.
                heard -> listOfNotNull(CardDisplay.alsoLine(card.target, chrome, listOf(card.target.text)))
                else -> targetLines(card.target, chrome)
            },
            // The note is the card's last line whichever side authored it — a literal
            // gloss belongs to the concept, not to one of its two faces.
            note = if (revealed) card.target.note ?: card.source.note else null,
        ) {
            when {
                written -> WrittenPrompt(model, ui)
                heard -> ReplayPrompt(model, ui)
                else -> PromptWord(model, ui)
            }
            // The card is what OPENS onto the answer — inline, growing downward, above the
            // field the learner is still typing in. A near miss never reaches here: its
            // correction stands at the field, beside the attempt it is correcting.
            if (revealed) CardReveal { ProduceReveal(model, ui, heard) }
        }
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
            // ONE primary action: an empty field reveals, a typed one checks.
            Button(
                onClick = { flow.primary() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (flow.input.isBlank()) chrome.sessionReveal else chrome.commonCheck)
            }
        }
        // why: nothing is drawn for a clean answer — it already stands in the learner's
        // own text with the field's own checkmark, and the card is on its way out. Under
        // a screen reader no beat ever armed, so the tap that replaces it is all there is.
        TurnFeedback.Correct -> if (flow.awaitsConfirm) ConfirmButton(chrome) { flow.confirm() }
        is TurnFeedback.Almost -> AlmostHold(model, flow, feedback, heard)
        TurnFeedback.Revealed -> MissedAnswer(model, ui, flow)
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
        if (card.promptFeminineMarker) FeminineBadge(model.chrome)
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
                contentDescription = chrome.a11yActionPronounce
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
 * The word that played, standing as text: a target word on a prompt, rendered as one, with
 * the speaker that says it — so the card still teaches the sound it was asking from.
 * While the question stands the meaning stays withheld; only the channel it arrives
 * through moved.
 */
@Composable
private fun WrittenPrompt(model: AppModel, ui: SessionUi) {
    val card = ui.card ?: return
    val chrome = model.chrome
    SpokenWord(model.pronounceAction(card.target.text), chrome) {
        Headword(
            localizedTarget(Dl.colors.articleColoredText(card.target), card.target.lang),
            modifier = Modifier.weight(1f, fill = false),
        )
    }
    CardDisplay.pluralLine(card.target, chrome)?.let { CardLine(it) }
}

/**
 * An accepted answer that was not clean pauses on what it owes back — a slip's proper
 * spelling, or the form that actually played where the card accepts the one written.
 * The box IS the correction, so it carries the word and the speaker that says it; the card
 * itself stays closed, and nothing is on screen twice.
 *
 * No speaker where the card was asked by ear: the correction is then a SOURCE word, and
 * the target voice would say a German word in Swahili.
 */
@Composable
private fun AlmostHold(model: AppModel, flow: TurnFlow, hold: TurnFeedback.Almost, heard: Boolean) {
    val chrome = model.chrome
    val caption = when (hold.reason) {
        AlmostReason.Typo -> chrome.sessionAlmostTypo
        AlmostReason.Heard -> chrome.sessionAlmostHeard
    }
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.s)) {
        AlmostCorrection(
            caption, hold.correctForm, chrome,
            pronounce = if (heard) null else model.pronounceAction(hold.correctForm),
        )
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
                chrome.sessionOtherWord.format(other.word, other.meanings.joinToString(", ")),
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
            Text(chrome.sessionSkip, style = MaterialTheme.typography.bodyMedium)
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
private fun ProduceReveal(model: AppModel, ui: SessionUi, heard: Boolean) {
    val card = ui.card ?: return
    val chrome = model.chrome
    // why: a card asked by ear owes the MEANING back, so its reveal is shaped like the
    // recognition one — the answer where the answer goes, and the word that played
    // standing above it in writing, which is what a retype finishes against.
    if (heard) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DlSpace.s),
        ) {
            Headword(
                (listOf(card.source.text) + card.source.synonyms).joinToString(" / "),
                color = Dl.colors.accent,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (card.promptFeminineMarker) FeminineBadge(chrome)
        }
        return
    }
    TargetReveal(
        card.target, chrome,
        pronounce = model.pronounceAction(card.target.text),
    )
}
