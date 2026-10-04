package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.Chrome
import net.spross.app.countLine
import net.spross.kern.trainer.DrillPauseReason
import net.spross.kern.trainer.DrillRunProgress

/**
 * Where an endless drill run stops to ask whether to go on — kern's
 * [net.spross.kern.trainer.DrillPacing], which says when and why; this only draws it.
 * It stands in place of the question inside the run's own screen, and exits through the pair
 * the session summary wears: Done closes the run as the ✕ does, keep practicing carries the
 * same run on.
 */
@Composable
fun ColumnScope.DrillPause(
    run: DrillRunProgress,
    reason: DrillPauseReason,
    chrome: Chrome,
    onDone: () -> Unit,
    onKeepPracticing: () -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph(reason), fontSize = 72.sp) // card-parity: the pause's own glyph, not a card prompt
        Spacer(Modifier.height(16.dp))
        Text(title(reason, chrome), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Figures(run, chrome)
        if (reason == DrillPauseReason.Struggling) {
            Spacer(Modifier.height(16.dp))
            Text(
                chrome.trainerPauseStrugglingHint,
                style = MaterialTheme.typography.bodyMedium,
                color = Theme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    SessionExitButtons(chrome, onDone = onDone, onTalk = null, onPractice = onKeepPracticing)
}

/**
 * What the run has done so far: the answers, how many landed clean, the best answer streak, the
 * climb — and a note only where something new was reached.
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
        Text(
            countLine(chrome.trainerResultTasksDoneOne, chrome.trainerResultTasksDone, run.done),
            style = MaterialTheme.typography.titleMedium,
        )
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
