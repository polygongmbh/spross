package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.CHIME_CLEARANCE_MS
import net.spross.app.CardDisplay
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
        modifier = Modifier.fillMaxSize().padding(DlSpace.l),
        verticalArrangement = Arrangement.spacedBy(DlSpace.l),
    ) {
        SessionTopBar(model, ui)
        if (ui.card == null) SessionSummary(model, ui) else TurnCard(model, ui)
    }
}

/**
 * The session's constant chrome, in the order the round is worked: the way OUT leading,
 * where the thumb that started the round already is; the progress carrying the whole
 * width between the two controls; the read-aloud switch trailing. Nothing up here varies
 * with the card below it, so no card pays a point of layout for it.
 */
@Composable
private fun SessionTopBar(model: AppModel, ui: SessionUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        CloseSessionButton(model)
        SegmentsBar(ui.segments, ui.remaining, Modifier.weight(1f))
        ReadAloudSwitch(model)
    }
}

/** The way out of a running round — first in the bar, so it is never hunted for. */
@Composable
private fun CloseSessionButton(model: AppModel) {
    val chrome = model.chrome
    Box(
        modifier = Modifier
            .chromeDisc()
            .semantics(mergeDescendants = true) { contentDescription = chrome.finish }
            .clickable(role = Role.Button) { model.finishSession() },
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", style = MaterialTheme.typography.titleMedium, color = Dl.colors.textSecondary)
    }
}

/**
 * The tinted disc both chrome controls sit in: one shape, one 48 dp target, so the pair
 * reads as chrome rather than as two loose glyphs jostling in a corner.
 */
@Composable
private fun Modifier.chromeDisc(): Modifier =
    this.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)

/**
 * The read-aloud switch, in constant chrome: the top bar never varies with the card
 * below it, so no card moves a point for the control (the card's own space belongs to
 * the word). It governs the SPOKEN WORDS only — the verdict cues are their own
 * matter, and the media volume is the switch for everything.
 */
@Composable
fun ReadAloudSwitch(model: AppModel) {
    val chrome = model.chrome
    val muted = model.pronouncer.muted
    Box(
        // why: toggleable rather than an IconButton — the control IS a switch, and a
        // button's own Role.Button would win the semantics merge against one set
        // around it. ONE stable label with the state as its VALUE: a label that flips
        // leaves TalkBack announcing the action as though it were the condition.
        modifier = Modifier
            .chromeDisc()
            .toggleable(
                value = !muted,
                role = Role.Switch,
                onValueChange = { model.pronouncer.muted = !it },
            )
            // why: merged, or the glyph inside would be a node of its own and TalkBack
            // would read the picture of a loudspeaker after the switch it belongs to.
            .semantics(mergeDescendants = true) {
                contentDescription = chrome.readAloud
                stateDescription = if (muted) chrome.stateOff else chrome.stateOn
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (muted) "🔇" else "🔊")
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
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    // why: keyed on the card AND on how many answers stand behind it — an endless refill
    // can bring the same word back, and a turn carried over would arrive already answered.
    val flow = remember(card.id, ui.segments.size) {
        model.newTurn(
            ui,
            onTone = { view.cueTone(it) },
            // why: a pause that waits for a tap must not hold the keyboard — it covers
            // the very button the pause is waiting for.
            onReleaseFocus = { focusManager.clearFocus() },
        )
    } ?: return

    // why: keyed on the card, so the prompt is said once as it arrives and the fire
    // belongs to that card alone. A produce card reaches here with nothing to say.
    LaunchedEffect(card.id) { model.autoplayPrompt() }

    // The beat kern armed. Nothing is ever armed where a screen reader runs (TurnFlow
    // renders an explicit Weiter instead), so this only waits out beats that may run.
    LaunchedEffect(flow.beatToken) {
        val tier = flow.beat ?: return@LaunchedEffect
        delay(tier.delayMs)
        flow.advanceElapsed()
    }

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
        verticalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        if (ui.role == PresentationRole.Recognize) {
            RecognizeTurn(model, ui, flow)
        } else {
            ProduceCard(model, ui, flow)
        }
        Spacer(Modifier.height(DlSpace.s))
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

    VocabCard(
        emoji = card.emoji,
        emojiShown = emojiShowing(ui.emojiCue, revealed),
    ) {
        SpokenWord(model.pronounceAction(promptForm), chrome) {
            Text(
                localizedTarget(
                    if (article == null) {
                        AnnotatedString(promptForm)
                    } else {
                        Dl.colors.articleColoredText(card.target)
                    },
                    card.target.lang,
                ),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (article != null) {
            CardDisplay.pluralLine(card.target, chrome)?.let { CardLine(it) }
        }
        if (revealed) {
            // The note is the card's last line whichever side authored it — a literal
            // gloss belongs to the concept, not to one of its two faces.
            CardReveal(note = card.target.note ?: card.source.note) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DlSpace.s),
                ) {
                    Text(
                        (listOf(card.source.text) + card.source.synonyms).joinToString(" / "),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Dl.colors.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (card.promptFeminineMarker) FeminineBadge()
                }
                // The prompt is still standing above the reveal — whatever form it
                // rotated in is on screen and is no longer an alternative.
                CardDisplay.alsoLine(card.target, chrome, promptForm)?.let { CardLine(it) }
            }
        }
    }

    if (!revealed) {
        Button(
            onClick = { flow.reveal() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(chrome.reveal)
        }
        return
    }
    val step = flow.copyStep
    if (step == null) {
        VerdictButtons(chrome, flow)
    } else {
        WriteOutStep(model, flow, step, model.targetName(ui))
    }
}
