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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.name
import net.spross.app.startLetterDrill
import net.spross.kern.trainer.DrillUnlockMark
import net.spross.kern.trainer.LetterDrillAvailability
import net.spross.kern.trainer.LetterDrillRunState
import net.spross.kern.trainer.LetterFormat
import net.spross.kern.trainer.SprosseMark

/**
 * The Letters entry: the alphabet of the language being learned, and the place its
 * drill is started from.
 *
 * The same shape as the numbers page — the drill's formats and start first, the alphabet
 * table under them. What the format rows say, and why the page still stands where this
 * device can sound nothing: `docs/drills-words.md`.
 */
@Composable
fun LettersOverviewScreen(model: AppModel) {
    val chrome = model.chrome
    val language = model.box?.joinStamp?.target ?: return
    val report = model.trainer.letters
    val available = report?.drillAvailable == true
    // Read on every composition: the page composes afresh as a run's screen comes down.
    val cleared = model.trainer.store.cleared(LetterDrillRunState.storageKey(language))
    // An unlock is marked once. Only a priced padlock counts: where the drill cannot run at
    // all, every format is shut for a reason that is not the learner's to earn.
    val priced = if (available) LetterFormat.entries else emptyList()
    val reach = priced.associateWith { formatOpen(it, report) }
    val locked = priced.filter { reach[it] == false }.map { DrillUnlockMark.row(it) }.toSet()
    val unlocking = remember(language, locked) {
        model.trainer.store.unlockMarks(
            LetterDrillRunState.storageKey(language),
            locked,
            priced.filter { reach[it] == true }.map { DrillUnlockMark.row(it) }.toSet(),
        )
    }
    AnnounceUnlocks(priced.filter { DrillUnlockMark.row(it) in unlocking }.map { chrome.name(it) }, chrome)

    OverviewScaffold(
        model = model,
        title = chrome.lettersTitle.format(model.languageName(language)),
        startEnabled = available,
        onStart = { model.startLetterDrill() },
    ) {
        Panel {
            LetterFormat.entries.forEachIndexed { index, format ->
                FormatRow(format, index + 1, report, cleared, DrillUnlockMark.row(format) in unlocking, chrome)
            }
        }
        OverviewStartButton(chrome, available) { model.startLetterDrill() }
        if (!available) OverviewNote(chrome.lettersUnavailable)

        AlphabetSection(model, language, chrome)
    }
}

/**
 * One format: what it asks, and whether this run will get there. The format the run OPENS on
 * wears the filled circle — every learner starts somewhere different, and the page should
 * not make them guess where — and a format some run climbed off clean is forest. The rows are
 * not tapped: the run walks the ladder by itself from the format it opens on.
 */
@Composable
private fun FormatRow(
    format: LetterFormat,
    step: Int,
    report: LetterDrillAvailability.Report?,
    cleared: Set<Int>,
    unlocking: Boolean,
    chrome: Chrome,
) {
    val ready = report?.takeIf { it.drillAvailable }
    val open = formatOpen(format, report)
    val entry = open && ready?.openingFormat(cleared) == format
    // Climbed off clean beats where the run opens; the rest are outlines.
    val mark = SprosseMark.of(cleared = ready?.formatCleared(format, cleared) == true, reached = entry)
    val caption = if (!open && ready != null) chrome.lettersStageDictationLocked else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .unlockWash(unlocking)
            // why: one format is one TalkBack stop — the mark and the name describe a single
            // thing, and the state says what the filled circle says.
            .semantics(mergeDescendants = true) {
                when {
                    entry -> stateDescription = chrome.a11yTrainerSprosseEntry
                    mark == SprosseMark.Cleared -> stateDescription = chrome.a11yTrainerSprosseCleared
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        if (open) {
            UnlockingMark(unlocking) { SprosseCircle(step, mark) }
        } else {
            Text(
                LOCK,
                style = MaterialTheme.typography.titleMedium,
                color = Theme.colors.textSecondary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { // card-parity: the title/caption pair sits tighter than xs
            Text(
                chrome.name(format),
                style = MaterialTheme.typography.titleMedium,
                color = if (open) Theme.colors.textPrimary else Theme.colors.textSecondary,
            )
            caption?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Theme.colors.textSecondary)
            }
        }
    }
}

/**
 * Dictation needs a pool of playable words the learner already holds; below that floor the
 * ramp stops one Sprosse short of it, so the row is a padlock with its price. Where the drill
 * cannot run at all, every format is out of reach for the one reason the line under the button
 * already gives.
 */
private fun formatOpen(format: LetterFormat, report: LetterDrillAvailability.Report?): Boolean {
    val ready = report?.takeIf { it.drillAvailable } ?: return false
    return format != LetterFormat.Dictation || ready.dictationAvailable
}
