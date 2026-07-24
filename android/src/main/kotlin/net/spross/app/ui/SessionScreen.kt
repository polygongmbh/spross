package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.CardDisplay
import net.spross.app.SessionUi
import net.spross.kern.model.PresentationRole

@Composable
fun SessionScreen(model: AppModel) {
    val ui = model.sessionUi ?: return
    BackHandler { model.finishSession() }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SegmentsBar(ui.segments, ui.remaining, Modifier.weight(1f))
            TextButton(onClick = { model.finishSession() }) { Text("✕") }
        }
        when {
            ui.card == null -> Summary(model, ui)
            ui.role == PresentationRole.Recognize -> RecognizeCard(model, ui)
            else -> ProduceCard(model, ui)
        }
    }
}

@Composable
private fun RecognizeCard(model: AppModel, ui: SessionUi) {
    val card = ui.card ?: return
    val chrome = model.chrome
    var revealed by remember(card.id) { mutableStateOf(ui.firstExposure) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        if (ui.showEmoji) {
            Text(card.emoji.orEmpty(), fontSize = 64.sp)
        }
        Text(
            ui.promptForm ?: card.target.text,
            style = MaterialTheme.typography.headlineLarge,
        )
        if (!revealed) {
            Spacer(Modifier.weight(1f))
            Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) {
                Text(chrome.reveal)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            TargetReveal(card.target, chrome)
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
