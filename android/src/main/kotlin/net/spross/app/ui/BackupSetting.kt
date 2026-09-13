package net.spross.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import net.spross.kern.store.StoredBoxes

/**
 * Carrying the boxes across a reinstall or to another phone: the progress in every language
 * the learner has one — or in the one on screen — out to a file, and back in ([BoxBackup]).
 *
 * A picked file is read whole before anything is asked, so the confirmation only ever
 * stands over a restore that can land.
 */
@Composable
fun BackupSetting(model: AppModel, catalog: Catalog, target: String) {
    val chrome = model.chrome
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<StoredBoxes?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    // The languages an export would carry — what the button offers to narrow to.
    var carried by remember { mutableStateOf(emptyList<String>()) }
    var only by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { carried = withContext(Dispatchers.IO) { model.backupLanguages() } }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val json = model.backupJson(only)
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

    // why: the file is named for what it carries, so two of them in one folder are told
    // apart before either is opened.
    val start = { pick: String? ->
        only = pick
        export.launch("Spross-${pick?.let { "$it-" } ?: ""}${LocalDate.now()}.json")
    }

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Text(chrome.settingsBackupTitle, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
            // A plain button while the file can only say one thing, a choice once the
            // learner has a second language in the box: the whole box travels to a new
            // phone, one language is what they hand to someone learning it.
            val choosable = carried.size > 1 && target in carried
            Box {
                TextButton(onClick = { if (choosable) menuOpen = true else start(null) }) {
                    Text(chrome.settingsBackupExport)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val name = LanguageChoices.name(target, catalog.languages[target])
                    DropdownMenuItem(
                        text = { Text(chrome.settingsBackupExportOnly.format(name)) },
                        onClick = { menuOpen = false; start(target) },
                    )
                    DropdownMenuItem(
                        text = { Text(chrome.settingsBackupExportAll) },
                        onClick = { menuOpen = false; start(null) },
                    )
                }
            }
            // Every type: providers label a .json file inconsistently, and the decode is the check.
            TextButton(onClick = { import.launch(arrayOf("*/*")) }) {
                Text(chrome.settingsBackupImport)
            }
        }
        SettingHint(chrome.settingsBackupHint)
    }

    pending?.let { imported ->
        val names = imported.boxes.keys.sorted()
            .joinToString(", ") { LanguageChoices.name(it, catalog.languages[it]) }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(chrome.settingsBackupConfirm.format(names)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    model.restoreBoxes(imported)
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
