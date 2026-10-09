package net.spross.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import net.spross.app.Chrome
import net.spross.kern.design.ChoiceTile
import net.spross.kern.design.ChoiceVerdict

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
    // The tile's state and what it announces are kern's ([ChoiceTile]).
    val state = ChoiceTile.of(option, answer, chosen)
    // why: correctness is never color alone — the mark carries it on screen and the state
    // description carries it to TalkBack.
    val mark = when (state) {
        ChoiceTile.Answer -> "✓"
        ChoiceTile.WrongPick -> "✗"
        else -> null
    }
    val palette = Theme.colors
    val target = when (state) {
        ChoiceTile.Answer -> palette.wash(palette.success)
        ChoiceTile.WrongPick -> palette.wash(palette.wrong)
        // A tile is a recessed slot, not a card: it takes the chip fill, so an unanswered
        // one still reads as a tile against the paper behind it.
        else -> palette.surfaceTint
    }
    // why: the fill eases into its verdict instead of snapping the instant a pick lands.
    val fill by animateColorAsState(target, turnTween(), label = "tileFill")
    // why: the mark fades in rather than snapping alongside the fill.
    val markAlpha by animateFloatAsState(if (mark != null) 1f else 0f, turnTween(), label = "tileMark")
    val markColor = if (state == ChoiceTile.Answer) palette.success else palette.wrong
    OutlinedButton(
        onClick = onClick,
        enabled = state == ChoiceTile.Open,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.heightIn(min = Theme.reserve.tile).semantics {
            described?.let { contentDescription = it }
            when (state.verdict) {
                ChoiceVerdict.Correct -> stateDescription = chrome.a11yVerdictCorrect
                ChoiceVerdict.Wrong -> stateDescription = chrome.a11yVerdictWrong
                null -> {}
            }
        },
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = fill,
            disabledContainerColor = fill,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Box(Modifier.fillMaxSize().padding(Theme.spacing.sm), contentAlignment = Alignment.Center) {
            Text(option, style = optionStyle)
            // why: pinned to the tile's corner rather than appended after the word, and
            // faded in rather than snapping in alongside the fill.
            Text(
                mark.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = markColor,
                modifier = Modifier.align(Alignment.TopEnd).alpha(markAlpha),
            )
        }
    }
}
