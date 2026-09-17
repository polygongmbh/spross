package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.countryTier
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.trainer.CountryDrill

/**
 * The reading half of the atlas page: every country the pair joins, grouped by the tier a
 * row enters the ladder at, with the people and the language(s) under each name — the triple
 * the drill asks about, written down in one place.
 *
 * The table is [CountryDrill.reference] — the same joined rows the run grades against, so it
 * cannot claim one name and ask for another. How a sheet is read is [ReferenceSection].
 */
@Composable
fun CountryReferenceSection(model: AppModel, content: CountryDrillContent, chrome: Chrome) {
    val groups = remember(content, chrome) {
        CountryDrill.reference(content).map { group ->
            ReferenceGroup(
                caption = chrome.countryTier(group.tier),
                rows = group.rows.map { row ->
                    ReferenceRow(
                        source = row.source,
                        target = row.target,
                        emoji = row.flag,
                        sourceUnder = (listOf(row.sourceNationality) + row.sourceLanguages)
                            .joinToString(" · "),
                        targetUnder = (listOf(row.targetNationality) + row.targetLanguages)
                            .joinToString(" · "),
                    )
                },
            )
        }
    }
    ReferenceSection(
        model, chrome.countriesReference, groups, content.source, content.target, chrome,
    )
}
