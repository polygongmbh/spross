package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import net.spross.app.AppModel
import net.spross.app.saveOwnEntry
import net.spross.kern.box.OwnWord

/**
 * Rewriting an entry the box holds no card for: a suggestion still waiting for its other
 * half, or a note that names no word at all.
 *
 * Neither is a word pair, and neither is edited as one. What the learner wrote is free text —
 * a half-remembered form, a question, a sentence about the catalog — so it is written back
 * into boxes that grow with what goes in them, the half in the language it was written in and
 * the note under it.
 *
 * The word form's second language field is deliberately not here: an entry has one side by
 * definition, and an empty field beside the written one is how that half gets cleared without
 * the learner meaning to ([OwnWordForm] is what a finished pair opens).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OwnEntrySheet(model: AppModel, entry: OwnWord, onDismiss: () -> Unit) {
    val chrome = model.chrome
    val catalog = model.catalog ?: return
    // The one language it is written in — none at all for a note, which is why a note is
    // edited as its comment and nothing else.
    val language = entry.languages.firstOrNull()
    var text by rememberSaveable(entry.id) {
        mutableStateOf(language?.let { entry.texts[it] }.orEmpty())
    }
    var comment by rememberSaveable(entry.id) { mutableStateOf(entry.comment.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            Text(chrome.boxOwnEntryEdit, style = MaterialTheme.typography.titleLarge)
            if (language != null) {
                WordField(
                    label = chrome.boxOwnWordInLanguage
                        .format(flaggedLanguage(catalog.languages[language], language)),
                    value = text,
                    onValueChange = { text = it },
                    imeAction = ImeAction.Next,
                    prose = true,
                )
            }
            WordField(
                label = chrome.boxOwnWordComment,
                value = comment,
                onValueChange = { comment = it },
                imeAction = ImeAction.Done,
                prose = true,
            )
            Text(
                if (text.isNotBlank()) {
                    chrome.boxOwnWordExplainerSuggestion
                } else {
                    chrome.boxOwnWordExplainerRemark
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    model.saveOwnEntry(entry, text, comment)
                    onDismiss()
                },
                enabled = text.isNotBlank() || comment.isNotBlank(),
                modifier = Modifier.fillMaxWidth().pressSpring(),
                shape = MaterialTheme.shapes.small,
            ) { Text(chrome.boxOwnWordSave) }
        }
    }
}
