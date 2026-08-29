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
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import net.spross.app.AppModel
import net.spross.app.dismissReportedIssue
import net.spross.app.reportIssue
import net.spross.app.reportedIssue
import net.spross.kern.box.BoxEngine
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
 * The menu a word grows on a long press: the report entry (or the way back out of one
 * already filed), and whatever the surface adds to it — [extra] is handed the way to close
 * the menu, since every entry closes it before it acts.
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
    extra: @Composable (close: () -> Unit) -> Unit = {},
) {
    val chrome = model.chrome
    var reporting by remember(card.id) { mutableStateOf(false) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (model.reportedIssue(card.id) == null) {
            DropdownMenuItem(
                text = { Text(chrome.reportAction) },
                onClick = { onDismiss(); reporting = true },
            )
        } else {
            DropdownMenuItem(
                text = { Text(chrome.reportDismiss) },
                onClick = { onDismiss(); model.dismissReportedIssue(card.id) },
            )
        }
        extra(onDismiss)
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
        CardMenu(model, card, open, captured, onDismiss = { open = false }) { close ->
            DropdownMenuItem(
                text = { Text(chrome.sleep) },
                onClick = {
                    close()
                    model.updateBox { BoxEngine.setSuspended(it, card.id, true) }
                },
            )
        }
    }
}

/**
 * The report itself: the word it is about, a comment that is optional and says so, and what
 * they typed — shown rather than offered, because a learner who has to tick a box to attach
 * their answer will not.
 */
@Composable
private fun ReportIssueDialog(
    model: AppModel,
    card: Card,
    learnerInput: String,
    onDismiss: () -> Unit,
) {
    val chrome = model.chrome
    var comment by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chrome.reportTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DlSpace.m)) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                model.reportIssue(card.id, comment, learnerInput)
                onDismiss()
            }) { Text(chrome.reportSend) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(chrome.cancel) } },
    )
}
