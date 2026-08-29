package net.spross.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import net.spross.app.hasExportedBefore
import net.spross.app.hasFeedback
import net.spross.app.markExported
import net.spross.app.ownWordsText
import net.spross.app.reportMailBody
import net.spross.app.suggestionText
import net.spross.app.suggestions
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.Feedback
import net.spross.kern.box.OwnWord
import net.spross.kern.box.OwnWords

/**
 * What the learner has to say back about the catalog: the words they had to write down
 * themselves with only one half, and the problems they filed against words that are in it.
 *
 * It sits at the bottom of the box, after the shelves, and appears only once there is
 * something in it — an empty complaints box is furniture. Reported words keep their place on
 * their own shelf ([BoxCardRow] flags them there); what this section lists are the
 * SUGGESTIONS, which no shelf can show because a word written in one language joins nothing
 * and is never scheduled.
 *
 * The two actions take the same lot two ways: onto the clipboard, or into a mail to whoever
 * maintains the catalog. Both offer "everything" or "only what is new", and both mark the
 * copy taken, so the next "new" means what it says.
 */
@Composable
internal fun BoxFeedbackSection(model: AppModel) {
    if (!model.hasFeedback(onlyNew = false)) return
    val chrome = model.chrome
    val context = LocalContext.current
    val suggestions = model.suggestions

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DlSpace.s),
    ) {
        Text(chrome.feedbackTitle, style = MaterialTheme.typography.titleLarge)
        Column(
            modifier = Modifier.fillMaxWidth().panel().padding(DlSpace.l),
            verticalArrangement = Arrangement.spacedBy(DlSpace.m),
        ) {
            if (suggestions.isNotEmpty()) {
                Text(
                    chrome.feedbackSuggestions,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                suggestions.forEach { SuggestionRow(model, it) }
                HorizontalDivider(color = Dl.colors.separator)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DlSpace.m)) {
                ScopedAction(model, chrome.feedbackCopy) { onlyNew ->
                    context.copyToClipboard(chrome.feedbackTitle, model.ownWordsText(onlyNew))
                    model.markExported()
                }
                ScopedAction(model, chrome.feedbackSend) { onlyNew ->
                    val body = model.reportMailBody(onlyNew) ?: return@ScopedAction
                    context.openFeedbackMail(Feedback.MAIL_SUBJECT, body)
                    model.markExported()
                }
            }
        }
    }
}

/**
 * One word waiting for its other half.
 *
 * The missing side is not a shortcoming of the entry, it is the whole point of it — what
 * the catalog owes — so the row says so rather than leaving a blank. A long press takes it
 * back out, the same gesture a card row grows for a word the learner wrote.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionRow(model: AppModel, word: OwnWord) {
    val chrome = model.chrome
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .combinedClickable(
                onLongClickLabel = chrome.ownWordRemove,
                onLongClick = { model.updateBox { BoxEngine.removeOwnWord(it, word.id) } },
                onClick = {},
            )
            .padding(horizontal = DlSpace.m, vertical = DlSpace.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        Text(word.emoji ?: OwnWords.EMOJI, style = MaterialTheme.typography.titleMedium)
        Text(
            model.suggestionText(word),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            chrome.feedbackNeedsTranslation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One action, offered over the whole lot or only over what is new. Until a copy has ever
 * been taken the two would be the same list, so it stays a plain button and asks nothing.
 */
@Composable
private fun ScopedAction(model: AppModel, label: String, run: (onlyNew: Boolean) -> Unit) {
    val chrome = model.chrome
    var open by remember { mutableStateOf(false) }
    if (!model.hasExportedBefore) {
        TextButton(onClick = { run(false) }) { Text(label) }
        return
    }
    Box {
        TextButton(onClick = { open = true }) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(chrome.feedbackScopeNew) },
                enabled = model.hasFeedback(onlyNew = true),
                onClick = { open = false; run(true) },
            )
            DropdownMenuItem(
                text = { Text(chrome.feedbackScopeAll) },
                onClick = { open = false; run(false) },
            )
        }
    }
}

/** The system clipboard, straight — no Compose handle in between to go stale. */
private fun Context.copyToClipboard(label: String, text: String) {
    getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(label, text))
}
