package net.spross.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.spross.app.AppModel
import net.spross.app.clearFeedback
import net.spross.app.clearableCount
import net.spross.app.hasExportedBefore
import net.spross.app.hasFeedback
import net.spross.app.ownWordPairs
import net.spross.kern.box.FeedbackScope

/**
 * One action, offered over the whole lot, over what is new, over what the catalog is owed,
 * or over the whole lot with the outbox emptied behind it. It stays a plain button while it
 * has only the one thing to offer — before any copy has been taken "new" is the same list
 * as "everything", with no word pair written so is the outbox, and with nothing clearable
 * the last says nothing.
 */
@Composable
internal fun ScopedAction(
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
internal fun ClearAction(model: AppModel) {
    val chrome = model.chrome
    var confirming by remember { mutableStateOf(false) }
    TextButton(onClick = { confirming = true }) {
        Text(chrome.commonClear, color = Theme.colors.wrong)
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
internal fun Context.copyToClipboard(label: String, text: String) {
    getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(label, text))
}
