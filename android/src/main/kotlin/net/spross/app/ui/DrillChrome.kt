package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.countLine
import net.spross.kern.session.AnswerTone
import net.spross.kern.trainer.DrillRunSummary
import net.spross.kern.trainer.StreakTier

/**
 * What the two endless drills put around whatever they happen to be asking: the top bar,
 * the score line, the way out offered where it is wanted, and the tile a closed run leaves
 * on the page that started it.
 *
 * Their state machines stay apart (a heard glyph and a typed numeral share no grammar);
 * this is the whole of what the two have in common, and a second copy of it is how two
 * beats drift apart.
 */

/**
 * The chrome of an ENDLESS run, which has no total to count toward.
 *
 * The bar's filled and empty stretches move together — one empty slot for the question on
 * screen — so it fills as the run grows instead of breaking past a fixed end, and the
 * counter reads clean/answered rather than position/total.
 */
@Composable
fun DrillTopBar(
    model: AppModel,
    outcomes: List<AnswerTone>,
    cleanCount: Int,
    answered: Int,
    onClose: () -> Unit,
) {
    val chrome = model.chrome
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        DrillCloseButton(chrome, onClose)
        SegmentsBar(outcomes, remaining = 1, modifier = Modifier.weight(1f))
        Text(
            "$cleanCount/$answered",
            style = MaterialTheme.typography.bodySmall,
            color = Dl.colors.textSecondary,
        )
        ReadAloudSwitch(model)
    }
}

/** The way out of a running drill — first in the bar, so it is never hunted for. */
@Composable
fun DrillCloseButton(chrome: Chrome, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics(mergeDescendants = true) { contentDescription = chrome.close }
            .clickable(role = Role.Button, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", style = MaterialTheme.typography.titleMedium, color = Dl.colors.textSecondary)
    }
}

/**
 * The score line above the card: which rung the run stands on, how long the streak is, and
 * the standing record while the streak has fallen short of it.
 *
 * [rung] is worded by the drill that owns it — a digit count reads differently from a plain
 * level — and is null where a run has one rung only. [announcesRecord] carries a real
 * difference rather than settling it: the letter drill has always spoken the streak alone.
 */
@Composable
fun DrillStreakLine(
    rung: String?,
    streak: Int,
    bestStreak: Int,
    chrome: Chrome,
    announcesRecord: Boolean = false,
) {
    val showsRecord = bestStreak > streak
    val parts = listOfNotNull(
        rung,
        chrome.streak.format(streak),
        if (showsRecord) chrome.record.format(bestStreak) else null,
    )
    val spoken = chrome.streakSpoken.format(streak) +
        if (announcesRecord && showsRecord) chrome.recordSpoken.format(bestStreak) else ""
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = if (streak > 0) Dl.colors.accent else Dl.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = spoken },
    )
}

/**
 * "Fertig", under the button that goes on. An endless run has no natural end, so the offer
 * is tied to the one moment a learner is weighing it — kern's [second miss in a
 * row][net.spross.kern.trainer.TrainerRunState.offersFinish]. The corner ✕ still works;
 * this is the same close, worded as finishing rather than abandoning.
 */
@Composable
fun DrillStopOffer(chrome: Chrome, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(chrome.finish)
    }
}

/**
 * What a closed run leaves behind, as the page that started it wears it: one tile above the
 * picks, where the button that opens the next run already is. Three figures do not earn a
 * page, and a page they do not earn is one more ✕ between a learner and their next run.
 */
@Composable
fun DrillResultTile(summary: DrillRunSummary, title: String, chrome: Chrome) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Dl.colors.surfaceTint, RoundedCornerShape(20.dp))
            .padding(DlSpace.l)
            // why: one TalkBack stop — the figures describe a single run.
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.l),
    ) {
        Text(tierEmoji(summary.tier), fontSize = 36.sp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                countLine(chrome.tasksDoneOne, chrome.tasksDone, summary.done),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                chrome.bestStreak.format(summary.bestStreak),
                style = MaterialTheme.typography.bodySmall,
                color = Dl.colors.textSecondary,
            )
            if (summary.newRecord) {
                Text(
                    chrome.newRecord,
                    style = MaterialTheme.typography.bodySmall,
                    color = Dl.colors.accent,
                )
            }
        }
        Text(title, style = MaterialTheme.typography.bodySmall, color = Dl.colors.textSecondary)
    }
}

/**
 * The ladder a run's best streak earns. Kern names the TIERS and their thresholds; which
 * glyph wears one is this platform's chrome.
 */
fun tierEmoji(tier: StreakTier): String = when (tier) {
    StreakTier.Trophy -> "🏆"
    StreakTier.Cheer -> "🎉"
    StreakTier.Effort -> "💪"
    StreakTier.Sprout -> "🌱"
}

/**
 * What a language is called wherever a drill names one — a page title, a field's
 * placeholder. The chrome's exonym, else the code, so a language the chrome does not know
 * is still named rather than spelled "UK".
 */
fun AppModel.languageName(language: String): String =
    catalog?.languages?.get(language)?.name ?: language

/** A section title on either overview page. */
@Composable
fun OverviewHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
}
