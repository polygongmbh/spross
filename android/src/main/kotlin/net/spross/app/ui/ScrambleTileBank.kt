package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.spross.app.Chrome
import net.spross.kern.trainer.ScrambleAtom

/**
 * The two halves a sentence is arranged on: the order taken shape above, the words still to
 * be spent below.
 *
 * A tap carries a chip up and another carries it back down — tapping is the whole gesture,
 * because a drag buys an arrangement nothing and costs assistive technology a great deal
 * (`docs/drills-words.md`). A spent chip stays in the bank, dimmed and disabled, rather than
 * vanishing: a bank that empties as it is used moves every chip under the thumb aiming at one.
 *
 * A chip is as wide as its word and no wider — "und" is not the size of "Krankenhaus", and a
 * grid that gave them one width would say the two are the same size of thing.
 */
@Composable
fun ScrambleTileBank(
    /** The atoms in kern's own dealt order — both platforms render the same deal. */
    bank: List<ScrambleAtom>,
    /** The arrangement so far, in the order it was committed. */
    placed: List<ScrambleAtom>,
    /** Whether the dealt atom at that index has already been carried up. */
    isTaken: (Int) -> Boolean,
    /** What the arrangement READS as — kern's `arranged`, so the row says a sentence. */
    arranged: String,
    verdict: ScrambleVerdict,
    chrome: Chrome,
    /** A bank slot tapped — an index into [bank]. */
    place: (Int) -> Unit,
    /** An answer-row slot tapped — an index into [placed]. */
    take: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
        AnswerRow(placed, arranged, verdict, chrome, take)
        BankRow(bank, isTaken, verdict, chrome, place)
    }
}

/**
 * How the arrangement stands. Anything but [Owed] locks every chip: the question has been
 * answered, and an order that could still be permuted afterwards would let a learner
 * brute-force one.
 */
enum class ScrambleVerdict { Owed, Correct, Wrong }

private val ScrambleVerdict.locked: Boolean get() = this != ScrambleVerdict.Owed

/** The arrangement: the words carried up, in the order they were committed. */
@Composable
private fun AnswerRow(
    placed: List<ScrambleAtom>,
    arranged: String,
    verdict: ScrambleVerdict,
    chrome: Chrome,
    take: (Int) -> Unit,
) {
    val border = when (verdict) {
        ScrambleVerdict.Owed -> Theme.colors.borderStrong
        ScrambleVerdict.Correct -> Theme.colors.success
        ScrambleVerdict.Wrong -> Theme.colors.wrong
    }
    val radius = 28.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // why: the row is reserved whether or not anything stands in it, so the bank
            // below never walks up the screen as the sentence is built.
            .heightIn(min = Theme.reserve.tile)
            .drawBehind {
                val width = (if (verdict.locked) 2.dp else 1.dp).toPx()
                drawRoundRect(
                    color = border,
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(
                        width = width,
                        // The dashes say the row is still open; a graded one closes solid.
                        pathEffect = if (verdict.locked) {
                            null
                        } else {
                            PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                        },
                    ),
                )
            }
            .padding(Theme.spacing.md)
            // why: NOT merged — a placed word that cannot be tapped back is the thing the
            // row exists to allow. The label and the sentence so far are the row's own.
            .semantics {
                contentDescription = chrome.a11yScrambleArrangement
                stateDescription = arranged
            },
        contentAlignment = Alignment.Center,
    ) {
        if (placed.isEmpty()) {
            Text(
                chrome.scrambleSentenceHint,
                style = MaterialTheme.typography.bodySmall,
                color = Theme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Theme.spacing.lg),
            )
        }
        ChipFlow {
            placed.forEachIndexed { slot, atom ->
                ScrambleChip(
                    word = atom.text,
                    dimmed = false,
                    enabled = !verdict.locked,
                    // TalkBack's "double tap to …": what this chip's tap would DO.
                    action = chrome.a11yActionTakeBack,
                ) { take(slot) }
            }
        }
        // why: correctness is never color alone — the mark carries it for anyone who cannot
        // tell the two tints apart (WCAG 1.4.1).
        Mark(verdict, chrome, Modifier.align(Alignment.TopEnd))
    }
}

/** The words still to be spent, in the order kern dealt them. */
@Composable
private fun BankRow(
    bank: List<ScrambleAtom>,
    isTaken: (Int) -> Boolean,
    verdict: ScrambleVerdict,
    chrome: Chrome,
    place: (Int) -> Unit,
) {
    ChipFlow(Modifier.semantics { contentDescription = chrome.a11yScrambleBank }) {
        bank.forEachIndexed { slot, atom ->
            val taken = isTaken(slot)
            ScrambleChip(
                word = atom.text,
                dimmed = taken,
                enabled = !verdict.locked && !taken,
                action = null,
            ) { place(slot) }
        }
    }
}

/** Chips left to right, wrapping where the line runs out, each line centered. */
@Composable
private fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        content()
    }
}

/**
 * One word's face, the same in the bank and in the row above it — a chip that changed shape
 * on the way up would read as a different thing.
 */
@Composable
private fun ScrambleChip(
    word: String,
    dimmed: Boolean,
    enabled: Boolean,
    action: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .alpha(if (dimmed) 0.35f else 1f)
            .clip(MaterialTheme.shapes.medium)
            .background(Theme.colors.surfaceTint)
            .clickable(enabled = enabled, onClickLabel = action, role = Role.Button, onClick = onClick)
            // why: a chip is a thumb target before it is a word — 48 dp is the floor a tap
            // may be aimed at.
            .heightIn(min = 48.dp)
            .padding(horizontal = Theme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            word,
            style = MaterialTheme.typography.titleMedium,
            color = Theme.colors.textPrimary,
            maxLines = 1,
        )
    }
}

/**
 * The mark keeps its own TalkBack stop rather than hiding: the tint it sits on is the only
 * other thing saying how the arrangement was graded.
 */
@Composable
private fun Mark(verdict: ScrambleVerdict, chrome: Chrome, modifier: Modifier) {
    val (icon, tint, said) = when (verdict) {
        ScrambleVerdict.Owed -> return
        ScrambleVerdict.Correct ->
            Triple(SprossIcons.Check, Theme.colors.success, chrome.a11yVerdictCorrect)
        ScrambleVerdict.Wrong ->
            Triple(SprossIcons.Close, Theme.colors.wrong, chrome.a11yVerdictWrong)
    }
    Icon(icon, contentDescription = said, tint = tint, modifier = modifier.padding(Theme.spacing.sm))
}
