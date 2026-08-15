package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.countLine

/**
 * The one card the day stands on, whichever it is: hero, headline, what it holds, the way
 * on, fine print. The four Heute cards differ in their content, never in that order.
 */
@Composable
private fun DayCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().panel(MaterialTheme.shapes.large),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(DlSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DlSpace.l),
            content = content,
        )
    }
}

/**
 * The day's mark, wearing the streak where there is one to wear.
 *
 * The mark and the run are ONE badge: as two elements they sandwiched the prose between
 * them, and a card that both cheers and counts says one thing, not two. Guarded, because
 * unguarded it read "🔥 0 Tage" to anyone who had not started a run.
 */
@Composable
private fun DayMark(emoji: String, streak: Int, chrome: Chrome) {
    if (streak <= 0) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        return
    }
    val unit = if (streak == 1) chrome.dayOne else chrome.dayMany
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(percent = 50))
            .padding(horizontal = DlSpace.l, vertical = DlSpace.m)
            .semantics(mergeDescendants = true) {
                contentDescription = countLine(chrome.streakDaysOne, chrome.streakDays, streak)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.s),
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Text("$streak", style = MaterialTheme.typography.headlineSmall)
        Text(
            unit,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * There is a round to sit down to.
 *
 * No progress ring: a growing box sets no daily quota, so any arc would divide work done
 * by work still queued — and both climb through the day, leaving the ring fullest exactly
 * when a capped backlog is worst. The counts say it without the false comfort.
 */
@Composable
fun SessionCard(model: AppModel, standing: HeuteStanding, streak: Int) {
    val chrome = model.chrome
    val offer = standing.offer
    DayCard {
        DayMark(if (streak > 0) "🔥" else "✨", streak, chrome)
        Text(
            headlineText(chrome, offer.headline),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            offerSummary(chrome, offer),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (offer.dueHeldBack > 0) {
            // The cap is a promise, not a loss: name the rest, so a backlog never looks
            // like cards that vanished.
            Text(
                chrome.sessionHeldBack.format(offer.dueHeldBack),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            onClick = { model.startSession() },
            modifier = Modifier.fillMaxWidth().pressSpring(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(chrome.sessionStart, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Nothing is due. "Done" only once the day has actually been WORKED — otherwise nothing is
 * due right now, which is a different message and must not claim a finish the learner
 * never made ([net.spross.kern.box.TodayReport.worked] is the difference).
 */
@Composable
fun DoneCard(model: AppModel, standing: HeuteStanding, streak: Int) {
    val chrome = model.chrome
    val worked = standing.today.worked
    DayCard {
        DayMark(if (worked) "🎉" else "🌱", streak, chrome)
        Text(
            if (worked) chrome.doneToday else chrome.caughtUpTitle,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        todayTally(chrome, standing.today)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        // An extra round is an ordinary round composed on demand, so it stands in every
        // done state with active cards; hidden only when the box can compose nothing at all.
        if (standing.canPracticeMore) {
            OutlinedButton(
                onClick = { model.startExtraSession() },
                modifier = Modifier.fillMaxWidth().pressSpring(),
                shape = MaterialTheme.shapes.small,
            ) { Text(chrome.extraRound) }
        }
        // Under the button on purpose: what happens next is the smallest thing on the card,
        // and the way on is what the thumb is looking for.
        Text(
            tomorrowText(chrome, standing.tomorrow, standing.tomorrowDue),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The two cards that state a condition rather than offer a round: a failure, or an empty box. */
@Composable
fun StateCard(
    emoji: String,
    title: String,
    message: String,
    action: Pair<String, () -> Unit>? = null,
) {
    DayCard {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.let { (label, run) ->
            OutlinedButton(
                onClick = run,
                modifier = Modifier.fillMaxWidth().pressSpring(),
                shape = MaterialTheme.shapes.small,
            ) { Text(label) }
        }
    }
}
