package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.CHIME_CLEARANCE_MS
import net.spross.app.CardDisplay
import net.spross.app.SessionCoach
import net.spross.app.SessionUi
import net.spross.app.TurnFlow
import net.spross.app.audio.Pronouncer
import net.spross.app.autoplayPrompt
import net.spross.app.newTurn
import net.spross.app.pronounceAction
import net.spross.app.pronounceTarget
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.shownArticle

@Composable
fun SessionScreen(model: AppModel) {
    val ui = model.sessionUi ?: return
    BackHandler { model.finishSession() }

    Column(
        modifier = Modifier.fillMaxSize().padding(Theme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
    ) {
        RunTopBar(model, ui.segments, model::finishSession, ui.remaining, closeLabel = model.chrome.commonDone)
        if (ui.card == null) SessionSummary(model, ui) else TurnCard(model, ui)
    }
}

/**
 * One card, one turn. Every tap and keystroke below becomes a `TurnIntent`, and what
 * comes back is the whole next state plus the only acts this screen takes — kern's
 * `TurnMachine` decides what an answer is worth, this decides what it looks like.
 */
@Composable
private fun TurnCard(model: AppModel, ui: SessionUi) {
    val card = ui.card ?: return
    val hooks = rememberTurnHooks(model)
    // why: keyed on the card AND on how many answers stand behind it — an endless refill
    // can bring the same word back, and a turn carried over would arrive already answered.
    val flow = remember(card.id, ui.segments.size) {
        model.newTurn(ui, onTone = hooks.tone, onReleaseFocus = hooks.releaseFocus)
    } ?: return

    // why: keyed on the card, so the prompt is said once as it arrives and the fire
    // belongs to that card alone. A produce card reaches here with nothing to say.
    LaunchedEffect(card.id) { model.autoplayPrompt() }

    BeatEffect(flow.beatToken, flow.armedBeat, flow::advanceElapsed)

    // The produce card says its word once it has stopped asking — after a beat, so the
    // verdict cue is out of the way, and once per card however the pause was reached.
    var spoken by remember(card.id) { mutableStateOf(false) }
    val revealForm = flow.spokenReveal
    LaunchedEffect(revealForm) {
        val form = revealForm ?: return@LaunchedEffect
        if (spoken) return@LaunchedEffect
        spoken = true
        delay(CHIME_CLEARANCE_MS)
        model.pronounceTarget(form, Pronouncer.Trigger.AUTO)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        if (ui.role == PresentationRole.Recognize) {
            RecognizeTurn(model, ui, flow)
        } else {
            ProduceCard(model, ui, flow)
        }
        Spacer(Modifier.height(Theme.spacing.sm))
    }
}

/**
 * Comprehension check: reveal, then an honest self-grade — never typed, so no schedule is
 * ever graded against a language it was not learned with. The very first exposure takes
 * this path too: the word is prompted before it is taught, so a learner who already knows
 * it gets the moment to recall it (contract §3). The one field it can carry is the
 * write-out a first-exposure miss opens.
 */
@Composable
private fun RecognizeTurn(model: AppModel, ui: SessionUi, flow: TurnFlow) {
    val card = ui.card ?: return
    val chrome = model.chrome
    val promptForm = ui.promptForm ?: card.target.text
    // Grammar decorates the prompt only where the form on screen IS the citation: a
    // rotated synonym is a different word and can carry a different gender, so the
    // article steps aside rather than mislabel it (kern `shownArticle`).
    val article = shownArticle(CardDisplay.article(card.target), promptForm, card.target.text)
    val revealed = flow.answerRevealed

    ReportableCard(model, card, flow.answerOut, typed = { flow.input }) {
        VocabCard(
            emoji = card.emoji,
            cue = ui.emojiCue,
            revealed = revealed,
            // The prompt is still standing above the reveal — whatever form it rotated in
            // is on screen and is no longer an alternative.
            closingLines = if (revealed) {
                listOfNotNull(CardDisplay.alsoLine(card.target, chrome, promptForm))
            } else {
                emptyList()
            },
            note = CardDisplay.closingNote(card.target, flow.state.alsoMeans, chrome, revealed),
        ) {
            SpokenWord(model.pronounceAction(promptForm), chrome) {
                Headword(
                    localizedTarget(
                        if (article == null) {
                            AnnotatedString(promptForm)
                        } else {
                            Theme.colors.articleColoredText(card.target)
                        },
                        card.target.lang,
                    ),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (article != null) {
                CardDisplay.pluralLine(card.target, chrome)?.let { CardLine(it) }
            }
            if (revealed) {
                CardReveal {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                    ) {
                        Headword(
                            (listOf(card.source.text) + card.source.synonyms).joinToString(" / "),
                            color = Theme.colors.accent,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (card.promptFeminineMarker) FeminineBadge(chrome)
                    }
                }
            }
        }
    }

    if (model.coachActive) {
        SessionCoach.recognizeLine(chrome, ui.role, revealed)?.let { PauseLine(it) }
    }
    if (!revealed) {
        Button(
            onClick = { flow.reveal() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(chrome.commonReveal)
        }
        return
    }
    val step = flow.copyStep
    if (step == null) {
        VerdictButtons(chrome, flow, caption = model.gradeCaption)
    } else {
        WriteOutStep(model, flow, step, model.targetName(ui))
    }
}
