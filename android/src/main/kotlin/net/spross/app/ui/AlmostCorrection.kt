package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.spross.app.Chrome

/**
 * The form an accepted-but-unclean answer owes back — a slip's proper spelling, or the
 * word that actually played where the card accepts the one written.
 *
 * A correction is the one thing on screen the learner most needs to take in, so it gets a
 * box rather than a subtitle: the caption says which of the two ambers it was, the FORM sits
 * under it at reading size, and the speaker beside it is what makes the replay findable —
 * the tap was always there, and an affordance nobody can see is no affordance
 * (iOS `AnswerInputView.correctionBox`).
 *
 * The speaker drops entirely where nothing can be heard, rather than offering a control
 * that would do nothing.
 */
@Composable
fun AlmostCorrection(
    caption: String,
    form: String,
    chrome: Chrome,
    pronounce: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val amber = Theme.colors.amber
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Theme.colors.wash(amber), shape)
            .border(1.dp, amber.copy(alpha = 0.35f), shape)
            .clip(shape)
            .padding(Theme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        // Decorative: the caption beside it already says what the turn was, and TalkBack
        // reading "arrow" first says nothing the words do not.
        Icon(
            SprossIcons.CornerDownRight,
            contentDescription = null,
            tint = amber,
            modifier = Modifier.size(SPEAKER_GLYPH),
        )
        Column(
            // why: TalkBack has no autoplay to tell it what became of the answer — the
            // correction announces itself where the learner's focus already is. Merged, so
            // caption and form arrive as one announcement instead of two; the speaker stays
            // outside it, a stop of its own, because a correction that cannot be replayed is
            // the thing this box exists to fix.
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = Theme.colors.textSecondary,
            )
            Text(
                form,
                style = MaterialTheme.typography.titleMedium,
                color = Theme.colors.textPrimary,
            )
        }
        if (pronounce != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = pronounce)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = chrome.a11yActionPronounce
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SprossIcons.Speaker,
                    contentDescription = null,
                    tint = Theme.colors.textSecondary,
                    modifier = Modifier.size(SPEAKER_GLYPH),
                )
            }
        }
    }
}
