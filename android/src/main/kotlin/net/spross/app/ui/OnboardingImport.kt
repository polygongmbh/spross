package net.spross.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
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
private const val IMPORT_ICON = "import"

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
        // why: the icon rides inline, so a label wrapped beside the hero keeps it on its first line.
        Text(
            buildAnnotatedString {
                appendInlineContent(IMPORT_ICON)
                append(" ")
                append(chrome.onboardingImport)
            },
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            inlineContent = mapOf(
                IMPORT_ICON to InlineTextContent(
                    Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter),
                ) { Icon(SprossIcons.Import, contentDescription = null) },
            ),
        )
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
