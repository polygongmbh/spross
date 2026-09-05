package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A short fact about THIS prompt, in the language being LEARNED — the place word the first
 * time a length appears, the word a date's pattern adds the first time it is asked.
 *
 * One shape for both drill cards, because it is one rule: a first-sight hint hands over
 * target-language material the card cannot otherwise teach, and it is scaffolding for a
 * prompt still unanswered, so the reveal TAKES its slot rather than stacking under it
 * (`docs/drills.md`). The iOS twin is `DrillHintPill`.
 */
@Composable
fun DrillHintPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Theme.colors.accent,
        modifier = Modifier
            .background(Theme.colors.surfaceTint, RoundedCornerShape(percent = 50))
            .padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.sm),
    )
}
