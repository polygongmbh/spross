package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.CardDisplay
import net.spross.app.SessionUi
import net.spross.app.autoplayPrompt
import net.spross.app.pronounceAction
import net.spross.kern.model.EmojiCue
import net.spross.kern.model.PresentationRole

@Composable
fun SessionScreen(model: AppModel) {
    val ui = model.sessionUi ?: return
    BackHandler { model.finishSession() }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SegmentsBar(ui.segments, ui.remaining, Modifier.weight(1f))
            ReadAloudSwitch(model)
            TextButton(onClick = { model.finishSession() }) { Text("✕") }
        }
        when {
            ui.card == null -> Summary(model, ui)
            ui.role == PresentationRole.Recognize -> RecognizeCard(model, ui)
            else -> ProduceCard(model, ui)
        }
    }
}

/**
 * The read-aloud switch, in constant chrome: the top bar never varies with the card
 * below it, so no card moves a point for the control (the card's own space belongs to
 * the word). It governs the SPOKEN WORDS only — the feedback chimes are their own
 * matter, and the media volume is the switch for everything.
 */
@Composable
private fun ReadAloudSwitch(model: AppModel) {
    val chrome = model.chrome
    val muted = model.pronouncer.muted
    Box(
        // why: toggleable rather than an IconButton — the control IS a switch, and a
        // button's own Role.Button would win the semantics merge against one set
        // around it. ONE stable label with the state as its VALUE: a label that flips
        // leaves TalkBack announcing the action as though it were the condition.
        modifier = Modifier
            .size(48.dp)
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

@Composable
private fun RecognizeCard(model: AppModel, ui: SessionUi) {
    val card = ui.card ?: return
    val chrome = model.chrome
    // The first exposure prompts before it teaches too — a learner who already
    // knows the word gets the moment to recall it (contract §3).
    var revealed by remember(card.id) { mutableStateOf(false) }

    // why: keyed on the card, so the prompt is said once as it arrives and the fire
    // belongs to that card alone. A produce card reaches here with nothing to say.
    LaunchedEffect(card.id) { model.autoplayPrompt() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        if (ui.emojiCue == EmojiCue.Upfront) {
            Text(card.emoji.orEmpty(), fontSize = 64.sp)
        }
        val promptForm = ui.promptForm ?: card.target.text
        Text(
            localizedTarget(promptForm, card.target.lang),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.pronounceOnTap(model.pronounceAction(promptForm), chrome),
        )
        if (!revealed) {
            Spacer(Modifier.weight(1f))
            Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) {
                Text(chrome.reveal)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (ui.emojiCue == EmojiCue.OnReveal) {
                    Text(card.emoji.orEmpty(), fontSize = 64.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(card.source.text, style = MaterialTheme.typography.headlineMedium)
                    if (card.promptFeminineMarker) {
                        Text(" ♀", style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }
                CardDisplay.alsoLine(card.source, chrome)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TargetReveal(
                card.target, chrome,
                pronounce = model.pronounceAction(card.target.text),
            )
            Spacer(Modifier.weight(1f))
            RatingButtons(chrome, onRate = { model.answerCurrent(it) })
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Summary(model: AppModel, ui: SessionUi) {
    val chrome = model.chrome
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎉", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(chrome.sessionDone, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            chrome.summaryLine.format(ui.introduced, ui.strengthened, ui.reviewed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = { model.continueEndless() }, modifier = Modifier.fillMaxWidth()) {
            Text(chrome.keepPracticing)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { model.finishSession() }, modifier = Modifier.fillMaxWidth()) {
            Text(chrome.finish)
        }
    }
}
