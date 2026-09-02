package net.spross.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import net.spross.app.AppModel
import net.spross.app.dismissReportedIssue
import net.spross.app.reportIssue
import net.spross.app.reportedIssue
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.OwnWords
import net.spross.kern.model.Card

/**
 * Saying what is wrong with a word: a wrong translation, a synonym the catalog ought to
 * accept, a prompt that reads badly.
 *
 * Filing changes nothing about the schedule. Putting the word to sleep is the menu's OTHER
 * entry, deliberately not a switch in the dialog — a word can be wrong and still worth
 * practicing, and one can be irrelevant without being wrong.
 */

/**
 * The menu a word grows on a long press: the report entry — filing one, or reopening and
 * withdrawing one already filed — and whatever the surface puts around it. [before] and
 * [after] are both handed the way to close the menu, since every entry closes it before it
 * acts; a surface that has something irreversible to offer puts it in [after], last.
 *
 * [learnerInput] is what stood in the answer field when the menu opened. It is taken THEN
 * rather than read here: the field empties as the turn advances, and the answer the catalog
 * rejected IS the report in the common case.
 */
@Composable
internal fun CardMenu(
    model: AppModel,
    card: Card,
    expanded: Boolean,
    learnerInput: String,
    onDismiss: () -> Unit,
    before: @Composable (close: () -> Unit) -> Unit = {},
    after: @Composable (close: () -> Unit) -> Unit = {},
) {
    val chrome = model.chrome
    var reporting by remember(card.id) { mutableStateOf(false) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        before(onDismiss)
        // why: a report is what the learner says to whoever maintains the CATALOG, so a
        // word they wrote themselves grows no entry — there is nobody to tell, and the
        // edit form beside it already changes anything they would have reported.
        if (!OwnWords.owns(card.id)) {
            if (model.reportedIssue(card.id) == null) {
                MenuAction(chrome.reportAction) { onDismiss(); reporting = true }
            } else {
                // Reopening beats refiling: the comment is what the maintainer reads, and a
                // learner with more to say should not have to withdraw the report to say it.
                // Withdrawing is inside that form, not a second row beside this one.
                MenuAction(chrome.reportEdit) { onDismiss(); reporting = true }
            }
        }
        after(onDismiss)
    }
    if (reporting) {
        ReportIssueDialog(model, card, learnerInput) { reporting = false }
    }
}

/**
 * The revealed card's long press, in a session: the two things a learner can say about the
 * word in front of them, and they are unrelated — one is about the CATALOG being wrong, the
 * other about this word not being worth their time. Neither implies the other, so neither
 * is a step in the other's flow.
 *
 * [typed] is read at the moment of the press, not at the moment the dialog opens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReportableCard(
    model: AppModel,
    card: Card,
    revealed: Boolean,
    typed: () -> String,
    content: @Composable () -> Unit,
) {
    val chrome = model.chrome
    var open by remember(card.id) { mutableStateOf(false) }
    var captured by remember(card.id) { mutableStateOf("") }
    Box(
        // why: only once the answer is out — before it, the learner has not seen the
        // translation they would be reporting, and a menu over the prompt is a menu over a
        // question. The tap does nothing: the card's own controls carry every tap it has.
        modifier = Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = revealed,
            onLongClickLabel = chrome.reportAction,
            onLongClick = { captured = typed(); open = true },
            onClick = {},
        ),
    ) {
        content()
        CardMenu(
            model = model,
            card = card,
            expanded = open,
            learnerInput = captured,
            onDismiss = { open = false },
            after = { close ->
                MenuAction(chrome.boxCardSleep) {
                    close()
                    // why: the round moves on with it — being made to rate a word one
                    // has just said should never be asked again is the exact busywork
                    // this removes.
                    model.suspendCurrentCard()
                }
            },
        )
    }
}

/**
 * The report itself: the word it is about, a comment that is optional and says so, and what
 * they typed — shown rather than offered, because a learner who has to tick a box to attach
 * their answer will not.
 *
 * Opened over a report already filed it carries that comment, so rewriting it is an edit
 * rather than a retype — and carries the way to withdraw it, which is here rather than in
 * the menu so a learner can read what they filed before deciding to drop it.
 */
@Composable
private fun ReportIssueDialog(
    model: AppModel,
    card: Card,
    learnerInput: String,
    onDismiss: () -> Unit,
) {
    val chrome = model.chrome
    var comment by remember { mutableStateOf(model.reportedIssue(card.id)?.comment.orEmpty()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chrome.reportTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                // The word as it stood, so the report names what it is about without the
                // learner having to retype it.
                Text(card.target.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    card.source.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(chrome.reportComment) },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (learnerInput.isNotBlank()) {
                    Text(
                        "${chrome.reportTyped} $learnerInput",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    chrome.reportExplainer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Last and set apart: the one thing in the form that editing again
                // cannot take back. Absent while nothing is on file to withdraw.
                if (model.reportedIssue(card.id) != null) {
                    TextButton(
                        onClick = { model.dismissReportedIssue(card.id); onDismiss() },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(chrome.reportDismiss, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // why: an edit is filed from a row, where nothing was being answered — the
                // answer the first report rode in with must not be dropped for that.
                val typed = learnerInput.ifBlank {
                    model.reportedIssue(card.id)?.learnerInput.orEmpty()
                }
                model.reportIssue(card.id, comment, typed)
                onDismiss()
            }) { Text(chrome.reportSend) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(chrome.commonCancel) } },
    )
}
