package net.spross.app.ui

import androidx.compose.runtime.Composable
import net.spross.app.AppModel
import net.spross.app.countrySprosse
import net.spross.app.countrySprosseHint
import net.spross.kern.trainer.CountryDrill

/**
 * The "Länder" entry: the world as the two languages name it, and the place its drill is
 * started from.
 *
 * The page itself is [TypedDrillOverview], shared with the calendar; what is here is the
 * atlas's own words, its own ladder and the table under it.
 *
 * That table is the one surface in the app written in TWO languages at once: a country's
 * name is a pair, not a property of the language being learned, and it is the very join the
 * run grades against ([CountryDrill.reference]) rather than a second table beside it.
 */
@Composable
fun CountriesOverviewScreen(model: AppModel) {
    val chrome = model.chrome
    // The join is the registry: no atlas for this pair, no page — and the chip that opens
    // it gates on the same predicate, so this is a closed door rather than a screen.
    val content = model.atlas ?: return
    val best = model.werkstatt.countriesBest
    TypedDrillOverview(
        model = model,
        ladder = TypedDrillLadder(
            title = chrome.countriesTitle.format(model.languageName(content.target)),
            pace = chrome.countriesPace,
            bestNote = if (best > 0) chrome.countriesBest.format(best) else null,
            fastHint = chrome.countriesFastHint,
            reverseHint = { reverse ->
                reverseHint(model, chrome.countriesReverseHint, content.source, content.target, reverse)
            },
            // The atlas ladder is one fixed height, whichever way round it asks.
            ceiling = { CountryDrill.MAX_LEVEL },
            fastOpen = { CountryDrill.fastUnlocked(best) },
            sprosse = { sprosse, _ ->
                LadderSprosse(chrome.countrySprosse(sprosse), chrome.countrySprosseHint(sprosse))
            },
            start = model::startCountryDrill,
        ),
    ) {
        CountryReferenceSection(model, content, chrome)
    }
}
