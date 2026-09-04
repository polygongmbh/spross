package net.spross.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
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
import net.spross.app.clearFeedback
import net.spross.app.hasBriefing
import net.spross.app.clearableCount
import net.spross.app.hasExportedBefore
import net.spross.app.hasFeedback
import net.spross.app.markExported
import net.spross.app.ownWordPairs
import net.spross.app.removeOwnWord
import net.spross.app.reportMailBody
import net.spross.app.reportText
import net.spross.app.reportedCatalogCards
import net.spross.app.reportedIssue
import net.spross.app.suggestionText
import net.spross.app.suggestions
import net.spross.kern.box.Feedback
import net.spross.kern.box.FeedbackScope
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
    val box = model.box ?: return
    val pairs = model.ownWordPairs
    val suggestions = model.suggestions
    val reported = model.reportedCatalogCards
    val actions = model.hasFeedback(onlyNew = false)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                chrome.boxOwnTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onWriteOwn(OwnWordDraft()) },
                modifier = Modifier.semantics { contentDescription = chrome.boxOwnWordAddAction },
            ) { Icon(SprossIcons.Plus, contentDescription = null) }
        }
        // An empty panel is furniture: with nothing written and nothing filed, the header
        // and its one button are the whole section.
        if (model.hasBriefing || pairs.isNotEmpty() || suggestions.isNotEmpty() ||
            reported.isNotEmpty() || actions
        ) {
            OwnContentPanel(model, box.cards, pairs, suggestions, reported, actions, onWriteOwn)
        }
    }
}

/**
 * The section's body: the word pairs, the suggestions, the reports, and the two ways to
 * send what the catalog is owed on.
 */
@Composable
private fun OwnContentPanel(
    model: AppModel,
    cards: Map<String, Card>,
    pairs: List<OwnWord>,
    suggestions: List<OwnWord>,
    reported: List<Card>,
    actions: Boolean,
    onWriteOwn: (OwnWordDraft) -> Unit,
) {
    val chrome = model.chrome
    val context = LocalContext.current
    var briefingOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().panel().padding(Theme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        // The box handed to a conversation the app does not host. It leads the panel
        // because it is the one entry here that goes OUT and comes back: the words under
        // it are what a conversation writes home.
        if (model.hasBriefing) {
            BriefingRow(model) { briefingOpen = true }
            HorizontalDivider(color = Theme.colors.separator)
        }
        // A word written in both languages IS a card and reads as one — badge, 💤, 🚩 and
        // menu. One written in a single language joins nothing, so it has no card row to
        // be drawn as, and stands in a block of its own beside the reports.
        if (pairs.isNotEmpty()) {
            BlockLabel(chrome.boxOwnShelf)
            pairs.forEach { word ->
                cards[word.id]?.let { BoxCardRow(model, it, onWriteOwn = onWriteOwn) }
            }
            HorizontalDivider(color = Theme.colors.separator)
        }
        if (suggestions.isNotEmpty()) {
            BlockLabel(chrome.boxOwnSuggestions)
            suggestions.forEach { word -> SuggestionRow(model, word, onWriteOwn) }
            HorizontalDivider(color = Theme.colors.separator)
        }
        if (reported.isNotEmpty()) {
            BlockLabel(chrome.boxOwnReported)
            reported.forEach { card -> ReportedRow(model, card) }
            HorizontalDivider(color = Theme.colors.separator)
        }
        if (actions) {
            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                ScopedAction(model, chrome.reportExportCopy) { onlyNew, scope ->
                    context.copyToClipboard(chrome.boxOwnTitle, model.reportText(onlyNew, scope))
                    model.markExported(scope)
                }
                ScopedAction(model, chrome.reportExportSend) { onlyNew, scope ->
                    val body = model.reportMailBody(onlyNew, scope) ?: return@ScopedAction
                    context.openFeedbackMail(Feedback.MAIL_SUBJECT, body)
                    model.markExported(scope)
                }
                if (model.clearableCount > 0) ClearAction(model)
            }
        }
    }
    if (briefingOpen) BriefingSheet(model) { briefingOpen = false }
}

