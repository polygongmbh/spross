package net.spross.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.restoreBoxes
import net.spross.kern.store.BoxBackup

/**
 * The first run's way around the pair: a backup from another phone brings its own,
 * so a learner who has one skips the pages and lands in the restored box ([restoreBoxes]).
 *
 * No confirmation stands over it, unlike the settings' import ([BackupSetting]):
 * a phone with no box yet has nothing for the file to replace.
 * [source] is the known language picked so far — the pair's other half where the file names none.
 */
@Composable
fun OnboardingImport(model: AppModel, chrome: Chrome, source: String, modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = resolver.openInputStream(uri) ?: error("no stream for $uri")
                    BoxBackup.decode(stream.use { it.readBytes().decodeToString() })
                }
            }
            read.onSuccess { model.restoreBoxes(it, firstRunSource = source) }
                .onFailure { failed = true }
        }
    }

    // Every type: providers label a .json file inconsistently, and the decode is the check.
    TextButton(onClick = { import.launch(arrayOf("*/*")) }, modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SprossIcons.PackIn, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(chrome.onboardingImport, style = MaterialTheme.typography.labelMedium)
        }
    }

    if (failed) {
        AlertDialog(
            onDismissRequest = { failed = false },
            title = { Text(chrome.settingsBackupImportFailed) },
            confirmButton = {
                TextButton(onClick = { failed = false }) { Text(chrome.commonDone) }
            },
        )
    }
}
