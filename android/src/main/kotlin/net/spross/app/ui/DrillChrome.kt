package net.spross.app.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import net.spross.kern.trainer.ChallengeVerdict
import net.spross.kern.trainer.DrillRunSummary
import net.spross.kern.trainer.AnswerStreakMilestone
import net.spross.kern.trainer.TimedOutcome

/**
 * What a drill puts around whatever it happens to be asking, inside the shell every asking
 * surface shares ([DrillRunScaffold]): the score line, the way out offered where it is
 * wanted, and the tile a closed run leaves on the page that started it.
 */

/**
 * The score line above the card: which Sprosse the run stands on and how long the answer streak
 * is. The record stays off it — a record is named where it falls, on the pause and the result
 * tile, never counted mid-run. A timed run's clock and score ([timed]) stand after the Sprosse.
 *
 * [Sprosse] is worded by the drill that owns it — a digit count reads differently from a plain
 * Sprosse — and is null where a run has one Sprosse only.
 */
@Composable
fun DrillStreakLine(
    sprosse: String?,
    answerStreak: Int,
    chrome: Chrome,
    timed: String? = null,
) {
    val parts = listOfNotNull(sprosse, timed, chrome.trainerRunStreak.format(answerStreak))
    val spoken = listOfNotNull(timed, chrome.a11yCountStreakInARow.format(answerStreak)).joinToString(", ")
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.titleMedium,
        color = if (answerStreak > 0) Theme.colors.accent else Theme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = spoken },
    )
}

/**
 * "Fertig", under the button that goes on. Between the pauses kern calls
 * ([net.spross.kern.trainer.DrillPacing]) an endless run has no end of its own, so the offer
 * is tied to the one moment a learner is weighing it — kern's [second miss in a
 * row][net.spross.kern.trainer.NumbersRunState.offersFinish]. The corner ✕ still works;
 * this is the same close, worded as finishing rather than abandoning.
 */
@Composable
fun DrillStopOffer(chrome: Chrome, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
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
            // why: one TalkBack stop, unless a share button must stay reachable.
            .semantics(mergeDescendants = summary.timed?.replyCode == null) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
    ) {
        Text(milestoneEmoji(summary.milestone), fontSize = 40.sp) // card-parity: the milestone emoji's own size, not a prompt role
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp), // card-parity: the tally lines sit tighter than xs
        ) {
            Text(
                countLine(chrome.trainerResultTasksDoneOne, chrome.trainerResultTasksDone, summary.done),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                chrome.trainerResultBestStreak.format(summary.bestAnswerStreak),
                style = MaterialTheme.typography.bodySmall,
                color = Theme.colors.textSecondary,
            )
            summary.timed?.let { timed -> TimedLines(timed, chrome) }
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

/** A timed run's tile additions: score, comparison with a challenge, and the reply code. */
@Composable
private fun TimedLines(timed: TimedOutcome, chrome: Chrome) {
    Text(
        countLine(chrome.trainerRunScoreOne, chrome.trainerRunScore, timed.score),
        style = MaterialTheme.typography.bodySmall,
    )
    val theirs = timed.challenge?.opponentScore
    val verdict = when (timed.verdict) {
        ChallengeVerdict.Won -> chrome.trainerChallengeWon
        ChallengeVerdict.Tied -> chrome.trainerChallengeTied
        ChallengeVerdict.Lost -> chrome.trainerChallengeLost
        null -> null
    }
    if (verdict != null && theirs != null) {
        Text(verdict.format(theirs), style = MaterialTheme.typography.bodySmall, color = Theme.colors.accent)
    }
    val reply = timed.replyCode ?: return
    val link = timed.replyLink ?: return
    val context = LocalContext.current
    TextButton(onClick = { context.shareChallenge(chrome.trainerChallengeMessage.format(timed.score, link)) }) {
        Text(chrome.trainerChallengeSend.format(reply), style = MaterialTheme.typography.bodySmall)
    }
}

/** Shares a challenge link through the system share sheet. */
private fun Context.shareChallenge(text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, null))
}

/**
 * The ladder a run's best answer streak earns. Kern names the MILESTONES and their thresholds; which
 * glyph wears one is this platform's chrome.
 */
fun milestoneEmoji(milestone: AnswerStreakMilestone): String = when (milestone) {
    AnswerStreakMilestone.Trophy -> "🏆"
    AnswerStreakMilestone.Cheer -> "🎉"
    AnswerStreakMilestone.Effort -> "💪"
    AnswerStreakMilestone.Sprout -> "🌱"
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
