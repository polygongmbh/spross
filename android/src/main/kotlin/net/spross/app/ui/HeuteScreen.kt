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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.letterDrillAvailable

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
                    // The pair yields space to the doors beside it rather than pushing
                    // one off the edge; `fill = false` keeps it packed left as before.
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(onClick = { model.editLanguages() }) { Text(chrome.changeLanguages) }
                // The only other door out of Heute: version, the read-aloud switch
                // and who spoke the recordings.
                TextButton(onClick = { model.openAbout() }) { Text(chrome.aboutButton) }
                // The box itself: every word the profile holds, packed or not. The icon
                // carries the name rather than a label — the row is already full.
                TextButton(
                    onClick = { model.openBox() },
                    modifier = Modifier.semantics { contentDescription = chrome.boxNav },
                ) { Text("📦") }
            }
        }

        if (stats != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Stat("${stats.dueCount}", chrome.dueLabel)
                    Stat("${stats.consolidatedCount}", chrome.consolidatedLabel)
                    Stat("${stats.learningCount}", chrome.freshLabel)
                    Stat("🔥 ${stats.streak}", "Streak")
                }
            }
        }

        // Fortschritt: the same fortnight the streak above was counted from, on the very
        // refresh that produced it — the strip reads kern's walk, never one of its own.
        ActivityStrip(model.activityWindow, stats?.streak ?: 0, chrome)

        // The platform's first trainer. It appears by itself once the synthesizer has
        // bound (the predicate is observable — a cold start answers "no voice" for a
        // moment), and stays put while reading aloud is switched off: the drill says so
        // and offers the one tap that undoes it, which hiding the chip would not.
        if (model.letterDrillAvailable) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(chrome.trainingTitle, style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = { model.startLetterDrill() },
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text("🔤 ${chrome.lettersTitle}")
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (model.sessionAvailable) {
            Button(
                onClick = { model.startSession() },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(chrome.practice, style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Text(
                if ((stats?.activeCount ?: 0) > 0) chrome.doneToday else chrome.emptyState,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            // The round has to come back with something; active cards alone did not
            // promise that.
            if (model.canPracticeExtra) {
                OutlinedButton(
                    onClick = { model.startExtraSession() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
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
