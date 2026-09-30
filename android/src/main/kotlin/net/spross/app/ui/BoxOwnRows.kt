package net.spross.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.otherPairFlags
import net.spross.app.otherPairLanguageNames
import net.spross.app.otherPairText
import net.spross.app.removeOwnWord
import net.spross.app.reportedIssue
import net.spross.app.suggestionText
import net.spross.kern.box.OwnWord
import net.spross.kern.box.OwnWords
import net.spross.kern.model.Card

/**
 * One word waiting for its other half.
 *
 * The missing side is not a shortcoming of the entry, it is the whole point of it — what
 * the catalog owes — so the row says so rather than leaving a blank.
 */
@Composable
internal fun SuggestionRow(model: AppModel, word: OwnWord, onEdit: () -> Unit) {
    EntryRow(
        model, word,
        lines = 1,
        line = model.suggestionText(word),
        said = word.comment,
        tail = model.chrome.boxOwnWordNeedsTranslation,
        editLabel = model.chrome.boxOwnEntryEdit,
        onEdit = onEdit,
    )
}

/**
 * One finished word the open pair cannot ask.
 *
 * The tail names the pair it IS written in, which is the whole of why it has no card: the
 * learner finished it, and a changed known language does not unfinish it.
 */
@Composable
internal fun OtherPairRow(model: AppModel, word: OwnWord, onWriteOwn: (OwnWordDraft) -> Unit) {
    val stamp = model.box?.joinStamp ?: return
    EntryRow(
        model, word,
        lines = 1,
        line = model.otherPairText(word),
        said = word.comment,
        tail = model.otherPairFlags(word),
        tailSaid = model.otherPairLanguageNames(word),
        editLabel = model.chrome.boxOwnWordEdit,
        onEdit = { onWriteOwn(OwnWordDraft.of(word, stamp.source, stamp.target)) },
    )
}

/**
 * One entry the box holds no card for — a suggestion, or a note. With no card it has no
 * standing to show and no schedule to act on: its menu is the three things that still apply,
 * writing it over, taking what it says elsewhere, and dropping it.
 *
 * [line] defaults to the entry's comment, which is the whole of a note, and is what the copy
 * action hands out; [said] is the note
 * under the line where the entry has one, and [tail] what the row has left to say about it —
 * what the catalog still owes, or the flag of the language this pair cannot read it in.
 * [tailSaid] names a [tail] that is a picture, for a screen reader handed no picture at all.
 * [editLabel] names what [onEdit] opens: a word form for a finished pair, the free-text
 * editor for an entry that is half a word or none.
 */
@Composable
internal fun EntryRow(
    model: AppModel,
    word: OwnWord,
    lines: Int,
    editLabel: String,
    line: String = word.comment.orEmpty(),
    said: String? = null,
    tail: String? = null,
    tailSaid: String? = null,
    onEdit: () -> Unit,
) {
    val chrome = model.chrome
    val context = LocalContext.current
    var menuOpen by remember(word.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier.entryRow(editLabel) { menuOpen = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Text(word.emoji ?: OwnWords.EMOJI, style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(line, style = MaterialTheme.typography.bodyLarge, maxLines = lines)
            if (!said.isNullOrEmpty()) {
                Text(
                    said,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
        }
        if (!tail.isNullOrEmpty()) {
            Text(
                tail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = tailSaid?.let { name ->
                    Modifier.semantics { contentDescription = name }
                } ?: Modifier,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuAction(editLabel) {
                menuOpen = false
                onEdit()
            }
            MenuAction(chrome.commonCopy) {
                menuOpen = false
                context.copyToClipboard(chrome.boxOwnTitle, line)
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
@Composable
internal fun ReportedRow(model: AppModel, card: Card) {
    val chrome = model.chrome
    var menuOpen by remember(card.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier.entryRow(chrome.reportEdit) { menuOpen = true },
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
            Text("🚩", modifier = Modifier.semantics { contentDescription = chrome.a11yReportReported })
            // Exposure surfaces render the TARGET side first (`kern/docs/reports.md`).
            Text(
                "${card.target.text} → ${card.source.text}",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
 * The tile every row here stands on: no card behind it, so a tap does nothing and the long
 * press, named [label], opens its menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.entryRow(label: String, onLongClick: () -> Unit): Modifier = this
    .fillMaxWidth()
    .sizeIn(minHeight = 48.dp)
    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
    .clip(MaterialTheme.shapes.small)
    .combinedClickable(onLongClickLabel = label, onLongClick = onLongClick, onClick = {})
    .padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.sm)
