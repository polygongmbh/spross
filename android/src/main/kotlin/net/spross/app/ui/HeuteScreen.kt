package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel

@Composable
fun HeuteScreen(model: AppModel) {
    val chrome = model.chrome
    val stats = model.stats
    val catalog = model.catalog
    val stamp = model.box?.joinStamp

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(chrome.heuteTitle, style = MaterialTheme.typography.headlineLarge)
        if (catalog != null && stamp != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sourceName = catalog.languages[stamp.source]?.name ?: stamp.source
                val targetName = catalog.languages[stamp.target]?.name ?: stamp.target
                Text(
                    "$sourceName → $targetName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { model.editLanguages() }) { Text(chrome.changeLanguages) }
                // The only other door out of Heute: version, the read-aloud switch
                // and who spoke the recordings.
                TextButton(onClick = { model.openAbout() }) { Text(chrome.aboutButton) }
            }
        }

        if (stats != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Stat("${stats.dueCount}", chrome.dueLabel)
                    Stat("${stats.settledCount}", chrome.settledLabel)
                    Stat("${stats.activeCount - stats.settledCount}", chrome.freshLabel)
                    Stat("🔥 ${stats.streak}", "Streak")
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (model.sessionAvailable) {
            Button(onClick = { model.startSession() }, modifier = Modifier.fillMaxWidth()) {
                Text(chrome.practice, style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Text(
                if ((stats?.activeCount ?: 0) > 0) chrome.doneToday else chrome.emptyState,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            if ((stats?.activeCount ?: 0) > 0) {
                OutlinedButton(
                    onClick = { model.startExtraSession() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(chrome.extraRound)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
