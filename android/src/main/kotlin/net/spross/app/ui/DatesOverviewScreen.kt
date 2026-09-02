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
import net.spross.app.dateSprosse
import net.spross.app.dateSprosseHint
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.trainer.DateDrill
import net.spross.kern.trainer.DateTaskKind

/**
 * The "Datum" entry: the calendar as the two languages name it, and the place its drill is
 * started from.
 *
 * The shape every overview uses — the run above, the reading below: the Sprossen a run
 * climbs, how it is played, the button, and then the calendar itself.
 *
 * Nothing on this page is earned — the atlas page's rule throughout: no padlocks on the
 * Sprossen, the furthest Sprosse read and never written, Fast the single row with a price, and
 * kern sets it.
 *
 * Unlike its siblings the ladder here is not one fixed list: how tall it is depends on
 * what the pair's content carries (no `dateWithYear` pattern, no year Sprosse) and which way
 * round the run asks (reversed, only the name Sprossen stand). The rows are therefore drawn
 * from kern's own [DateDrill.kinds] per Sprosse, and the panel redraws when the reverse
 * switch below it flips — the page shows exactly the ladder the start button opens.
 */
@Composable
fun DatesOverviewScreen(model: AppModel) {
    val chrome = model.chrome
    // The join is the registry: no calendars for this pair, no page — and the chip that
    // opens it gates on the same predicate, so this is a closed door rather than a screen.
    val content = model.dates ?: return
    val scroll = rememberScrollState()
    BackHandler { model.closeOverview() }

    var reverse by rememberSaveable { mutableStateOf(false) }
    var fastPicked by rememberSaveable { mutableStateOf(false) }

    val best = model.werkstatt.datesBest
    val ceiling = DateDrill.maxLevel(content, reverse)
    val fastOpen = DateDrill.fastUnlocked(best, content, reverse)
    // why: the numbers page's rule — a ladder that grew under a stored best puts Fast back
    // out of reach, and a switch must never outlive the price that bought it.
    val fast = fastPicked && fastOpen

    val result = model.werkstatt.result
    // why: a tile inserted ABOVE the content keeps the scroll offset, so what a run came
    // back with would sit off the top of a page the learner is still looking at.
    LaunchedEffect(result) { if (result != null) scroll.animateScrollTo(0) }

    val start = { model.startDateDrill(reverse, fast) }

    OverviewScaffold(
        title = chrome.datesTitle.format(model.languageName(content.target)),
        chrome = chrome,
        scroll = scroll,
        startEnabled = true,
        onClose = { model.closeOverview() },
        onStart = start,
    ) {
        result?.let { DrillResultTile(it, model.werkstatt.resultTitle, chrome) }

        OverviewHeading(chrome.trainerOverviewPractice)
        OverviewPanel {
            // Kern's own ceiling for THIS pair and direction, never a count beside it.
            for (sprosse in 1..ceiling) {
                DateSprosseRow(sprosse, DateDrill.kinds(content, sprosse, reverse), chrome)
            }
        }
        // How the ladder is walked, said once instead of marked on every row — and, where a
        // run has climbed before, how far it came.
        Column(verticalArrangement = Arrangement.spacedBy(DlSpace.xs)) {
            OverviewNote(chrome.datesPace)
            // why: printed as it stands, ceiling and all — the Sprosse keeps counting past the
            // named ladder, so the record is a number to beat rather than a row on the page.
            if (best > 0) OverviewNote(chrome.datesBest.format(best))
        }
        OverviewPanel {
            ModifierSwitchRow(
                title = chrome.trainerModifierReverse,
                caption = reverseHint(model, chrome, content, reverse),
                open = true,
                on = reverse,
                onChange = { reverse = it },
            )
            ModifierSwitchRow(
                title = chrome.trainerModifierFast,
                // why: the dates Sprosse costs THREE clean wins, so the shared "statt zwei"
                // hint would misprice it — this ladder says its own. Its price is kern's
                // ceiling rather than a Sprosse number authored beside it.
                caption = if (fastOpen) {
                    chrome.datesFastHint
                } else {
                    "${chrome.numbersUnlock} ${chrome.trainerSprosse.format(ceiling)}"
                },
                open = fastOpen,
                on = fast,
                onChange = { fastPicked = it },
            )
        }
        OverviewStartButton(chrome, true, start)

        DateReferenceSection(model, content, chrome)
    }
}

/**
 * One Sprosse: what standing on it is asked. The mark is the Sprosse's NUMBER, the letters
 * page's rule — these rows are a ladder the run walks by itself, and a circle beside each
 * one reads as a choice that never answers the tap.
 */
@Composable
private fun DateSprosseRow(sprosse: Int, kinds: List<DateTaskKind>, chrome: Chrome) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // why: one Sprosse is one TalkBack stop — the mark, the name and the line under it
            // describe a single thing.
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        Text(
            "$sprosse.",
            style = MaterialTheme.typography.titleMedium,
            color = Dl.colors.textSecondary,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(chrome.dateSprosse(kinds), style = MaterialTheme.typography.titleMedium)
            Text(
                chrome.dateSprosseHint(kinds),
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
    content: DateDrillContent,
    reverse: Boolean,
): String {
    val asked = if (reverse) content.target else content.source
    val owed = if (reverse) content.source else content.target
    return chrome.datesReverseHint.format(model.languageName(asked), model.languageName(owed))
}
