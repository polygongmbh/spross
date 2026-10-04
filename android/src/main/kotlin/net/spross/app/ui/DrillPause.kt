package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        hint = chrome.trainerPauseStrugglingHint.takeIf { reason == DrillPauseReason.Struggling },
        details = { Figures(run, chrome) },
    ) { SummaryGlyph(glyph(reason)) }
}

/**
 * What the run has done beyond its count: how many landed clean, the best answer streak, the climb —
 * and a note only where something new was reached.
 */
@Composable
private fun Figures(run: DrillRunProgress, chrome: Chrome) {
    val pacing = run.pacing
    val opened = pacing.openedOn
    val reached = pacing.reached
    val climb = when {
        opened == null || reached == null -> null
        reached > opened -> chrome.trainerPauseSprossen.format(opened, reached)
        else -> chrome.trainerSprosse.format(reached)
    }
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp), // card-parity: the tally lines sit tighter than xs
    ) {
        listOfNotNull(
            chrome.trainerPauseTally.format(run.tally.clean, run.tally.judged),
            chrome.trainerResultBestStreak.format(run.bestAnswerStreak),
            climb,
        ).forEach { Line(it, Theme.colors.textSecondary) }
        if (pacing.newSprossen > 0) Line(chrome.trainerPauseNewSprosse, Theme.colors.accent)
        if (pacing.newRecord) Line(chrome.trainerResultNewRecord, Theme.colors.accent)
    }
}

@Composable
private fun Line(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, textAlign = TextAlign.Center)
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
