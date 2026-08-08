package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.kern.model.Language
import net.spross.kern.trainer.ReferenceEntry
import net.spross.kern.trainer.Trainer

/**
 * How a language counts, as a table: every band kern names, each row a written value beside
 * the reading the drill grades against.
 *
 * GENERATED, never authored — the readings come out of the very packs the run grades
 * against, so the page cannot claim one reading and mark another. Kern names the bands; the
 * heading each one gets is chrome and is resolved here.
 *
 * One component, two doors: the numbers page stacks it under its own heading, and the
 * in-run "?" raises the very same table, so a look-up mid-drill lands on exactly the page
 * the overview shows.
 */
@Composable
fun NumberReferenceTable(language: Language, chrome: Chrome, modifier: Modifier = Modifier) {
    // why: empty for a language kern has no pack for — `reference` requires one, and the
    // absence is checked here rather than trusted of every caller.
    val sections = remember(language) {
        if (Trainer.supports(language)) Trainer.reference(language) else emptyList()
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DlSpace.l),
    ) {
        for (section in sections) {
            Column(verticalArrangement = Arrangement.spacedBy(DlSpace.s)) {
                // A band this build has no wording for still gets its rows: a new band
                // must be able to land in kern first.
                chrome.numberSections[section.key]?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Dl.colors.textSecondary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                        .padding(DlSpace.l),
                    verticalArrangement = Arrangement.spacedBy(DlSpace.xs),
                ) {
                    for (entry in section.entries) ReferenceRow(entry, language)
                }
            }
        }
    }
}

/**
 * Value and reading on one line. One TalkBack stop — "1 000 → eintausend", not two stops
 * that have to be paired by ear — and the reading wraps rather than truncating, because
 * this is the page a learner reads the language off.
 */
@Composable
private fun ReferenceRow(entry: ReferenceEntry, language: Language) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        Text(
            entry.value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = Dl.colors.textSecondary,
        )
        Text(
            localizedTarget(entry.reading, language),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The whole numbers page, one tap away mid-run — the overview's own table, not a second,
 * smaller truth beside it. It covers the run rather than replacing it, so closing it lands
 * back on the very question that raised it.
 */
@Composable
fun NumberReferenceOverlay(
    model: AppModel,
    language: String,
    chrome: Chrome,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // why: the panel SWALLOWS taps — it covers the run rather than replacing it,
            // and an unconsumed tap would reach the button standing underneath.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { },
            )
            .padding(DlSpace.l),
        verticalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        BackHandler { onDismiss() }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                chrome.numbersPage.format(model.languageName(language)),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onDismiss) { Text(chrome.finish) }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            NumberReferenceTable(language, chrome)
        }
    }
}
