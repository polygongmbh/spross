package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import net.spross.app.AppModel
import net.spross.app.startExtraSession
import net.spross.app.startSession
import net.spross.kern.box.StreakHealth
import net.spross.kern.session.DayMark as DayMarkRule
import net.spross.kern.session.HomeStanding
import net.spross.kern.session.RoundStart
import net.spross.kern.trainer.DayLead

/**
 * The day's card once the day has answered more than it still owes and kern leads with a way
 * in other than the round ([DayLead], `docs/drills.md` § The suggestion): what the day has done
 * on top, the drill, listening or the conversation as the card's body and first action, and
 * the round still one button away.
 */
@Composable
fun LeadCard(
    model: AppModel,
    standing: HomeStanding,
    glyph: String,
    title: String,
    reason: String,
    streak: Int,
    health: StreakHealth,
    onOpen: () -> Unit,
) {
    val chrome = model.chrome
    DayCard {
        DayHeader(model, standing, streak, health)
        HorizontalDivider()
        Text(glyph, style = MaterialTheme.typography.displaySmall)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            reason,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(chrome.homeOfferStart, style = MaterialTheme.typography.titleMedium)
        }
        // The round with reviews still due; an extra one once none are (kern's `roundInstead`).
        standing.roundInstead?.let { round ->
            OutlinedButton(
                onClick = { if (round == RoundStart.Due) model.startSession() else model.startExtraSession() },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) { Text(chrome.homeSuggestionWordsInstead) }
        }
    }
}

/** What the day has done, compact: the check and its title, the tally, the run. */
@Composable
private fun DayHeader(model: AppModel, standing: HomeStanding, streak: Int, health: StreakHealth) {
    val chrome = model.chrome
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
            ) {
                Icon(SprossIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(chrome.homeDoneTitle, style = MaterialTheme.typography.titleMedium)
            }
            todayTally(chrome, standing.today)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DayMark(DayMarkRule.lead(streak), streak, health, chrome)
    }
}
