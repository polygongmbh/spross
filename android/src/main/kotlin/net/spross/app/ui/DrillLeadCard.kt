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
import net.spross.kern.session.SessionOfferKind
import net.spross.kern.trainer.DayLead
import net.spross.kern.trainer.DrillSuggestion

/**
 * The day's card once the day has answered more than it still owes and kern names a drill
 * ([DayLead.Drill], `docs/drills.md` § The suggestion): what the day has done on top, the drill
 * as the card's body and first action, and the round still one button away.
 */
@Composable
fun DrillLeadCard(
    model: AppModel,
    standing: HomeStanding,
    pick: DrillSuggestion.Pick,
    streak: Int,
    health: StreakHealth,
) {
    val chrome = model.chrome
    val roundLeft = standing.offer.kind != SessionOfferKind.Nothing
    DayCard {
        DayHeader(model, standing, streak, health)
        HorizontalDivider()
        Text(pick.drill.emoji, style = MaterialTheme.typography.displaySmall)
        Text(
            chrome.homeSuggestionTitle.format(pick.drill.title(chrome)),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            reason(chrome, pick),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { model.open(pick.drill) },
            modifier = Modifier.fillMaxWidth().pressSpring(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(chrome.homeOfferStart, style = MaterialTheme.typography.titleMedium)
        }
        // The round with reviews still due; an extra one once none are.
        if (roundLeft || standing.canPracticeMore) {
            OutlinedButton(
                onClick = { if (roundLeft) model.startSession() else model.startExtraSession() },
                modifier = Modifier.fillMaxWidth().pressSpring(),
                shape = MaterialTheme.shapes.small,
            ) { Text(chrome.homeDoneExtraRound) }
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
        DayMark(null, streak, health, chrome)
    }
}
