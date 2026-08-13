package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import net.spross.app.countryTier
import net.spross.app.speakFormOnTap
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.model.Language
import net.spross.kern.trainer.CountryDrill
import net.spross.kern.trainer.CountryReferenceGroup
import net.spross.kern.trainer.CountryReferenceRow

/**
 * The reading half of the atlas page: every country the pair joins, both sides beside each
 * other, grouped by the tier a row enters the ladder at.
 *
 * The table is [CountryDrill.reference] — the same joined rows the run grades against,
 * so it cannot claim one name and ask for another.
 *
 * Reading matter, so the CONTENT is the control: a whole row says its learned-side name,
 * and the page discloses that gesture once under its heading instead of growing a speaker
 * on every line.
 */
@Composable
fun CountryReferenceSection(model: AppModel, content: CountryDrillContent, chrome: Chrome) {
    val groups = remember(content) { CountryDrill.reference(content) }
    // Heading and hint stand in the page's own rhythm rather than a Column of their own,
    // so the line sits the same distance under its heading as it does on the numbers page.
    OverviewHeading(chrome.countriesReference)
    // The hint is the affordance: where nothing on the page can be heard it would
    // promise a gesture that does nothing.
    val audible = groups.any { group ->
        group.rows.any { model.speakFormOnTap(it.target, content.target) != null }
    }
    if (audible) TapToHearHint(chrome)
    for (group in groups) TierGroup(group, model, content, chrome)
}

/** How far from home a group sits, and the countries standing there. */
@Composable
private fun TierGroup(
    group: CountryReferenceGroup,
    model: AppModel,
    content: CountryDrillContent,
    chrome: Chrome,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.s)) {
        Text(
            chrome.countryTier(group.tier).uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = Dl.colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        OverviewPanel {
            for (row in group.rows) CountryRow(row, model, content, chrome)
        }
    }
}

/**
 * One country, twice: the known language on the left, the learned one on the right, each
 * with the people and the language(s) under the name — the triple the drill asks about,
 * written down in one place.
 *
 * The whole row says the LEARNED side. The other column is the reader's own language,
 * and a reference sheet is read to hear what one cannot yet say.
 */
@Composable
private fun CountryRow(
    row: CountryReferenceRow,
    model: AppModel,
    content: CountryDrillContent,
    chrome: Chrome,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // why: one country is one TalkBack stop — both names, the people and the
            // languages are the same row of the table.
            .semantics(mergeDescendants = true) { }
            .pronounceOnTap(
                model.speakFormOnTap(row.target, content.target),
                chrome,
                minHeight = 0.dp,
            ),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        Text(row.flag, fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { })
        CountrySide(
            name = row.source,
            under = row.sourceNationality,
            languages = row.sourceLanguages,
            language = content.source,
            tint = Dl.colors.textPrimary,
            align = TextAlign.Start,
            alignment = Alignment.Start,
            modifier = Modifier.weight(1f),
        )
        CountrySide(
            name = row.target,
            under = row.targetNationality,
            languages = row.targetLanguages,
            language = content.target,
            tint = Dl.colors.accent,
            align = TextAlign.End,
            alignment = Alignment.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** [language] tags the column, so TalkBack reads each side in its own voice. */
@Composable
private fun CountrySide(
    name: String,
    under: String,
    languages: List<String>,
    language: Language,
    tint: Color,
    align: TextAlign,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = alignment,
    ) {
        Text(
            localizedTarget(name, language),
            style = MaterialTheme.typography.titleMedium,
            color = tint,
            textAlign = align,
        )
        Text(
            localizedTarget((listOf(under) + languages).joinToString(" · "), language),
            style = MaterialTheme.typography.bodySmall,
            color = Dl.colors.textSecondary,
            textAlign = align,
        )
    }
}
