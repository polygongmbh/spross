package net.spross.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spross.app.AppModel
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.store.BoxBackup

/**
 * Carrying the boxes across a reinstall or to another phone: every language's progress out
 * to one file, and back in ([BoxBackup]).
 *
 * A picked file is read whole before anything is asked, so the confirmation only ever
 * stands over a restore that can land.
 */
@Composable
fun BackupSetting(model: AppModel, catalog: Catalog) {
    val chrome = model.chrome
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Map<String, String>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val json = model.backupJson()
                    val stream = resolver.openOutputStream(uri, "wt") ?: error("no stream for $uri")
                    stream.use { it.write(json.encodeToByteArray()) }
                }
            }
            if (written.isFailure) failure = chrome.settingsBackupExportFailed
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = resolver.openInputStream(uri) ?: error("no stream for $uri")
                    BoxBackup.decode(stream.use { it.readBytes().decodeToString() })
                }
            }
            read.onSuccess { pending = it }.onFailure { failure = chrome.settingsBackupImportFailed }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Text(chrome.settingsBackupTitle, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
            TextButton(onClick = { export.launch("Spross-${LocalDate.now()}.json") }) {
                Text(chrome.settingsBackupExport)
            }
            // Every type: providers label a .json file inconsistently, and the decode is the check.
            TextButton(onClick = { import.launch(arrayOf("*/*")) }) {
                Text(chrome.settingsBackupImport)
            }
        }
        SettingHint(chrome.settingsBackupHint)
    }

    pending?.let { documents ->
        val names = documents.keys.sorted().joinToString(", ") { LanguageChoices.name(it, catalog.languages[it]) }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(chrome.settingsBackupConfirm.format(names)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    model.restoreBoxes(documents)
                }) { Text(chrome.settingsBackupReplace, color = Theme.colors.wrong) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(chrome.commonCancel) }
            },
        )
    }
    failure?.let { message ->
        AlertDialog(
            onDismissRequest = { failure = null },
            title = { Text(message) },
            confirmButton = {
                TextButton(onClick = { failure = null }) { Text(chrome.commonDone) }
            },
        )
    }
}
