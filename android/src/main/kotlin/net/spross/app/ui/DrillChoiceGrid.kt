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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import net.spross.app.Chrome

/**
 * The 2×2 a multiple-choice question is answered off, wherever one is asked:
 * the letters ladder's opening stages and the calendar's warm-up Sprosse.
 *
 * What is shared is the VERDICT skin — the fill an answered tile takes, the mark that
 * carries correctness for anyone who cannot tell the two tints apart, the tile going dead
 * once a pick has landed, and what TalkBack hears. That half must never drift between two
 * drills; a learner reading a wrong pick as right on one screen and not the other is one
 * app behaving as two.
 *
 * What is NOT shared is how an option is SET, which is the only real difference: a
 * letterform is a picture and is set at picture size, a calendar name is prose.
 * [describe] covers the other one — a bare Cyrillic glyph read by a German engine is a
 * guess where "Buchstabe ч" is not, while a name needs no help being read as itself.
 */
@Composable
fun DrillChoiceGrid(
    /** The options in kern's own shuffled order — both platforms render the same draw. */
    options: List<String>,
    /** Which of them is right; the grid marks it once a pick has landed. */
    answer: String,
    /** What was picked, or null while the question is still owed. */
    chosen: String?,
    /** How one option is set on its tile. */
    optionStyle: TextStyle,
    chrome: Chrome,
    /** What a screen reader hears in place of the bare text, where it is not a word. */
    describe: (String) -> String? = { null },
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        for (row in options.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                for (option in row) {
                    Tile(option, answer, chosen, optionStyle, chrome, describe(option),
                        Modifier.weight(1f)) { onPick(option) }
                }
                // why: an odd last row keeps the grid's column width instead of stretching
                // one tile across the screen.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Tile(
    option: String,
    answer: String,
    chosen: String?,
    optionStyle: TextStyle,
    chrome: Chrome,
    described: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val answered = chosen != null
    val isAnswer = option == answer
    val isChosen = option == chosen
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
            described?.let { contentDescription = it }
            if (answered && isAnswer) stateDescription = chrome.a11yVerdictCorrect
            if (answered && isChosen && !isAnswer) stateDescription = chrome.a11yVerdictWrong
        }.pressSpring(),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = fill,
            disabledContainerColor = fill,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(option, style = optionStyle)
        mark?.let { Text("  $it", style = MaterialTheme.typography.titleLarge) }
    }
}
