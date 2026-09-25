package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.catalogMatches
import net.spross.app.catalogText
import net.spross.app.merge
import net.spross.app.writtenText
import net.spross.kern.box.CatalogMatch
import net.spross.kern.box.MatchSide
import net.spross.kern.box.OwnWords

/**
 * The catalog catching up with the words the learner had to write themselves.
 *
 * A word is written by hand because the catalog had none for it; the catalog grows, and the
 * word lands in one eventually. This is where the two meet: every own word the catalog now
 * has a word for, beside the catalog's own writing of it, and one button that moves the
 * progress across ([net.spross.kern.box.CatalogMatches], `BoxEngine.mergeOwnWord`).
 *
 * Nothing merges unasked. The words where both halves agree arrive ticked — there is nothing
 * left to judge about those — and a match on one half only waits to be read: the catalog says
 * the other half differently, and which of the two is right is the learner's call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogMatchSheet(model: AppModel, onDismiss: () -> Unit) {
    val chrome = model.chrome
    // Taken once when the sheet opens: building it walks every card in the box.
    val matches = remember { model.catalogMatches() }
    var picked: Set<String> by remember {
        mutableStateOf(matches.filter { it.side == MatchSide.Both }.map { it.word.id }.toSet())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            Text(chrome.boxOwnMatchTitle, style = MaterialTheme.typography.titleLarge)
            if (matches.isEmpty()) {
                Text(
                    chrome.boxOwnMatchNone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val toggle: (String) -> Unit = { id ->
                picked = if (id in picked) picked - id else picked + id
            }
            // Kern hands the list back with the whole matches leading, so a run of one side
            // is one heading — and a side kern grows later heads itself rather than going
            // unshown.
            matches.groupBy { it.side }.forEach { (side, run) ->
                MatchGroup(model, heading(side, chrome), run, picked, toggle)
            }
            val kept = matches.filter { it.word.id in picked }
            Button(
                onClick = {
                    model.merge(kept)
                    onDismiss()
                },
                enabled = kept.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(chrome.boxOwnMatchMerge.format(kept.size))
            }
        }
    }
}

/**
 * Which heading a group wears. The sides are kern's; naming them is ours. The two one-sided
 * ones read alike to the learner — the catalog says the other half differently — so they
 * share a heading.
 */
private fun heading(side: MatchSide, chrome: Chrome): String =
    if (side == MatchSide.Both) chrome.boxOwnMatchGroupWhole else chrome.boxOwnMatchGroupHalf

@Composable
private fun MatchGroup(
    model: AppModel,
    title: String,
    matches: List<CatalogMatch>,
    picked: Set<String>,
    onToggle: (String) -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    matches.forEach { match ->
        MatchRow(model, match, kept = match.word.id in picked) { onToggle(match.word.id) }
    }
}

/**
 * The catalog's word, and under it the learner's own where the two are written differently —
 * which is what a one-sided match has to be read for before it is ticked. Where they are
 * written alike there is nothing under it: a line repeating the one above it is a line
 * nobody reads twice.
 *
 * The learner's line wears the pen the own-content panel wears everywhere else, so which of
 * the two lines is theirs needs no words to say.
 */
@Composable
private fun MatchRow(model: AppModel, match: CatalogMatch, kept: Boolean, onToggle: () -> Unit) {
    val catalog = model.catalogText(match)
    val written = model.writtenText(match)
    Row(
        // A checkbox row: TalkBack says whether the word is kept, not just the word.
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = kept, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            // why: ✓ against ✕, as the harvest list marks its rows — the row is a decision
            // about the word, and the two marks say which way it went.
            if (kept) SprossIcons.Check else SprossIcons.Close,
            contentDescription = null,
            tint = if (kept) Theme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            Text(catalog, style = MaterialTheme.typography.bodyMedium)
            if (written != catalog) {
                Text(
                    "${OwnWords.EMOJI} $written",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
