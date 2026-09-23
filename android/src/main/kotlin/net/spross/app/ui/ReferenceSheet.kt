package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.speakFormOnTap
import net.spross.kern.model.Language

/**
 * The reading half of a drill page: the table the run grades against, written down — every
 * entry the pair joins, both sides beside each other, in captioned blocks.
 *
 * Reading matter, so the CONTENT is the control: a whole row says its learned-side name, and
 * the page discloses that gesture once under its heading instead of growing a speaker on
 * every line.
 *
 * Which table, how its blocks are captioned and what each row carries under a name is the
 * page's — the atlas, the calendar — and the shape they are read in is this.
 */

/** One captioned block of a sheet: the rows standing at one tier, or one pool of names. */
class ReferenceGroup(val caption: String, val rows: List<ReferenceRow>)

/** One entry of a sheet, both sides beside each other. */
class ReferenceRow(
    val source: String,
    val target: String,
    /** The picture leading the row; null where the table has none to show. */
    val emoji: String? = null,
    /** What stands under the known name — null where the row has nothing to add there. */
    val sourceUnder: String? = null,
    /** What stands under the learned name — the forms it also answers to. */
    val targetUnder: String? = null,
)

@Composable
fun ReferenceSection(
    model: AppModel,
    heading: String,
    groups: List<ReferenceGroup>,
    source: Language,
    target: Language,
    chrome: Chrome,
) {
    // Heading and hint stand in the page's own rhythm rather than a Column of their own,
    // so the line sits the same distance under its heading as it does on the numbers page.
    OverviewHeading(heading)
    // The hint is the affordance: where nothing on the page can be heard it would
    // promise a gesture that does nothing.
    val audible = groups.any { group ->
        group.rows.any { model.speakFormOnTap(it.target, target) != null }
    }
    if (audible) TapToHearHint(chrome)
    for (group in groups) {
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
            Text(
                group.caption.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = Theme.colors.textSecondary,
                modifier = Modifier.semantics { heading() },
            )
            OverviewPanel {
                for (row in group.rows) SheetRow(row, model, source, target, chrome)
            }
        }
    }
}

/**
 * One entry, twice: the known language on the left, the learned one on the right, each with
 * whatever the table adds under the name.
 *
 * The whole row says the LEARNED side. The other column is the reader's own language, and a
 * reference sheet is read to hear what one cannot yet say.
 */
@Composable
private fun SheetRow(
    row: ReferenceRow,
    model: AppModel,
    source: Language,
    target: Language,
    chrome: Chrome,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // why: one entry is one TalkBack stop — both names and whatever stands under
            // them are the same row of the table.
            .semantics(mergeDescendants = true) { }
            .pronounceOnTap(
                model.speakFormOnTap(row.target, target),
                chrome,
                minHeight = 0.dp,
            ),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        row.emoji?.let { Text(it, fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { }) } // card-parity: a picture, not a type role
        SheetSide(
            name = row.source,
            under = row.sourceUnder,
            language = source,
            tint = Theme.colors.textPrimary,
            align = TextAlign.Start,
            alignment = Alignment.Start,
            modifier = Modifier.weight(1f),
        )
        SheetSide(
            name = row.target,
            under = row.targetUnder,
            language = target,
            tint = Theme.colors.accent,
            align = TextAlign.End,
            alignment = Alignment.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** [language] tags the column, so TalkBack reads each side in its own voice. */
@Composable
private fun SheetSide(
    name: String,
    under: String?,
    language: Language,
    tint: Color,
    align: TextAlign,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp), // card-parity: the line under a name sits tighter than xs
        horizontalAlignment = alignment,
    ) {
        Text(
            localizedTarget(name, language),
            style = MaterialTheme.typography.titleMedium,
            color = tint,
            textAlign = align,
        )
        under?.let {
            Text(
                localizedTarget(it, language),
                style = MaterialTheme.typography.bodySmall,
                color = Theme.colors.textSecondary,
                textAlign = align,
            )
        }
    }
}

/**
 * What a sheet's rows cannot say: how the language assembles what it lists and what trips a
 * learner up doing it, written in the known language. Nothing at all where the pair carries none.
 */
@Composable
fun ReferenceNotes(lines: List<String>, chrome: Chrome) {
    if (lines.isEmpty()) return
    OverviewHeading(chrome.commonNotes)
    OverviewPanel {
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
            for (line in lines) {
                Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
                    Text(
                        "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Theme.colors.textSecondary,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
