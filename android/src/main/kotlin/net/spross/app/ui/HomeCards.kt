package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.countLine
import net.spross.kern.box.StreakHealth

/**
 * The one card the day stands on, whichever it is: hero, headline, what it holds, the way
 * on, fine print. The four Home cards differ in their content, never in that order.
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
 *
 * [emoji] is the card's OWN mark — a celebration, a sprout. Where it is null the badge
 * wears the run's flame instead, at the grade [health] gives it.
 */
@Composable
private fun DayMark(emoji: String?, streak: Int, health: StreakHealth, chrome: Chrome) {
    if (streak <= 0) {
        emoji?.let { Text(it, style = MaterialTheme.typography.displaySmall) }
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
        if (emoji == null) {
            StreakFlame(health, MaterialTheme.typography.titleLarge)
        } else {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
        }
        Text("$streak", style = MaterialTheme.typography.titleLarge)
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
fun SessionCard(model: AppModel, standing: HomeStanding, streak: Int, health: StreakHealth) {
    val chrome = model.chrome
    val offer = standing.offer
    DayCard {
        // A run wears its own flame; without one there is nothing to grade, so the day
        // gets a plain mark instead.
        DayMark(if (streak > 0) null else "✨", streak, health, chrome)
        Text(
            headlineText(chrome, standing.headline),
            style = MaterialTheme.typography.titleLarge,
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
        // A long round is more than an evening some days, and an abandoned one leaves the
        // day unworked; kern says when the two are different enough to offer both.
        if (offer.shortRound > 0) {
            OutlinedButton(
                onClick = { model.startShortSession() },
                modifier = Modifier.fillMaxWidth().pressSpring(),
                shape = MaterialTheme.shapes.small,
            ) { Text(chrome.sessionShortRound) }
        }
    }
}

/**
 * Nothing is due. "Done" only once the day has actually been WORKED — otherwise nothing is
 * due right now, which is a different message and must not claim a finish the learner
 * never made ([net.spross.kern.box.TodayReport.worked] is the difference).
 */
@Composable
fun DoneCard(model: AppModel, standing: HomeStanding, streak: Int, health: StreakHealth) {
    val chrome = model.chrome
    val worked = standing.today.worked
    DayCard {
        DayMark(if (worked) "🎉" else "🌱", streak, health, chrome)
        Text(
            if (worked) chrome.doneToday else chrome.caughtUpTitle,
            style = MaterialTheme.typography.titleLarge,
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

/**
 * Listening: the quiet card under the day's round.
 *
 * The round is what the box ASKS of the learner; this is what the learner can do when
 * answering is not on the table — a walk, a commute, a sink full of dishes. It is not a
 * Sprosse and gets no chip on that row: a Sprosse is a skill with a ladder to climb, and
 * listening asks nothing, grades nothing and has no rung to reach (`docs/surfaces.md`).
 *
 * ONE CARD, and the whole card is the tap. The emoji leads, the title names the mode once,
 * and the subtitle carries the two facts the name cannot — which words it leans on, and that
 * it needs no hands. The chevron says it opens.
 */
@Composable
fun ListenCard(model: AppModel) {
    val chrome = model.chrome
    if (!model.listeningOffered) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressSpring()
            .panel(MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .semantics(mergeDescendants = true) { }
            .clickable(role = Role.Button) { model.startListening() }
            .padding(DlSpace.xl),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        Text(
            "🎧",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DlSpace.xs),
        ) {
            Text(chrome.listenTitle, style = MaterialTheme.typography.titleLarge)
            Text(
                chrome.listenSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            SprossIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The card that states a condition rather than offering a round: a failure. */
@Composable
fun StateCard(
    emoji: String,
    title: String,
    message: String,
) {
    DayCard {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
