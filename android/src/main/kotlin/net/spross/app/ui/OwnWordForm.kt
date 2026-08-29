package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import net.spross.app.AppModel
import net.spross.kern.box.BoxEngine

/**
 * Writing down a word the catalog has none of. Reached only from a search that found
 * nothing — the moment the learner has already proved the box cannot answer them.
 *
 * Both sides are asked for, because a word is only studiable as a pair. The KNOWN side
 * arrives prefilled from [query]: someone typing into a search box is far more often
 * naming what they want to be able to SAY than a form they already met in the wild — and
 * so the cursor belongs on the half that is actually missing.
 *
 * One side alone is still taken, as a SUGGESTION: the learner noticed a gap and only has
 * the half they came with. It is never scheduled — there is nothing to ask them yet — and
 * waits in the feedback section to be sent on to the catalog ([BoxFeedbackSection]).
 *
 * What happens to the word is kern's: [BoxEngine.addOwnWord] mints its id from the learnt
 * side, stores it under the pair's two languages, and PACKS it — the learner named this
 * word themselves, so waiting for growth to walk to it would be absurd.
 */
@Composable
fun OwnWordForm(
    model: AppModel,
    query: String,
    /** Called once the word is in the box; true where it joined a card rather than waiting. */
    onAdded: (joined: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val chrome = model.chrome
    val catalog = model.catalog ?: return
    val stamp = model.box?.joinStamp ?: return
    var draft by remember { mutableStateOf(OwnWordDraft(known = query)) }
    val learningFocus = remember { FocusRequester() }
    BackHandler { onCancel() }

    LaunchedEffect(Unit) { learningFocus.requestFocus() }

    fun languageName(code: String): String = catalog.languages[code]?.name ?: code

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DlSpace.xl),
        verticalArrangement = Arrangement.spacedBy(DlSpace.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                chrome.ownWordTitle,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(chrome.cancel) }
        }
        WordField(
            label = chrome.ownWordInLanguage.format(languageName(stamp.source)),
            value = draft.known,
            onValueChange = { draft = draft.copy(known = it) },
            imeAction = ImeAction.Next,
        )
        WordField(
            label = chrome.ownWordInLanguage.format(languageName(stamp.target)),
            value = draft.learning,
            onValueChange = { draft = draft.copy(learning = it) },
            imeAction = ImeAction.Next,
            modifier = Modifier.focusRequester(learningFocus),
        )
        WordField(
            label = chrome.ownWordPicture,
            value = draft.emoji,
            onValueChange = { draft = draft.copy(emoji = it) },
            imeAction = ImeAction.Done,
        )
        Text(
            if (draft.isPair) chrome.ownWordsExplainer else chrome.ownWordSuggestion,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                model.updateBox { state ->
                    val word = draft.word(
                        source = stamp.source,
                        target = stamp.target,
                        taken = state.ownWords.mapTo(mutableSetOf()) { it.id },
                    ) ?: return@updateBox state
                    BoxEngine.addOwnWord(state, word, model.now())
                }
                onAdded(draft.isPair)
            },
            enabled = draft.hasAnything,
            modifier = Modifier.fillMaxWidth().pressSpring(),
            shape = MaterialTheme.shapes.small,
        ) { Text(chrome.ownWordAdd) }
    }
}

/**
 * One labeled field.
 *
 * why: no autocapitalization and no autocorrect — a word is not a sentence, and the
 * automatic capital puts one on a Swahili noun, which is simply the wrong spelling of the
 * word being stored. Whoever writes German capitalizes it themselves.
 */
@Composable
private fun WordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.xs)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = imeAction,
            ),
            modifier = modifier.fillMaxWidth(),
        )
    }
}
