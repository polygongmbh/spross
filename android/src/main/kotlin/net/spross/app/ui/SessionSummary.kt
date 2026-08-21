package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.SessionUi
import net.spross.kern.box.StreakHealth
import net.spross.kern.box.TallyPartKind
import net.spross.kern.box.completionTallyParts

/**
 * What the round bought, in kern's own order and only where there is something to name —
 * a round that started nothing says so plainly instead of printing three zeros.
 */
@Composable
fun SessionSummary(model: AppModel, ui: SessionUi) {
    val chrome = model.chrome
    val parts = completionTallyParts(ui.introduced, ui.strengthened, ui.reviewed)
    val tally = if (parts.isEmpty()) {
        chrome.roundAllDone
    } else {
        parts.joinToString(" · ") {
            when (it.kind) {
                TallyPartKind.Introduced -> chrome.roundNew.format(it.count)
                TallyPartKind.Consolidated -> chrome.roundConsolidated.format(it.count)
                TallyPartKind.Reviews -> chrome.roundReviewed.format(it.count)
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎉", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(chrome.sessionDone, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            tally,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (ui.streakDays > 0) {
            Spacer(Modifier.height(16.dp))
            // why: the day unit declines with the count (1 Tag / 2 Tage) — the same
            // dayOne/dayMany pair the Heute pill wears, so the two can never disagree;
            // no "Serie:" prefix, matching the iOS summary's plain streak pill.
            val unit = if (ui.streakDays == 1) chrome.dayOne else chrome.dayMany
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DlSpace.s),
            ) {
                // This screen is only reached by finishing a round, so today has reviews
                // layer-ok: the run is safe until tomorrow — no other grade can stand here
                StreakFlame(StreakHealth.Earned, MaterialTheme.typography.titleMedium)
                Text("${ui.streakDays} $unit", style = MaterialTheme.typography.titleMedium)
            }
            if (ui.streakIsRecord) {
                Text(chrome.streakRecord, style = MaterialTheme.typography.titleMedium,
                    color = Dl.colors.accent)
            }
        }
        if (ui.restSuggested) {
            // why: a day the box itself is telling the learner to stop makes no growth
            // claim — a screen that celebrates and is contradicted two lines down
            // teaches the learner not to believe it.
            Spacer(Modifier.height(16.dp))
            Text(chrome.restHint, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(32.dp))
        // The offer stands only while there is something behind it: a refill that would
        // come back dry leaves the button doing nothing when tapped.
        if (ui.canPracticeMore) {
            OutlinedButton(
                onClick = { model.continueEndless() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(chrome.keepPracticing)
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = { model.finishSession() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(chrome.finish)
        }
    }
}
