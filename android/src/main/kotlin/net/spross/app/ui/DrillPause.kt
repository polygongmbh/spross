package net.spross.app.ui

import androidx.compose.runtime.Composable
import net.spross.app.Chrome
import net.spross.app.countLine
import net.spross.kern.trainer.DrillPauseReason
import net.spross.kern.trainer.DrillRunProgress

/**
 * Where an endless drill run stops to ask whether to go on — kern's
 * [net.spross.kern.trainer.DrillPacing], which says when and why; this only draws it.
 * It stands in place of the question inside the run's own screen, on the session summary's
 * own scaffold: Done closes the run as the ✕ does, keep practicing carries the same run on.
 */
@Composable
fun DrillPause(
    run: DrillRunProgress,
    reason: DrillPauseReason,
    chrome: Chrome,
    onDone: () -> Unit,
    onKeepPracticing: () -> Unit,
) {
    SummaryScaffold(
        title = title(reason, chrome),
        chrome = chrome,
        onDone = onDone,
        onPractice = onKeepPracticing,
        tally = countLine(chrome.trainerResultTasksDoneOne, chrome.trainerResultTasksDone, run.done),
        milestone = milestone(run, chrome),
        hint = chrome.trainerPauseStrugglingHint.takeIf { reason == DrillPauseReason.Struggling },
    ) { SummaryGlyph(glyph(reason)) }
}

/** What the stretch reached, only where it reached something: the climb from the Sprosse the run opened on, and a record beaten. */
private fun milestone(run: DrillRunProgress, chrome: Chrome): String? {
    val pacing = run.pacing
    val opened = pacing.openedOn
    val reached = pacing.reached
    return listOfNotNull(
        chrome.trainerPauseSprossen.format(opened, reached).takeIf { opened != null && reached != null && reached > opened },
        chrome.trainerResultNewRecord.takeIf { pacing.newRecord },
    ).joinToString(" · ").ifEmpty { null }
}

private fun title(reason: DrillPauseReason, chrome: Chrome): String = when (reason) {
    DrillPauseReason.Improved -> chrome.trainerPauseTitleImproved
    DrillPauseReason.Struggling -> chrome.trainerPauseTitleStruggling
    DrillPauseReason.Count -> chrome.trainerPauseTitleCount
}

private fun glyph(reason: DrillPauseReason): String = when (reason) {
    DrillPauseReason.Improved -> "🎉"
    DrillPauseReason.Struggling -> "☕️"
    DrillPauseReason.Count -> "💪"
}
