package net.spross.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.briefing
import net.spross.app.harvest
import net.spross.app.keepHarvested
import net.spross.kern.box.BriefWord

/**
 * Taking the box into a conversation the app does not host, and bringing back what the
 * conversation turned up.
 *
 * The app ships no assistant and knows the name of none: it hands over a text and a share
 * intent, and whichever chat the learner already has takes it from there. What that text may
 * say is kern's ([net.spross.kern.box.Briefing]) — this sheet shows only how much of the box
 * is in it, because a wall of prompt scrolling past is not a preview.
 *
 * The way back is the half that pays: a conversation turns up words no catalog has, the
 * assistant is asked to list them, and pasting that list here reads them into own words
 * ([net.spross.kern.box.Harvest]). Nothing is taken in unasked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BriefingSheet(model: AppModel, onDismiss: () -> Unit) {
    val chrome = model.chrome
    val context = LocalContext.current
    val briefing = remember { model.briefing() } ?: return
    var copied by remember { mutableStateOf(false) }
    var harvested by remember { mutableStateOf(emptyList<BriefWord>()) }
    var dropped by remember { mutableStateOf(emptySet<String>()) }
    var pasteWasEmpty by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            Text(chrome.briefingTitle, style = MaterialTheme.typography.titleLarge)
            Text(
                chrome.briefingIntro,
                style = MaterialTheme.typography.bodyMedium,
            )
            BriefingTally(model, briefing.freeCount, briefing.inPlay.size, briefing.newWords.size)
            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                TextButton(onClick = {
                    context.copyBrief(chrome.briefingTitle, briefing.text)
                    copied = true
                }) { Text(if (copied) chrome.briefingCopied else chrome.briefingCopy) }
                TextButton(onClick = { context.shareBrief(chrome.briefingTitle, briefing.text) }) {
                    Text(chrome.briefingShare)
                }
            }
            HorizontalDivider(color = Theme.colors.separator)
            Text(chrome.briefingReturnTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                chrome.briefingReturnExplainer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                val found = model.harvest(context.clipboardText())
                harvested = found
                dropped = emptySet()
                pasteWasEmpty = found.isEmpty()
            }) { Text(chrome.briefingReturnPaste) }
            if (pasteWasEmpty) {
                Text(
                    chrome.briefingReturnEmpty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            harvested.forEach { word ->
                HarvestRow(word, kept = word.target !in dropped) {
                    dropped = if (word.target in dropped) dropped - word.target else dropped + word.target
                }
            }
            if (harvested.isNotEmpty()) {
                val kept = harvested.filter { it.target !in dropped }
                Button(
                    onClick = {
                        model.keepHarvested(kept)
                        onDismiss()
                    },
                    enabled = kept.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                ) {
                    val label = if (kept.size == 1) chrome.briefingReturnKeepOne else chrome.briefingReturnKeep
                    Text(label.format(kept.size))
                }
            }
        }
    }
}

/** What the brief carries, as counts. The words themselves are the box, listed everywhere else. */
@Composable
private fun BriefingTally(model: AppModel, free: Int, inPlay: Int, fresh: Int) {
    val chrome = model.chrome
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (free > 0) {
            TallyLine((if (free == 1) chrome.briefingTallyFreeOne else chrome.briefingTallyFree).format(free))
        }
        if (inPlay > 0) {
            TallyLine((if (inPlay == 1) chrome.briefingTallyInPlayOne else chrome.briefingTallyInPlay).format(inPlay))
        }
        if (fresh > 0) {
            TallyLine((if (fresh == 1) chrome.briefingTallyNewOne else chrome.briefingTallyNew).format(fresh))
        }
    }
}

@Composable
private fun TallyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun HarvestRow(word: BriefWord, kept: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
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
            Text(word.target, style = MaterialTheme.typography.bodyMedium)
            Text(
                word.source,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Context.clipboardText(): String =
    getSystemService(ClipboardManager::class.java)
        ?.primaryClip?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.coerceToText(this)?.toString()
        .orEmpty()

private fun Context.copyBrief(label: String, text: String) {
    getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}

/**
 * The share sheet, which is how the brief reaches a chat app without this app knowing the
 * name of a single one of them.
 */
private fun Context.shareBrief(title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, title))
}
