package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.TypedDrillStanding
import net.spross.kern.model.Language
import net.spross.kern.trainer.TrainerMode

/**
 * What tells one typed drill's page from the other: its own words, its own ladder, what its
 * runs have filed, and the run it opens.
 *
 * The ladder is asked for rather than handed over, because the REVERSE switch that decides
 * it lives on the page: the calendar's height depends on what the pair's content carries
 * and which way round the run asks, where the atlas stands at kern's one constant.
 */
class TypedDrillLadder(
    val title: String,
    /** Every figure this pair's runs have filed — the circles, the record line, Fast's price. */
    val standing: TypedDrillStanding,
    /** What Fast does, in this ladder's own price — a Sprosse here costs THREE clean wins. */
    val fastHint: String,
    /** Which side asks and which side answers, as the switch stands right now. */
    val reverseHint: (reverse: Boolean) -> String,
    /** Kern's own ceiling for this pair and direction, never a count authored beside it. */
    val ceiling: (reverse: Boolean) -> Int,
    val fastOpen: (reverse: Boolean) -> Boolean,
    /** What standing on a Sprosse is called — one line, the whole row. */
    val sprosse: (sprosse: Int, reverse: Boolean) -> String,
    /** Opens a run on [level]. */
    val start: (reverse: Boolean, fast: Boolean, level: Int) -> Unit,
)

/**
 * The page both typed drills are started from — the atlas, and the calendar.
 *
 * The shape every overview uses — the run above, the reading below: the Sprossen a run
 * climbs, how it is played, the button, and then the table itself.
 *
 * The RUNGS are not earned — the drills are ungated, so no row carries a padlock — but the
 * ladder wears its RECORD: each circle says whether some run stood on that Sprosse or
 * answered every question of it, and `Los` opens on the lowest Sprosse no run has answered
 * out ([TrainerMode.entrySprosse]). The rows are the control: tapping one opens a run there
 * instead. Fast is the single row with a price, and kern sets it.
 *
 * The ladder redraws when the reverse switch below it flips — the page shows exactly the
 * ladder the start button opens, and the mask it reads is that direction's own.
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
    val cleared = ladder.standing.cleared(reverse)
    val entry = TrainerMode.entrySprosse(cleared, ceiling)

    val result = model.trainer.result
    // why: a tile inserted ABOVE the content keeps the scroll offset, so what a run came
    // back with would sit off the top of a page the learner is still looking at.
    LaunchedEffect(result) { if (result != null) scroll.animateScrollTo(0) }

    val start = { ladder.start(reverse, fast, entry) }

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
            for (sprosse in 1..ceiling) {
                SprosseRow(
                    sprosse = sprosse,
                    name = ladder.sprosse(sprosse, reverse),
                    mark = sprosseMark(sprosse, cleared, ladder.standing.bestSprosse),
                    entry = sprosse == entry,
                    chrome = chrome,
                    onClick = { ladder.start(reverse, fast, sprosse) },
                )
            }
        }
        // Under the ladder: that the rows are the control, and — once a run has closed —
        // the two counted records. The Sprosse itself is not printed: the circles say
        // where the ladder stands.
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
            OverviewNote(chrome.trainerLadderTap)
            if (ladder.standing.record > 0) {
                OverviewNote(chrome.trainerLadderBest.format(ladder.standing.record, ladder.standing.answers))
            }
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
 * One Sprosse: its number in a circle that wears the record, and its name. The row is the
 * control — it opens a run on that Sprosse.
 */
@Composable
private fun SprosseRow(
    sprosse: Int,
    name: String,
    mark: SprosseMark,
    entry: Boolean,
    chrome: Chrome,
    onClick: () -> Unit,
) {
    val state = when {
        entry -> chrome.trainerSprosseEntry
        mark == SprosseMark.Cleared -> chrome.trainerSprosseCleared
        mark == SprosseMark.Reached -> chrome.trainerSprosseReached
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            // why: one Sprosse is one TalkBack stop — the mark and the name describe a single
            // thing, and the state says what the circle's fill says.
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { state?.let { stateDescription = it } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        SprosseCircle(sprosse, mark)
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
