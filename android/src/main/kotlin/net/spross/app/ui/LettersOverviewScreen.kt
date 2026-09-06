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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.name
import net.spross.kern.trainer.LetterDrillAvailability
import net.spross.kern.trainer.LetterStage

/**
 * The Letters entry: the alphabet of the language being learnt, and the place its
 * drill is started from.
 *
 * The same shape as the numbers page — the drill's stages and start first, the alphabet
 * table under them. What the stage rows say, and why the page still stands where this
 * device can sound nothing: `docs/drills.md`.
 */
@Composable
fun LettersOverviewScreen(model: AppModel) {
    val chrome = model.chrome
    val language = model.box?.joinStamp?.target ?: return
    val report = model.trainer.letters
    val scroll = rememberScrollState()
    BackHandler { model.closeOverview() }

    val result = model.trainer.result
    // why: the numbers page's rule — a tile inserted above the content keeps the offset,
    // so the page comes up to meet it.
    LaunchedEffect(result) { if (result != null) scroll.animateScrollTo(0) }

    val available = report?.drillAvailable == true

    OverviewScaffold(
        title = chrome.lettersTitle.format(model.languageName(language)),
        chrome = chrome,
        scroll = scroll,
        startEnabled = available,
        onClose = { model.closeOverview() },
        onStart = { model.startLetterDrill() },
    ) {
        result?.let { DrillResultTile(it, model.trainer.resultTitle, chrome) }

        OverviewHeading(chrome.trainerOverviewPractice)
        OverviewPanel {
            LetterStage.entries.forEachIndexed { index, stage ->
                StageRow(stage, index + 1, report, chrome)
            }
        }
        OverviewStartButton(chrome, available) { model.startLetterDrill() }
        if (!available) OverviewNote(chrome.lettersUnavailable)

        AlphabetSection(model, language, chrome)
    }
}

/**
 * One stage: what it asks, and whether this run will get there. The stage the run OPENS on
 * wears the filled circle — every learner starts somewhere different, and the page should
 * not make them guess where. The rows are not tapped: the run walks the ladder by itself
 * from that stage, so the circle carries no record either.
 */
@Composable
private fun StageRow(
    stage: LetterStage,
    step: Int,
    report: LetterDrillAvailability.Report?,
    chrome: Chrome,
) {
    val ready = report?.takeIf { it.drillAvailable }
    // Dictation needs a pool of playable words the learner already holds; below that floor
    // the ramp stops one Sprosse short of it, so the row is a padlock with its price. Where
    // the drill cannot run at all, every stage is out of reach for the one reason the line
    // under the button already gives.
    val open = ready != null && (stage != LetterStage.Dictation || ready.dictationAvailable)
    val entry = open && ready.entryStage == stage
    val caption = if (!open && ready != null) chrome.lettersStageDictationLocked else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // why: one stage is one TalkBack stop — the mark and the name describe a single
            // thing, and the state says what the filled circle says.
            .semantics(mergeDescendants = true) {
                if (entry) stateDescription = chrome.trainerSprosseEntry
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        if (open) {
            SprosseCircle(step, if (entry) SprosseMark.Reached else SprosseMark.Untouched)
        } else {
            Text(
                LOCK,
                style = MaterialTheme.typography.titleMedium,
                color = Theme.colors.textSecondary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                chrome.name(stage),
                style = MaterialTheme.typography.titleMedium,
                color = if (open) Theme.colors.textPrimary else Theme.colors.textSecondary,
            )
            caption?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Theme.colors.textSecondary)
            }
        }
    }
}
