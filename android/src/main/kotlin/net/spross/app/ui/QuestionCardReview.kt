package net.spross.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import net.spross.kern.session.Question
import net.spross.kern.design.CardType

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
        side.marker?.let { FormBadge(it, chrome) }
    }
}

@Composable
internal fun CardContext.PluralLine(side: Question.Side) {
    side.plural?.let { CardLine(pluralText(it, chrome)) }
}

/**
 * The card that owns the screen: height is abundant and width is what the words are short of,
 * so the picture stands ABOVE them at full size and the words get the card's full width.
 */
@Composable
internal fun CardContext.ListeningFace(modifier: Modifier) {
    val emoji = question.emoji?.takeIf { it.isNotEmpty() }
    val hero = with(LocalDensity.current) { EMOJI_HERO.toDp() }
    // why: the meaning's LINE is held for the whole turn and only its ink fades in — a card that
    // grows and shrinks every few seconds pumps in height with nothing being revealed.
    // Keyed on the question, so a new word re-seeds the fade at nothing rather than showing
    // the incoming word's meaning at full ink before the word has been said once.
    val meaning = remember(question.key) { Animatable(0f) }
    LaunchedEffect(question.key, opens) { meaning.animateTo(if (opens) 1f else 0f) }
    CardFace(modifier, padding = Theme.spacing.xl) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (emoji != null) EmojiSlot(emoji, emojiShowing(question.emojiCue, opens), hero, EMOJI_HERO_GLYPH)
            HeadwordBlock(question.prompt, emphasized = false)
            CardReveal(
                // why: alpha does not measure, so the line is there all along — but it is not YET
                // part of the card, and a screen reader reading it would say the meaning early.
                modifier = Modifier.alpha(meaning.value)
                    .then(if (opens) Modifier else Modifier.clearAndSetSemantics { }),
                divided = CardType.LISTENING_REVEAL_DIVIDED,
            ) {
                HeadwordBlock(question.answer, emphasized = true)
            }
        }
    }
}
