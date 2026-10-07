package net.spross.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import net.spross.kern.session.Question

/**
 * The review card: a word over its meaning, set at headword size with its grammar,
 * the picture beside the headwords rather than above the whole stack.
 *
 * The prompt is compact (no space reserved for the answer); the reveal expands the card.
 * The picture's slot is held for the card's whole life and mirrored on the far edge,
 * so a withheld picture fades into a space already kept for it.
 */
@Composable
internal fun CardContext.ReviewFace(modifier: Modifier) {
    val emoji = question.emoji?.takeIf { it.isNotEmpty() }
    val slot = with(LocalDensity.current) { EMOJI_SLOT.toDp() }
    // why: a review card holds one height whether the prompt is a word, a word under an
    // area label, or the replay glyph of a by-ear question.
    CardFace(modifier.heightIn(min = Theme.reserve.reviewCard)) {
        Column(
            // why: the growth is animated inside the face, so the edge is never clipped mid-reveal.
            modifier = Modifier.fillMaxWidth().animateContentSize(turnTween()),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                if (emoji != null) EmojiSlot(emoji, emojiShowing(question.emojiCue, opens), slot, EMOJI_GLYPH)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HeadwordBlock(question.prompt, emphasized = false)
                    PluralLine(question.prompt)
                    // why: the divider belongs beside the picture, so the reveal grows the row
                    // rather than starting a second stack under it.
                    if (opens) CardReveal { HeadwordBlock(question.answer, emphasized = true) }
                }
                if (emoji != null) Spacer(Modifier.width(slot))
            }
            if (opens) ClosingLines()
            OtherWordLine()
        }
    }
}

/**
 * What the reveal says ABOUT the answer rather than as the answer: grammar, the other forms,
 * the closing note. They are the long lines and sit beside nothing, so they take the full width.
 */
@Composable
private fun CardContext.ClosingLines() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PluralLine(question.answer)
        alternatesText()?.let { CardLine(it) }
        noteText()?.let { PauseLine(it) }
    }
}

/** The word itself, under the area named over an ambiguous prompt. */
@Composable
internal fun CardContext.HeadwordBlock(side: Question.Side, emphasized: Boolean) {
    // why: ABOVE the headword, so it reads as a label on the prompt and never
    // sits in the plural/alternates region that belongs to the reveal.
    side.context?.let { CardCue(areaTitle(it)) }
    if (side.form == Question.Form.Sound) {
        ReplayGlyph(pronounce(side), chrome, replayFocus)
        return
    }
    val text = side.text ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm, Alignment.CenterHorizontally),
    ) {
        SpokenWord(pronounce(side), chrome, Modifier.weight(1f, fill = false)) {
            Headword(
                tagged(Theme.colors.articleColoredText(side.article, text, side.lang), side.lang),
                color = if (emphasized) Theme.colors.accent else Color.Unspecified,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (side.femMarker) FeminineBadge(chrome)
    }
}

@Composable
internal fun CardContext.PluralLine(side: Question.Side) {
    side.plural?.let { CardLine(pluralText(it, chrome)) }
}
