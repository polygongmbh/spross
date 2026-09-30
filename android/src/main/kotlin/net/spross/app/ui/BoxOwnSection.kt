package net.spross.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.clearableCount
import net.spross.app.hasBriefing
import net.spross.app.hasFeedback
import net.spross.app.markExported
import net.spross.app.ownWordPairs
import net.spross.app.remarks
import net.spross.app.reportMailBody
import net.spross.app.reportText
import net.spross.app.reportedCatalogCards
import net.spross.app.suggestions
import net.spross.kern.box.Feedback
import net.spross.kern.box.OwnWord
import net.spross.kern.model.Card

/**
 * Everything the learner put into the box themselves, and everything they have to say back
 * about what the catalog put there — one section, at the foot of the box.
 *
 * The words stand in three blocks: the pairs, which ARE cards; the suggestions, still
 * waiting for a half the catalog owes; and the notes, which name no word and so suggest
 * none.
 *
 * Own words get no shelf: they are queued the moment they are written, so an area control
 * offering to queue them would say nothing, and a progress bar over five hand-written words
 * less still.
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
    val notes = model.remarks
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
            IconButton(onClick = { onWriteOwn(OwnWordDraft()) }) {
                Icon(
                    SprossIcons.Plus,
                    contentDescription = chrome.a11yBoxOwnWordAddAction,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // An empty panel is furniture: with nothing written and nothing filed, the header
        // and its one button are the whole section.
        if (model.hasBriefing || pairs.isNotEmpty() || suggestions.isNotEmpty() ||
            notes.isNotEmpty() || reported.isNotEmpty() || actions
        ) {
            OwnContentPanel(
                model, box.cards, pairs, suggestions, notes, reported, actions, onWriteOwn,
            )
        }
    }
}

/**
 * The section's body: the word pairs, the suggestions, the notes, the reports, and the two
 * ways to send what the catalog is owed on.
 */
@Composable
private fun OwnContentPanel(
    model: AppModel,
    cards: Map<String, Card>,
    pairs: List<OwnWord>,
    suggestions: List<OwnWord>,
    notes: List<OwnWord>,
    reported: List<Card>,
    actions: Boolean,
    onWriteOwn: (OwnWordDraft) -> Unit,
) {
    val chrome = model.chrome
    val context = LocalContext.current
    var briefingOpen by remember { mutableStateOf(false) }
    var matchOpen by remember { mutableStateOf(false) }
    // The suggestion or note being rewritten, which opens as free text rather than as a
    // word pair ([OwnEntrySheet]).
    var editingEntry by remember { mutableStateOf<OwnWord?>(null) }
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
        // A word this profile can pair IS a card and reads as one — badge, 💤, 🚩 and menu.
        // One written in two languages it cannot pair has no card row to be drawn as and
        // lists as its own two halves, here where it has always belonged. One written in a
        // single language joins nothing at all, and stands in a block of its own beside
        // the reports.
        if (pairs.isNotEmpty()) {
            BlockLabel(chrome.boxOwnShelf)
            pairs.forEach { word ->
                val card = cards[word.id]
                if (card != null) BoxCardRow(model, card, onWriteOwn = onWriteOwn)
                else OtherPairRow(model, word, onWriteOwn)
            }
            HorizontalDivider(color = Theme.colors.separator)
        }
        if (suggestions.isNotEmpty()) {
            BlockLabel(chrome.boxOwnSuggestions)
            suggestions.forEach { word -> SuggestionRow(model, word) { editingEntry = word } }
            HorizontalDivider(color = Theme.colors.separator)
        }
        // A note names no word, so it suggests none: what it is about need not be in the
        // catalog at all, and it stands apart from the words the catalog owes an answer to.
        if (notes.isNotEmpty()) {
            BlockLabel(chrome.boxOwnNotes)
            notes.forEach { note ->
                EntryRow(model, note, lines = 3, editLabel = chrome.boxOwnEntryEdit) {
                    editingEntry = note
                }
            }
            HorizontalDivider(color = Theme.colors.separator)
        }
        if (reported.isNotEmpty()) {
            BlockLabel(chrome.boxOwnReported)
            reported.forEach { card -> ReportedRow(model, card) }
            HorizontalDivider(color = Theme.colors.separator)
        }
        val written = pairs.isNotEmpty() || suggestions.isNotEmpty()
        if (actions || written) {
            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                if (actions) {
                    ScopedAction(model, chrome.commonCopy) { onlyNew, scope ->
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
                // The catalog measured against the words already here — one more thing to do
                // with them, beside the two that hand them on and the one that empties them.
                if (written) TextButton(onClick = { matchOpen = true }) { Text(chrome.boxOwnMatchAction) }
            }
        }
    }
    if (briefingOpen) BriefingSheet(model) { briefingOpen = false }
    if (matchOpen) CatalogMatchSheet(model) { matchOpen = false }
    editingEntry?.let { entry -> OwnEntrySheet(model, entry) { editingEntry = null } }
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
