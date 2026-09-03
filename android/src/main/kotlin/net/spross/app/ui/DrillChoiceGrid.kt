package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import net.spross.app.Chrome

/**
 * A 2×2 of NAME tiles, in kern's own shuffled order — the calendar warm-up's answer.
 *
 * Not the letter drill's grid, though it is the same shape: a letterform is a picture and
 * is set at picture size, announced as "Buchstabe ч" because a bare Cyrillic glyph read by
 * a German engine is a guess. A calendar name is prose — it is set as prose, wraps rather
 * than truncating, and a screen reader saying it needs no help.
 */
@Composable
fun DrillNameTiles(
    names: List<String>,
    answer: String,
    chosen: String?,
    chrome: Chrome,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        for (row in names.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                for (name in row) {
                    NameTile(name, answer, chosen, chrome, Modifier.weight(1f)) { onPick(name) }
                }
                // why: an odd last row keeps the grid's column width instead of stretching
                // one tile across the screen.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NameTile(
    name: String,
    answer: String,
    chosen: String?,
    chrome: Chrome,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val answered = chosen != null
    val isAnswer = name == answer
    val isChosen = name == chosen
    // why: correctness is never color alone — the mark carries it on screen and the state
    // description carries it to TalkBack.
    val mark = when {
        answered && isAnswer -> "✓"
        answered && isChosen -> "✗"
        else -> null
    }
    val palette = Theme.colors
    val fill = when {
        answered && isAnswer -> palette.wash(palette.success)
        answered && isChosen -> palette.wash(palette.wrong)
        // A tile is a recessed slot, not a card: it takes the chip fill, so an unanswered
        // one still reads as a tile against the paper behind it.
        else -> palette.surfaceTint
    }
    OutlinedButton(
        onClick = onClick,
        enabled = !answered,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.heightIn(min = Theme.reserve.tile).semantics {
            if (answered && isAnswer) stateDescription = chrome.a11yVerdictCorrect
            if (answered && isChosen && !isAnswer) stateDescription = chrome.a11yVerdictWrong
        }.pressSpring(),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = fill,
            disabledContainerColor = fill,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(
            text = mark?.let { "$name  $it" } ?: name,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
