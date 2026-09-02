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
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.hint
import net.spross.app.name
import net.spross.kern.trainer.LetterDrillAvailability
import net.spross.kern.trainer.LetterStage

/**
 * The "Buchstaben" entry: the alphabet of the language being learnt, and the place its
 * drill is started from.
 *
 * The same shape as the numbers page — the drill's stages and start first, the alphabet
 * table under them. Where the drill cannot run at all (this device can sound nothing in the
 * language) the stages are out of reach and the page is the alphabet alone: audio is the
 * DRILL's precondition, not the table's.
 *
 * Nothing here is graded and nothing is stored: the letter drill books no review and keeps
 * no record, so this page has no ladder to read. The stage rows say which stage a run OPENS
 * on, derived from the learner's consolidated words.
 */
@Composable
fun LettersOverviewScreen(model: AppModel) {
    val chrome = model.chrome
    val language = model.box?.joinStamp?.target ?: return
    val report = model.werkstatt.letters
    val scroll = rememberScrollState()
    BackHandler { model.closeOverview() }

    val result = model.werkstatt.result
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
        result?.let { DrillResultTile(it, model.werkstatt.resultTitle, chrome) }

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
 * is marked and says so — every learner starts somewhere different, and the page should not
 * make them guess where.
 *
 * The mark is the stage's NUMBER, not a circle: these rows are a ladder the run walks by
 * itself, and an empty circle beside each one reads as a choice that never answers the tap.
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
    // the ramp stops one Sprosse short of it, so the row is a padlock with its price.
    val open = ready != null && (stage != LetterStage.Dictation || ready.dictationAvailable)
    val entry = open && ready.entryStage == stage
    val caption = when {
        // Where the drill cannot run at all, every stage is out of reach for the one
        // reason the line under the button already gives.
        !open -> if (ready == null) chrome.hint(stage) else chrome.lettersStageDictationLocked
        entry -> "${chrome.hint(stage)} · ${chrome.lettersStageEntry}"
        else -> chrome.hint(stage)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // why: one stage is one TalkBack stop — the mark, the name and the line under
            // it describe a single thing.
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        Text(
            if (open) "$step." else LOCK,
            style = MaterialTheme.typography.titleMedium,
            color = if (entry) Dl.colors.accent else Dl.colors.textSecondary,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                chrome.name(stage),
                style = MaterialTheme.typography.titleMedium,
                color = if (open) Dl.colors.textPrimary else Dl.colors.textSecondary,
            )
            Text(caption, style = MaterialTheme.typography.bodySmall, color = Dl.colors.textSecondary)
        }
    }
}
