package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.dateSprosse
import net.spross.app.speakFormOnTap
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.trainer.DateDrill
import net.spross.kern.trainer.DateReferenceGroup
import net.spross.kern.trainer.DateReferenceRow

/**
 * The reading half of the dates page: the seven weekdays and the twelve months, both
 * sides beside each other.
 *
 * The table is [DateDrill.reference] — the same joined rows the run grades against, so it
 * cannot claim one name and ask for another. Under a learned name stand the forms the
 * drill also accepts and teaches: its short form, its other lexemes (de `Sonnabend`), and
 * what it becomes inside a date where that differs (uk `березня`).
 *
 * Reading matter, so the CONTENT is the control: a whole row says its learned-side name,
 * and the page discloses that gesture once under its heading instead of growing a speaker
 * on every line.
 */
@Composable
fun DateReferenceSection(model: AppModel, content: DateDrillContent, chrome: Chrome) {
    val groups = remember(content) { DateDrill.reference(content) }
    OverviewHeading(chrome.datesReference)
    // The hint is the affordance: where nothing on the page can be heard it would
    // promise a gesture that does nothing.
    val audible = groups.any { group ->
        group.rows.any { model.speakFormOnTap(it.target, content.target) != null }
    }
    if (audible) TapToHearHint(chrome)
    for (group in groups) KindGroup(group, model, content, chrome)
}

/** One bare-name pool — the Sprosse rows above already name the two, so this reuses them. */
@Composable
private fun KindGroup(
    group: DateReferenceGroup,
    model: AppModel,
    content: DateDrillContent,
    chrome: Chrome,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.s)) {
        Text(
            chrome.dateSprosse(listOf(group.kind)).uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = Dl.colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        OverviewPanel {
            for (row in group.rows) NameRow(row, model, content, chrome)
        }
    }
}

/**
 * One name, twice: the known language on the left, the learned one on the right, with the
 * learned side's other forms under it — its short form, its other lexemes, and its
 * in-a-date form, on one caption line.
 *
 * The whole row says the LEARNED side. The other column is the reader's own language, and
 * a reference sheet is read to hear what one cannot yet say.
 */
@Composable
private fun NameRow(
    row: DateReferenceRow,
    model: AppModel,
    content: DateDrillContent,
    chrome: Chrome,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // why: one name is one TalkBack stop — both sides and the forms under them are
            // the same row of the table.
            .semantics(mergeDescendants = true) { }
            .pronounceOnTap(
                model.speakFormOnTap(row.target, content.target),
                chrome,
                minHeight = 0.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            localizedTarget(row.source, content.source),
            style = MaterialTheme.typography.titleMedium,
            color = Dl.colors.textPrimary,
        )
        Spacer(Modifier.width(DlSpace.m).weight(1f))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                localizedTarget(row.target, content.target),
                style = MaterialTheme.typography.titleMedium,
                color = Dl.colors.accent,
                textAlign = TextAlign.End,
            )
            otherForms(row)?.let {
                Text(
                    localizedTarget(it, content.target),
                    style = MaterialTheme.typography.bodySmall,
                    color = Dl.colors.textSecondary,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** What else the learned name answers to; null keeps the caption line off the row. */
private fun otherForms(row: DateReferenceRow): String? {
    val forms = listOfNotNull(row.abbr) + row.synonyms + listOfNotNull(row.dateForm)
    return if (forms.isEmpty()) null else forms.joinToString(" · ")
}
