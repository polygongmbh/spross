package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.countLine
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.trainer.DrillRunSummary
import net.spross.kern.trainer.StreakTier

/**
 * What a drill puts around whatever it happens to be asking, inside the shell every asking
 * surface shares ([DrillRunScaffold]): the score line, the way out offered where it is
 * wanted, and the tile a closed run leaves on the page that started it.
 */

/**
 * The score line above the card: which Sprosse the run stands on, how long the streak is, and
 * the standing record while the streak has fallen short of it.
 *
 * [Sprosse] is worded by the drill that owns it — a digit count reads differently from a plain
 * level — and is null where a run has one Sprosse only. [announcesRecord] carries a real
 * difference rather than settling it: the letter drill has always spoken the streak alone.
 */
@Composable
fun DrillStreakLine(
    sprosse: String?,
    streak: Int,
    bestStreak: Int,
    chrome: Chrome,
    announcesRecord: Boolean = false,
) {
    val showsRecord = bestStreak > streak
    val parts = listOfNotNull(
        sprosse,
        chrome.trainerRunStreak.format(streak),
        if (showsRecord) chrome.trainerRunRecord.format(bestStreak) else null,
    )
    val spoken = chrome.a11yCountStreakInARow.format(streak) +
        if (announcesRecord && showsRecord) chrome.a11ySuffixRecord.format(bestStreak) else ""
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = if (streak > 0) Theme.colors.accent else Theme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = spoken },
    )
}

/**
 * "Fertig", under the button that goes on. An endless run has no natural end, so the offer
 * is tied to the one moment a learner is weighing it — kern's [second miss in a
 * row][net.spross.kern.trainer.NumbersRunState.offersFinish]. The corner ✕ still works;
 * this is the same close, worded as finishing rather than abandoning.
 */
@Composable
fun DrillStopOffer(chrome: Chrome, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(chrome.commonDone)
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
            .background(Theme.colors.surfaceTint, MaterialTheme.shapes.medium)
            .padding(Theme.spacing.lg)
            // why: one TalkBack stop — the figures describe a single run.
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
    ) {
        Text(tierEmoji(summary.tier), fontSize = 36.sp) // card-parity: the tier emoji's own size, not a prompt role
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp), // card-parity: the tally lines sit tighter than xs
        ) {
            Text(
                countLine(chrome.trainerResultTasksDoneOne, chrome.trainerResultTasksDone, summary.done),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                chrome.trainerResultBestStreak.format(summary.bestStreak),
                style = MaterialTheme.typography.bodySmall,
                color = Theme.colors.textSecondary,
            )
            if (summary.newRecord) {
                Text(
                    chrome.trainerResultNewRecord,
                    style = MaterialTheme.typography.bodySmall,
                    color = Theme.colors.accent,
                )
            }
        }
        Text(title, style = MaterialTheme.typography.bodySmall, color = Theme.colors.textSecondary)
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
 * placeholder. Kern picks the name so both phones say the same one.
 */
fun AppModel.languageName(language: String): String =
    LanguageChoices.name(language, catalog?.languages?.get(language))

/** A section title on either overview page. */
@Composable
fun OverviewHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
}
