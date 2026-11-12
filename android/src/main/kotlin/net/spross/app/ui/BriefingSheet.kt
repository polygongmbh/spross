package net.spross.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.briefing
import net.spross.app.harvest
import net.spross.app.harvestFile
import net.spross.app.keepHarvested
import net.spross.kern.box.HarvestWord

/**
 * Taking the box into a conversation the app does not host, and bringing back what the
 * conversation turned up.
 *
 * The app ships no assistant and knows the name of none: it hands over a text and a share
 * intent, and whichever chat the learner already has takes it from there. What that text may
 * say is kern's ([net.spross.kern.box.Briefing]) and is never shown here: 7 KB of prompt
 * scrolling past is a wall, not a preview. What the sheet spends its words on instead is the
 * three moves the loop takes; the box's counts would only name the box back at the learner,
 * who already has it.
 *
 * The way back is the half that pays: a conversation turns up words no catalog has, the
 * assistant is asked to list them, and pasting that list here reads them into own words
 * ([net.spross.kern.box.Harvest]) — or from a file ([net.spross.kern.box.WordListFile]). Nothing is taken in unasked: every pair the paste carried
 * stands under the heading for where it stands against the box, and only the ones the box has
 * nothing like arrive ticked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BriefingSheet(model: AppModel, onDismiss: () -> Unit) {
    val chrome = model.chrome
    val context = LocalContext.current
    val briefing = remember { model.briefing() } ?: return
    var copied by remember { mutableStateOf(false) }
    var harvested by remember { mutableStateOf(emptyList<HarvestWord>()) }
    // Ticked target forms. Set from the paste, then the learner's — a word dropped and one
    // never offered are the same answer, so one set holds both.
    var picked by remember { mutableStateOf(emptySet<String>()) }
    // What the last paste or file turned out empty, named under the buttons.
    var emptyNote by remember { mutableStateOf<String?>(null) }
    // A file past WordListFile.LARGE_LINES, held until the learner says to read it.
    var largeFile by remember { mutableStateOf<String?>(null) }
    val show = { found: List<HarvestWord>, empty: String ->
        harvested = found
        picked = found.initiallyPicked()
        emptyNote = if (found.isEmpty()) empty else null
    }
    val readFile = { text: String -> show(model.harvestFile(text), chrome.briefingReturnFileEmpty) }
    val pickFile = rememberWordListPicker { text ->
        when {
            text == null -> emptyNote = chrome.briefingReturnFileEmpty
            isLargeWordList(text) -> largeFile = text
            else -> readFile(text)
        }
    }

    GrowableSheet(
        onDismiss = onDismiss,
        content = {
            Text(chrome.briefingTitle, style = MaterialTheme.typography.titleLarge)
            Text(chrome.briefingLead, style = MaterialTheme.typography.bodyMedium)
            BriefingSteps(chrome)
            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                TextButton(onClick = {
                    context.copyBrief(chrome.briefingTitle, briefing.text)
                    copied = true
                }) { Text(if (copied) chrome.briefingCopied else chrome.commonCopy) }
                TextButton(onClick = { context.shareBrief(chrome.briefingTitle, briefing.text) }) {
                    Text(chrome.briefingShare)
                }
            }
            HorizontalDivider(color = Theme.colors.separator)
            Text(chrome.briefingReturnTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                chrome.briefingReturnLead,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                TextButton(onClick = { show(model.harvest(context.clipboardText()), chrome.briefingReturnEmpty) }) {
                    Text(chrome.briefingReturnPaste)
                }
                TextButton(onClick = pickFile) { Text(chrome.briefingReturnFile) }
            }
            emptyNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HarvestReview(harvested, picked, chrome) { target ->
                picked = if (target in picked) picked - target else picked + target
            }
        },
        action = {
            if (harvested.isNotEmpty()) {
                val kept = harvested.kept(picked)
                HarvestKeepButton(kept, chrome) {
                    model.keepHarvested(kept)
                    onDismiss()
                }
            }
        },
    )
    largeFile?.let { text ->
        LargeWordListDialog(text, chrome, onRead = { largeFile = null; readFile(text) }, onDismiss = { largeFile = null })
    }
}

/** The loop in three moves, numbered: take it out, talk, bring the words back. */
@Composable
private fun BriefingSteps(chrome: Chrome) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        StepLine(1, chrome.briefingStepCopy)
        StepLine(2, chrome.briefingStepTalk)
        StepLine(3, chrome.briefingStepBack)
    }
}

@Composable
private fun StepLine(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        Text("$number.", style = MaterialTheme.typography.bodySmall, color = Theme.colors.accent)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
