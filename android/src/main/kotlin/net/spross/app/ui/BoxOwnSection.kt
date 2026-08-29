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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.hasExportedBefore
import net.spross.app.hasFeedback
import net.spross.app.markExported
import net.spross.app.ownWords
import net.spross.app.removeOwnWord
import net.spross.app.reportMailBody
import net.spross.app.reportText
import net.spross.app.reportedCatalogCards
import net.spross.app.reportedIssue
import net.spross.app.suggestionText
import net.spross.kern.box.Feedback
import net.spross.kern.box.OwnWord
import net.spross.kern.box.OwnWords
import net.spross.kern.model.Card

/**
 * Everything the learner put into the box themselves, and everything they have to say back
 * about what the catalog put there — one section, at the foot of the box.
 *
 * It replaces both the own-words shelf and the old feedback block. Own words needed no shelf:
 * they are packed the moment they are written, so an area control offering to pack them said
 * nothing, while the progress bar over five hand-written words said less. And the reports had
 * nowhere to be reviewed at all except the flag on the row itself.
 *
 * Unlike a shelf it is ALWAYS drawn, empty or not: it carries the add button, which is the one
 * way into writing a word that does not start from a search that found nothing.
 *
 * [onWriteOwn] opens the form — blank from the header, on a copy or on the word itself from a
 * row's menu ([BoxRowMenu]).
 */
@Composable
internal fun BoxOwnSection(model: AppModel, onWriteOwn: (OwnWordDraft) -> Unit) {
    val chrome = model.chrome
    val context = LocalContext.current
    val box = model.box ?: return
    val words = model.ownWords
    val reported = model.reportedCatalogCards
    val actions = model.hasFeedback(onlyNew = false)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DlSpace.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                chrome.ownContentTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onWriteOwn(OwnWordDraft()) },
                modifier = Modifier.semantics { contentDescription = chrome.ownWordAddAction },
            ) { Icon(SprossIcons.Plus, contentDescription = null) }
        }
        // An empty panel is furniture: with nothing written and nothing filed, the header
        // and its one button are the whole section.
        if (words.isEmpty() && reported.isEmpty() && !actions) return@Column
        OwnContentPanel(model, box.cards, words, reported, actions, context, onWriteOwn)
    }
}

/** The section's body: the words, the reports, and the two ways to send the lot on. */
@Composable
private fun OwnContentPanel(
    model: AppModel,
    cards: Map<String, Card>,
    words: List<OwnWord>,
    reported: List<Card>,
    actions: Boolean,
    context: Context,
    onWriteOwn: (OwnWordDraft) -> Unit,
) {
    val chrome = model.chrome
    Column(
        modifier = Modifier.fillMaxWidth().panel().padding(DlSpace.l),
        verticalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        if (words.isNotEmpty()) {
            BlockLabel(chrome.ownWordsTitle)
            words.forEach { word ->
                // A word written in both languages IS a card, and reads as one — badge,
                // 💤, 🚩 and menu. One written in a single language joins nothing, so it
                // has no card row to be drawn as and gets its own.
                val card = cards[word.id]
                if (card != null) {
                    BoxCardRow(model, card, onWriteOwn = onWriteOwn)
                } else {
                    SuggestionRow(model, word, onWriteOwn)
                }
            }
            HorizontalDivider(color = Dl.colors.separator)
        }
        if (reported.isNotEmpty()) {
            BlockLabel(chrome.ownContentReported)
            reported.forEach { card -> ReportedRow(model, card) }
            HorizontalDivider(color = Dl.colors.separator)
        }
        if (actions) {
            Row(horizontalArrangement = Arrangement.spacedBy(DlSpace.m)) {
                ScopedAction(model, chrome.feedbackCopy) { onlyNew ->
                    context.copyToClipboard(chrome.ownContentTitle, model.reportText(onlyNew))
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

@Composable
private fun BlockLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One word waiting for its other half.
 *
 * The missing side is not a shortcoming of the entry, it is the whole point of it — what
 * the catalog owes — so the row says so rather than leaving a blank. It has no card, so it
 * has no standing to show and no schedule to act on: its menu is the two things that still
 * apply, filling in the other half and taking it back out.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionRow(model: AppModel, word: OwnWord, onWriteOwn: (OwnWordDraft) -> Unit) {
    val chrome = model.chrome
    val stamp = model.box?.joinStamp ?: return
    var menuOpen by remember(word.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .combinedClickable(
                onLongClickLabel = chrome.ownWordEdit,
                onLongClick = { menuOpen = true },
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
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuAction(chrome.ownWordEdit) {
                menuOpen = false
                onWriteOwn(OwnWordDraft.of(word, stamp.source, stamp.target))
            }
            MenuAction(chrome.ownWordRemove, destructive = true) {
                menuOpen = false
                model.removeOwnWord(word.id)
            }
        }
    }
}

/**
 * One problem filed against a CATALOG word: the pair it is about, and the comment where the
 * learner wrote one. The word's own row, wherever it stands on its shelf, wears the flag;
 * this is where the learner can read back what they actually said.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReportedRow(model: AppModel, card: Card) {
    val chrome = model.chrome
    var menuOpen by remember(card.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .combinedClickable(
                onLongClickLabel = chrome.reportEdit,
                onLongClick = { menuOpen = true },
                onClick = {},
            )
            .padding(horizontal = DlSpace.m, vertical = DlSpace.s),
        verticalArrangement = Arrangement.spacedBy(DlSpace.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DlSpace.m)) {
            Text("🚩", modifier = Modifier.semantics { contentDescription = chrome.reported })
            // Exposure surfaces render the TARGET side first (`kern/docs/reports.md`).
            Text(
                "${card.target.text} → ${card.source.text}",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
        }
        model.reportedIssue(card.id)?.comment?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
        }
        // The report's own two entries, and nothing else: this row is the report, not the word.
        CardMenu(model, card, menuOpen, learnerInput = "", onDismiss = { menuOpen = false })
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
