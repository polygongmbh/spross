package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import net.spross.app.AppModel
import net.spross.app.ownWordIds
import net.spross.app.saveOwnWord
import net.spross.kern.box.BoxEngine

/**
 * Writing down a word the catalog has none of — or rewriting one already written.
 *
 * Both sides are asked for, because a word is only studiable as a pair, and each field says
 * which language it wants with that language's flag in front of its own name for itself.
 * One side alone is still taken, as a SUGGESTION: the learner noticed a gap and only has the
 * half they came with. It is never scheduled — there is nothing to ask them yet — and waits
 * in the own-content section to be sent on to the catalog ([BoxOwnSection]).
 *
 * What happens to the word is kern's: [BoxEngine.addOwnWord] mints its id from the learnt
 * side, stores it under the pair's two languages, and PACKS it — the learner named this word
 * themselves, so waiting for growth to walk to it would be absurd. An EDIT
 * ([BoxEngine.updateOwnWord]) mints nothing: the id stays, and with it the schedule and the
 * queue slot, so fixing a typo never costs the progress made on the word.
 *
 * [initial] is what the form opens on — blank, a query, a card copied over, or a word
 * being rewritten — and [OwnWordDraft.editing] is what tells the last of those from the rest.
 */
@Composable
fun OwnWordForm(
    model: AppModel,
    initial: OwnWordDraft,
    /** Called once the word is in the box, studiable or still waiting for its other half. */
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val chrome = model.chrome
    val catalog = model.catalog ?: return
    val stamp = model.box?.joinStamp ?: return
    var draft by remember { mutableStateOf(initial) }
    val learningFocus = remember { FocusRequester() }
    val rewriting = draft.editing != null
    BackHandler { onCancel() }

    // why: the cursor belongs on the half that is actually missing — a form opened from a
    // failed search already carries the known side. A rewrite asks for no focus at all: the
    // learner came to change one of the two fields and has not said which.
    LaunchedEffect(Unit) { if (!rewriting) learningFocus.requestFocus() }

    fun label(code: String): String =
        chrome.boxOwnWordInLanguage.format(flaggedLanguage(catalog.languages[code], code))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Theme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (rewriting) chrome.boxOwnWordEdit else chrome.boxOwnWordTitle,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(chrome.commonCancel) }
        }
        WordField(
            label = label(stamp.source),
            value = draft.known,
            onValueChange = { draft = draft.copy(known = it) },
            imeAction = ImeAction.Next,
        )
        // Between the two fields, where what it does is visible: for the learner who filled
        // them in the wrong way round, which is a retype of both otherwise.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { draft = draft.swapped() }) {
                Text("⇅ ${chrome.boxOwnWordSwap}", style = MaterialTheme.typography.bodySmall)
            }
        }
        WordField(
            label = label(stamp.target),
            value = draft.learning,
            onValueChange = { draft = draft.copy(learning = it) },
            imeAction = ImeAction.Next,
            modifier = Modifier.focusRequester(learningFocus),
        )
        PictureField(
            label = chrome.boxOwnWordPicture,
            value = draft.emoji,
            onValueChange = { draft = draft.withPicture(it) },
        )
        Text(
            if (draft.isPair) chrome.boxOwnWordExplainer else chrome.boxOwnWordExplainerSuggestion,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                val word = draft.word(
                    source = stamp.source,
                    target = stamp.target,
                    taken = model.ownWordIds,
                ) ?: return@Button
                model.saveOwnWord(word, rewriting = rewriting)
                onDone()
            },
            enabled = draft.hasAnything,
            modifier = Modifier.fillMaxWidth().pressSpring(),
            shape = MaterialTheme.shapes.small,
        ) { Text(if (rewriting) chrome.boxOwnWordSave else chrome.boxOwnWordAdd) }
    }
}