/** The entry into [BriefingSheet] — what it is, and what it is for, in two lines. */
@Composable
private fun BriefingRow(model: AppModel, onOpen: () -> Unit) {
    val chrome = model.chrome
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(chrome.briefingTitle, style = MaterialTheme.typography.bodyLarge)
            Text(
                chrome.briefingRowSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(SprossIcons.ChevronRight, contentDescription = null,
             tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                onLongClickLabel = chrome.boxOwnWordEdit,
                onLongClick = { menuOpen = true },
                onClick = {},
            )
            .padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Text(word.emoji ?: OwnWords.EMOJI, style = MaterialTheme.typography.titleMedium)
        Text(
            model.suggestionText(word),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            chrome.boxOwnWordNeedsTranslation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuAction(chrome.boxOwnWordEdit) {
                menuOpen = false
                onWriteOwn(OwnWordDraft.of(word, stamp.source, stamp.target))
            }
            MenuAction(chrome.boxOwnWordRemove, destructive = true) {
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
            .padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
            Text("🚩", modifier = Modifier.semantics { contentDescription = chrome.reportReported })
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
 * One action, offered over the whole lot, over what is new, over what the catalog is owed,
 * or over the whole lot with the outbox emptied behind it. It stays a plain button while it
 * has only the one thing to offer — before any copy has been taken "new" is the same list
 * as "everything", with no word pair written so is the outbox, and with nothing clearable
 * the last says nothing.
 */
@Composable
private fun ScopedAction(
    model: AppModel,
    label: String,
    run: (onlyNew: Boolean, scope: FeedbackScope) -> Unit,
) {
    val chrome = model.chrome
    var open by remember { mutableStateOf(false) }
    val whole = FeedbackScope.Everything
    val exported = model.hasExportedBefore
    val clearable = model.clearableCount > 0
    // The narrower offer is only worth making where it says something the wider one does
    // not: with no word pair written, the outbox IS the lot.
    val outbox = model.ownWordPairs.isNotEmpty() && model.hasFeedback(false, FeedbackScope.Outbox)
    if (!exported && !clearable) {
        TextButton(onClick = { run(false, whole) }) { Text(label) }
        return
    }
    Box {
        TextButton(onClick = { open = true }) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (exported) {
                DropdownMenuItem(
                    text = { Text(chrome.reportExportScopeNew) },
                    enabled = model.hasFeedback(onlyNew = true),
                    onClick = { open = false; run(true, whole) },
                )
            }
            if (outbox) {
                DropdownMenuItem(
                    text = { Text(chrome.reportExportScopeOutbox) },
                    onClick = { open = false; run(false, FeedbackScope.Outbox) },
                )
            }
            DropdownMenuItem(
                text = { Text(chrome.reportExportScopeAll) },
                onClick = { open = false; run(false, whole) },
            )
            if (clearable) {
                // why: the lot has just gone to the clipboard or into a draft, so there is
                // nothing left to lose and nothing to ask about.
                DropdownMenuItem(
                    text = { Text(chrome.reportExportScopeAllClear, color = Theme.colors.wrong) },
                    onClick = { open = false; run(false, whole); model.clearFeedback() },
                )
            }
        }
    }
}

/**
 * Emptying the outbox on its own, with nothing copied first — the one control in this
 * section that can lose something unread, so it asks.
 */
@Composable
private fun ClearAction(model: AppModel) {
    val chrome = model.chrome
    var confirming by remember { mutableStateOf(false) }
    TextButton(onClick = { confirming = true }) {
        Text(chrome.reportExportClear, color = Theme.colors.wrong)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            // The question IS the title, as on iOS ([BoxSettings]).
            title = { Text(chrome.reportExportClearConfirm.format(model.clearableCount)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    model.clearFeedback()
                }) { Text(chrome.commonClear, color = Theme.colors.wrong) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(chrome.commonCancel) }
            },
        )
    }
}

/** The system clipboard, straight — no Compose handle in between to go stale. */
private fun Context.copyToClipboard(label: String, text: String) {
    getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(label, text))
}
