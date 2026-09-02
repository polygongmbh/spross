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
import net.spross.kern.model.Language

/** One Sprosse of a ladder, in words: what standing on it is called, and what it asks. */
data class LadderSprosse(val name: String, val hint: String)

/**
 * What tells one typed drill's page from the other: its own words, its own ladder, and the
 * run it opens.
 *
 * The ladder is asked for rather than handed over, because the REVERSE switch that decides
 * it lives on the page: the calendar's height depends on what the pair's content carries
 * and which way round the run asks, where the atlas stands at kern's one constant.
 */
class TypedDrillLadder(
    val title: String,
    /** How the ladder is walked, said once instead of marked on every row. */
    val pace: String,
    /** How far a run ever climbed, or null where none has. */
    val bestNote: String?,
    /** What Fast does, in this ladder's own price — a Sprosse here costs THREE clean wins. */
    val fastHint: String,
    /** Which side asks and which side answers, as the switch stands right now. */
    val reverseHint: (reverse: Boolean) -> String,
    /** Kern's own ceiling for this pair and direction, never a count authored beside it. */
    val ceiling: (reverse: Boolean) -> Int,
    val fastOpen: (reverse: Boolean) -> Boolean,
    val sprosse: (sprosse: Int, reverse: Boolean) -> LadderSprosse,
    val start: (reverse: Boolean, fast: Boolean) -> Unit,
)

/**
 * The page both typed drills are started from — the atlas, and the calendar.
 *
 * The shape every overview uses — the run above, the reading below: the Sprossen a run
 * climbs, how it is played, the button, and then the table itself.
 *
 * Nothing on either page is earned. The drills are ungated — every run opens at Sprosse 1 and
 * climbs by itself — so the Sprosse rows say what a Sprosse ASKS and never carry a padlock. The
 * one thing that persists is the furthest Sprosse reached, which this page reads and never
 * writes; Fast is the single row with a price, and kern sets it.
 *
 * The ladder redraws when the reverse switch below it flips — the page shows exactly the
 * ladder the start button opens.
 */
@Composable
fun TypedDrillOverview(
    model: AppModel,
    ladder: TypedDrillLadder,
    reference: @Composable () -> Unit,
) {
    val chrome = model.chrome
    val scroll = rememberScrollState()
    BackHandler { model.closeOverview() }

    // Not stored: which way round a run asks and how fast it climbs last as long as the
    // screen does. Names rather than booleans is unnecessary here — a Boolean survives a
    // saved instance state as it is.
    var reverse by rememberSaveable { mutableStateOf(false) }
    var fastPicked by rememberSaveable { mutableStateOf(false) }

    val ceiling = ladder.ceiling(reverse)
    val fastOpen = ladder.fastOpen(reverse)
    // why: the numbers page's rule — a ladder that grew under a stored best puts Fast back
    // out of reach, and a switch must never outlive the price that bought it.
    val fast = fastPicked && fastOpen

    val result = model.trainer.result
    // why: a tile inserted ABOVE the content keeps the scroll offset, so what a run came
    // back with would sit off the top of a page the learner is still looking at.
    LaunchedEffect(result) { if (result != null) scroll.animateScrollTo(0) }

    val start = { ladder.start(reverse, fast) }

    OverviewScaffold(
        title = ladder.title,
        chrome = chrome,
        scroll = scroll,
        startEnabled = true,
        onClose = { model.closeOverview() },
        onStart = start,
    ) {
        result?.let { DrillResultTile(it, model.trainer.resultTitle, chrome) }

        OverviewHeading(chrome.trainerOverviewPractice)
        OverviewPanel {
            for (sprosse in 1..ceiling) SprosseRow(sprosse, ladder.sprosse(sprosse, reverse))
        }
        // How the ladder is walked, said once instead of marked on every row — and, where a
        // run has climbed before, how far it came.
        Column(verticalArrangement = Arrangement.spacedBy(DlSpace.xs)) {
            OverviewNote(ladder.pace)
            // why: printed as it stands, ceiling and all — the Sprosse keeps counting past the
            // named ladder, so the record is a number to beat rather than a row on the page.
            ladder.bestNote?.let { OverviewNote(it) }
        }
        OverviewPanel {
            ModifierSwitchRow(
                title = chrome.trainerModifierReverse,
                caption = ladder.reverseHint(reverse),
                open = true,
                on = reverse,
                onChange = { reverse = it },
            )
            ModifierSwitchRow(
                title = chrome.trainerModifierFast,
                // why: a Sprosse here costs THREE clean wins, so the shared "statt zwei" hint
                // would misprice it — each ladder says its own. Its price is kern's ceiling
                // rather than a Sprosse number authored beside it.
                caption = if (fastOpen) {
                    ladder.fastHint
                } else {
                    "${chrome.numbersUnlock} ${chrome.trainerSprosse.format(ceiling)}"
                },
                open = fastOpen,
                on = fast,
                onChange = { fastPicked = it },
            )
        }
        OverviewStartButton(chrome, true, start)

        reference()
    }
}

/**
 * One Sprosse: what standing on it is asked. The mark is the Sprosse's NUMBER, the letters
 * page's rule — these rows are a ladder the run walks by itself, and a circle beside each
 * one reads as a choice that never answers the tap.
 */
@Composable
private fun SprosseRow(sprosse: Int, words: LadderSprosse) {
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
            Text(words.name, style = MaterialTheme.typography.titleMedium)
            Text(
                words.hint,
                style = MaterialTheme.typography.bodySmall,
                color = Dl.colors.textSecondary,
            )
        }
    }
}

/**
 * Which side asks and which side answers, as the switch stands right now — the one line
 * each page fills with its own wording and its own pair.
 */
internal fun reverseHint(
    model: AppModel,
    line: String,
    source: Language,
    target: Language,
    reverse: Boolean,
): String {
    val asked = if (reverse) target else source
    val owed = if (reverse) source else target
    return line.format(model.languageName(asked), model.languageName(owed))
}
