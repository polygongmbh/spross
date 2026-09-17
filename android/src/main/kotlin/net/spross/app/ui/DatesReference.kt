package net.spross.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.dateSprosse
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.catalog.dateNotes
import net.spross.kern.trainer.DateDrill
import net.spross.kern.trainer.DateReferenceRow

/**
 * The reading half of the dates page: the seven weekdays and the twelve months, both sides
 * beside each other.
 *
 * The table is [DateDrill.reference] — the same joined rows the run grades against, so it
 * cannot claim one name and ask for another. Under a learned name stand the forms the drill
 * also accepts and teaches: its short form, its other lexemes (de `Sonnabend`), and what it
 * becomes inside a date where that differs (uk `березня`). How a sheet is read is
 * [ReferenceSection].
 *
 * The generated table can say nothing the rows do not carry, so how the language ASSEMBLES
 * a date — and what trips a learner up doing it — is authored prose under it
 * (`catalog/dates/<lang>.json` § dateNotes), the numbers page's own shape.
 */
@Composable
fun DateReferenceSection(model: AppModel, content: DateDrillContent, chrome: Chrome) {
    val groups = remember(content, chrome) {
        DateDrill.reference(content).map { group ->
            ReferenceGroup(
                // One bare-name pool — the Sprosse rows above already name the two.
                caption = chrome.dateSprosse(listOf(group.kind)),
                rows = group.rows.map {
                    ReferenceRow(source = it.source, target = it.target, targetUnder = otherForms(it))
                },
            )
        }
    }
    ReferenceSection(model, chrome.datesReference, groups, content.source, content.target, chrome)

    val notes = remember(content) { model.catalog?.dateNotes(content.target, content.source).orEmpty() }
    if (notes.isNotEmpty()) {
        OverviewHeading(chrome.commonNotes)
        OverviewPanel {
            for (note in notes) {
                Text(note, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** What else the learned name answers to; null keeps the caption line off the row. */
private fun otherForms(row: DateReferenceRow): String? {
    val forms = listOfNotNull(row.abbr) + row.synonyms + listOfNotNull(row.dateForm)
    return if (forms.isEmpty()) null else forms.joinToString(" · ")
}
