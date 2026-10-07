package net.spross.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
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
    cheer: () -> Unit,
) {
    // why: a pause is a round's end the run may go on from, cheered and rained on as the round summary is.
    LaunchedEffect(Unit) { if (reason.celebrated) cheer() }
    Box(Modifier.fillMaxSize()) {
        SummaryScaffold(
            title = title(reason, chrome),
            chrome = chrome,
            onDone = onDone,
            onPractice = onKeepPracticing,
            tally = countLine(chrome.trainerResultTasksDoneOne, chrome.trainerResultTasksDone, run.done),
            milestone = milestone(run, chrome),
            hint = chrome.trainerPauseStrugglingHint.takeIf { reason == DrillPauseReason.Struggling },
        ) { SummaryGlyph(reason.emoji) }
        if (reason.celebrated) Confetti()
    }
}

/** What the stretch reached, only where it reached something: the climb from the Sprosse the run opened on, and a record beaten. */
private fun milestone(run: DrillRunProgress, chrome: Chrome): String? {
    val pacing = run.pacing
    return listOfNotNull(
        pacing.climbed?.let { chrome.trainerPauseSprossen.format(it.from, it.to) },
        chrome.trainerResultNewRecord.takeIf { pacing.newRecord },
    ).joinToString(" · ").ifEmpty { null }
}

private fun title(reason: DrillPauseReason, chrome: Chrome): String = when (reason) {
    DrillPauseReason.Improved -> chrome.trainerPauseTitleImproved
    DrillPauseReason.Struggling -> chrome.trainerPauseTitleStruggling
    DrillPauseReason.Count -> chrome.trainerPauseTitleCount
}
