package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.countryRung
import net.spross.app.countryRungHint
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.trainer.CountryDrill

/**
 * The "Länder" entry: the world as the two languages name it, and the place its drill is
 * started from.
 *
 * The shape both other overviews use — the run above, the reading below: the nine rungs a
 * run climbs, how it is played, the button, and then the atlas itself.
 *
 * Nothing on this page is earned. The drill is ungated — every run opens at rung 1 and
 * climbs by itself — so the rung rows say what a rung ASKS and never carry a padlock. The
 * one thing that persists is the furthest rung reached, which this page reads and never
 * writes; Fast is the single row with a price, and kern sets it.
 *
 * The one surface in the app written in TWO languages at once: a country's name is a pair,
 * not a property of the language being learned, and the table is the very join the run
 * grades against ([CountryDrill.reference]) rather than a second table beside it.
 */
@Composable
fun CountriesOverviewScreen(model: AppModel) {
    val chrome = model.chrome
    // The join is the registry: no atlas for this pair, no page — and the chip that opens
    // it gates on the same predicate, so this is a closed door rather than a screen.
    val content = model.atlas ?: return
    val scroll = rememberScrollState()
    BackHandler { model.closeOverview() }

    // Not stored: which way round a run asks and how fast it climbs last as long as the
    // screen does. Names rather than booleans is unnecessary here — a Boolean survives a
    // saved instance state as it is.
    var reverse by rememberSaveable { mutableStateOf(false) }
    var fastPicked by rememberSaveable { mutableStateOf(false) }

    val best = model.werkstatt.countriesBest
    val fastOpen = CountryDrill.fastUnlocked(best)
    // why: the numbers page's rule — a ladder that grew under a stored best puts Fast back
    // out of reach, and a switch must never outlive the price that bought it.
    val fast = fastPicked && fastOpen

    val result = model.werkstatt.result
    // why: a tile inserted ABOVE the content keeps the scroll offset, so what a run came
    // back with would sit off the top of a page the learner is still looking at.
    LaunchedEffect(result) { if (result != null) scroll.animateScrollTo(0) }

    val start = { model.startCountryDrill(reverse, fast) }

    OverviewScaffold(
        title = chrome.countriesPage.format(model.languageName(content.target)),
        chrome = chrome,
        scroll = scroll,
        startEnabled = true,
        onClose = { model.closeOverview() },
        onStart = start,
    ) {
        result?.let { DrillResultTile(it, model.werkstatt.resultTitle, chrome) }

        OverviewHeading(chrome.overviewPractice)
        OverviewPanel {
            // Kern's ceiling, never a count written down beside it.
            for (rung in 1..CountryDrill.MAX_LEVEL) RungRow(rung, chrome)
        }
        // How the ladder is walked, said once instead of marked on every row — and, where a
        // run has climbed before, how far it came.
        Column(verticalArrangement = Arrangement.spacedBy(DlSpace.xs)) {
            OverviewNote(chrome.countriesPace)
            // why: clamped to the ladder's own ceiling — a best booked under a longer
            // ladder must never print a rung that is not on the page.
            if (best > 0) {
                OverviewNote(chrome.countriesBest.format(minOf(best, CountryDrill.MAX_LEVEL)))
            }
        }
        OverviewPanel {
            ModifierSwitchRow(
                title = chrome.modifierReverse,
                caption = reverseHint(model, chrome, content, reverse),
                open = true,
                on = reverse,
                onChange = { reverse = it },
            )
            ModifierSwitchRow(
                title = chrome.modifierFast,
                // why: the atlas rung costs THREE clean wins, so the shared "statt zwei"
                // hint would misprice it — this ladder says its own. Its price is kern's
                // ceiling rather than a rung number authored beside it.
                caption = if (fastOpen) {
                    chrome.countriesFastHint
                } else {
                    "${chrome.unlockPrefix} ${chrome.level.format(CountryDrill.MAX_LEVEL)}"
                },
                open = fastOpen,
                on = fast,
                onChange = { fastPicked = it },
            )
        }
        OverviewStartButton(chrome, true, start)

        CountryReferenceSection(model, content, chrome)
    }
}

/**
 * One rung: the pool it opens and the question it adds, each bringing exactly ONE new
 * thing. The mark is the rung's NUMBER, the letters page's rule — these rows are a ladder
 * the run walks by itself, and a circle beside each one reads as a choice that never
 * answers the tap.
 */
@Composable
private fun RungRow(rung: Int, chrome: Chrome) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // why: one rung is one TalkBack stop — the mark, the name and the line under it
            // describe a single thing.
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        Text(
            "$rung.",
            style = MaterialTheme.typography.titleMedium,
            color = Dl.colors.textSecondary,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(chrome.countryRung(rung), style = MaterialTheme.typography.titleMedium)
            Text(
                chrome.countryRungHint(rung),
                style = MaterialTheme.typography.bodySmall,
                color = Dl.colors.textSecondary,
            )
        }
    }
}

/** Which side asks and which side answers, as the switch stands right now. */
private fun reverseHint(
    model: AppModel,
    chrome: Chrome,
    content: CountryDrillContent,
    reverse: Boolean,
): String {
    val asked = if (reverse) content.target else content.source
    val owed = if (reverse) content.source else content.target
    return chrome.countriesReverseHint.format(model.languageName(asked), model.languageName(owed))
}
