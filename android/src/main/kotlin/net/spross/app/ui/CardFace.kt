package net.spross.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import net.spross.kern.model.EmojiCue

/**
 * The card face: the surface every prompt wears, and the picture's fixed slot.
 * The card built on it is [QuestionCard]; the words themselves are [CardText].
 *
 * `docs/drills.md`: a drill card is a review card — same
 * face, same reveal — so both ride [CardFace] and neither may cut its own.
 */

/**
 * The ONE card face: surface fill, the hairline edge, a soft shadow, and the inner padding
 * that lets content compose flat. Everything a session puts a question on wears it, so a
 * screen never shows two cards cut from different cloth.
 *
 * Content is centered and evenly spaced, because a card is read as one block from the
 * middle out — a caller that wants a row lays one out inside.
 */
@Composable
fun CardFace(
    modifier: Modifier = Modifier,
    /** The inset the content composes flat inside; a card with room to spare takes more. */
    padding: Dp = Theme.spacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .panel(MaterialTheme.shapes.large)
            .padding(padding),
        // why: a card holds a reserved minimum height, so before the reveal its content is
        // shorter than the card it sits in. Arranged from the top, the question hung off
        // the ceiling with the reserve pooled underneath it; the block is read from the
        // middle out, so it is centered in whatever height the card currently has.
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * Whether the picture is on the card YET.
 *
 * Kern decides WHICH cue a word gets — the picture upfront while it is still landing, or
 * held back to the reveal once it stands on its own ([EmojiCue]). All this adds is when
 * the held-back one arrives: with the answer, never before it, and never a frame later
 * than any other reveal line. A card with no picture has no cue and never shows a slot.
 */
fun emojiShowing(cue: EmojiCue?, revealed: Boolean): Boolean =
    cue == EmojiCue.Upfront || (cue != null && revealed)

@Composable
internal fun EmojiSlot(emoji: String, shown: Boolean, size: Dp, glyph: TextUnit) {
    // why: the picture FADES into a slot that was already there — appearing would push
    // every line of the card down at the moment the answer needs reading.
    //
    // Keyed on the picture itself, so a NEW word starts wherever that word belongs rather
    // than inheriting the last one's opacity. Animating across the swap fades the incoming
    // picture out: the glyph is already the next card's while the alpha is still traveling
    // down from the card that has gone, which shows the answer to a question not yet asked.
    val fade = remember(emoji) { Animatable(if (shown) 1f else 0f) }
    LaunchedEffect(emoji, shown) { fade.animateTo(if (shown) 1f else 0f) }
    Box(
        // why: the fade takes the DISC with it, not just the picture in it. Fading the
        // glyph alone leaves an empty gray circle sitting on every held-back card until
        // the answer lands, which reads as a picture that failed to load rather than as
        // one deliberately withheld. Alpha does not measure, so the slot is still held
        // and nothing below it moves when the picture arrives (iOS fades the whole
        // illustration for the same reason).
        modifier = Modifier.size(size).alpha(fade.value).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            emoji,
            fontSize = glyph,
            // Decorative: the headword beside it carries the content, and a screen
            // reader announcing "thinking face" before the word helps nobody.
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * The picture's disc and the glyph in it, both in sp so they scale together.
 *
 * Two sizes: small where the picture rides beside the words and takes as little of their
 * width as it can, full size where it stands above them with nothing to make room for.
 */
internal val EMOJI_SLOT = 52.sp
internal val EMOJI_GLYPH = 28.sp
internal val EMOJI_HERO = 96.sp
internal val EMOJI_HERO_GLYPH = 52.sp
