package net.spross.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spross.app.Chrome
import net.spross.kern.box.BriefWord
import net.spross.kern.box.HarvestKind
import net.spross.kern.box.HarvestWord
import net.spross.kern.box.WordListFile

/**
 * The pairs a paste or a word-list file carried, headed by where each stands against the box,
 * ticked a word at a time — shared by the chat sheet ([BriefingSheet]) and the settings import
 * ([WordListImportSheet]). Everything is shown and sorted, never filtered: only the ones the box
 * has nothing like arrive ticked ([initiallyPicked]).
 */
@Composable
internal fun HarvestReview(
    harvested: List<HarvestWord>,
    picked: Set<String>,
    chrome: Chrome,
    onToggle: (String) -> Unit,
) {
    // Kern's list arrives grouped, so the runs are already in it — and a kind kern grows later
    // heads itself rather than going unshown.
    harvested.groupBy { it.kind }.forEach { (kind, words) ->
        Text(
            heading(kind, chrome),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        words.forEach { found ->
            HarvestRow(found, kept = found.word.target in picked) { onToggle(found.word.target) }
        }
    }
}

/** The accept action, standing outside the scrolling list ([GrowableSheet]'s action slot). */
@Composable
internal fun HarvestKeepButton(kept: List<BriefWord>, chrome: Chrome, onKeep: () -> Unit) {
    Button(
        onClick = onKeep,
        enabled = kept.isNotEmpty(),
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = Theme.spacing.lg, vertical = Theme.spacing.md),
        shape = MaterialTheme.shapes.small,
    ) {
        val label = if (kept.size == 1) chrome.briefingReturnKeepOne else chrome.briefingReturnKeep
        Text(label.format(kept.size))
    }
}

/** The ones the learner asked for — those the box has nothing like. */
internal fun List<HarvestWord>.initiallyPicked(): Set<String> =
    filter { it.kind == HarvestKind.New }.mapTo(mutableSetOf()) { it.word.target }

internal fun List<HarvestWord>.kept(picked: Set<String>): List<BriefWord> =
    filter { it.word.target in picked }.map { it.word }

/**
 * A launcher for a word-list file: [onText] gets its text, or null when it cannot be read.
 * Any type is offered — exports name themselves `.txt`, `.csv`, `.tsv` or nothing at all.
 */
@Composable
internal fun rememberWordListPicker(onText: (String?) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            onText(text)
        }
    }
    return { launcher.launch(arrayOf("text/*", "*/*")) }
}

/** Whether a file is long enough to ask before it is read, against an unexpected wait. */
internal fun isLargeWordList(text: String): Boolean = WordListFile.lineCount(text) > WordListFile.LARGE_LINES

/** The question [isLargeWordList] asks, holding [text] until the learner says to read it. */
@Composable
internal fun LargeWordListDialog(text: String, chrome: Chrome, onRead: () -> Unit, onDismiss: () -> Unit) {
    val lines = NumberFormat.getIntegerInstance().format(WordListFile.lineCount(text))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chrome.importLarge.format(lines)) },
        confirmButton = { TextButton(onClick = onRead) { Text(chrome.importLargeRead) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(chrome.commonCancel) } },
    )
}

/** Which heading a group wears. The kinds are kern's; naming them is ours. */
private fun heading(kind: HarvestKind, chrome: Chrome): String = when (kind) {
    HarvestKind.New -> chrome.briefingGroupNew
    HarvestKind.Near -> chrome.briefingGroupNear
    HarvestKind.Held -> chrome.briefingGroupHeld
}

@Composable
private fun HarvestRow(found: HarvestWord, kept: Boolean, onToggle: () -> Unit) {
    Row(
        // A checkbox row: TalkBack says whether the word is kept, not just the word.
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = kept, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            // why: ✓ against ✕ rather than a filled circle against an empty one — the row
            // is a decision about the word, and the two marks say which way it went.
            if (kept) SprossIcons.Check else SprossIcons.Close,
            contentDescription = null,
            tint = if (kept) Theme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            Text(found.word.target, style = MaterialTheme.typography.bodyMedium)
            Text(
                gloss(found),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The gloss, and after it the box's own word where this one leans on it — which is the whole
 * reason the row came up unticked, said in the row rather than the heading.
 */
private fun gloss(found: HarvestWord): String {
    val match = found.match
    return if (found.kind == HarvestKind.Near && match != null) "${found.word.source} · ≈ $match"
    else found.word.source
}
