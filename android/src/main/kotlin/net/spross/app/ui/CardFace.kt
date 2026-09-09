package net.spross.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.kern.model.EmojiCue

/**
 * The card face and the card built on it: the surface every prompt wears, the picture's
 * fixed slot beside the words, and the vocabulary card that composes both. The words
 * themselves are [CardText].
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
 * The ONE raised surface: the card fill, a soft shadow under it, and the hairline that
 * closes the edge.
 *
 * Every panel in the app wears this, not just the card a session asks its question on —
 * paper at `#FBFBF6` on paper at `#F2F1EA` is a four-percent step, so a panel with no
 * shadow under it is not a panel, it is a rectangle nobody can find. M3's own `Card`
 * defaults to zero elevation and the theme deliberately kills its tonal tint
 * (`surfaceTint = Transparent`), which left every surface but this one perfectly flat.
 *
 * The hairline is deliberately faint: the fill and the shadow carry the boundary and the
 * edge only closes it (iOS `cardSurface`, separator @ 0.6).
 */
@Composable
fun Modifier.panel(shape: Shape = MaterialTheme.shapes.medium): Modifier = this
    .dropShadow(shape, CARD_SHADOW)
    .background(MaterialTheme.colorScheme.surface, shape)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)

/**
 * WHERE a card puts its picture — stated as the SITUATION the card is in, never as a size.
 *
 * It was a size flag on the other phone once, and the one run that had to choose chose
 * wrong: listening drew the big picture, the words kept a column too narrow for their font,
 * and a six-letter word hyphenated ("mchele" as "mc-hele"). A caller can always say what
 * else its screen is holding; it cannot be trusted to turn that into a rendering.
 */
enum class CardArrangement {
    /**
     * The card SHARES the screen — a review or drill card standing above an input, a
     * button and a keyboard. Vertical space is the scarce axis, so the picture stays small
     * and sits BESIDE the words.
     */
    Beside,

    /**
     * The card OWNS the screen — a run whose only content is the card (listening: nothing
     * to type, nothing to press, no keyboard). Height is abundant and width is what the
     * words are short of, so the picture stands ABOVE them at full size and the words get
     * the card's whole width.
     */
    Above,
}

/**
 * A review card: the picture in a slot the CARD places, the words in the middle.
 *
 * Which slot is [CardArrangement]'s, worked out from what the surface is. Either way it is
 * held for the card's whole life and only its contents fade in, so a picture withheld until
 * the reveal fades into a space that was already there.
 *
 * A word with no picture drops the slot entirely and centers on itself.
 *
 * The card is read in two registers, and they are two different slots here. [content] is the
 * HEADWORD BLOCK — the one or two words the card is about — and it rides in the picture's
 * row, mirrored on the trailing edge so it stays centered in the card. [closingLines] and
 * [note] are the long lines, and they stand under that row across the card's full width.
 * They are STRINGS rather than a second lambda on purpose: the narrow column is barely wider
 * than a headword, and the one way to guarantee a plural line or a literal gloss never lands
 * in it is to give a caller no way to compose one there.
 */
@Composable
fun VocabCard(
    emoji: String?,
    /**
     * Kern's rule for this card ([EmojiCue]), not a boolean a caller worked out: whether the
     * picture would GIVE THE ANSWER AWAY is the engine's answer, and a component taking the
     * resolved flag instead let every caller re-derive it — and one of them get it wrong.
     * Null keeps the slot and never fills it.
     */
    cue: EmojiCue?,
    /** [EmojiCue.OnReveal]'s other half: whether the card has given its answer. */
    revealed: Boolean,
    modifier: Modifier = Modifier,
    /** What the surface is; the card works its own layout out from that. */
    arrangement: CardArrangement = CardArrangement.Beside,
    /**
     * Grammar and the other forms the ANSWER carries — the plural, the "auch:" family.
     * They say something ABOUT the answer rather than being it, so they close the card
     * instead of crowding the column the headwords stand in.
     */
    closingLines: List<String> = emptyList(),
    /**
     * The literal gloss, the card's last line. A note handed to a nested [CardReveal]
     * instead would land in the narrow column — this is where it belongs.
     */
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val emojiShown = emojiShowing(cue, revealed)
    val hasEmoji = !emoji.isNullOrEmpty()
    val shared = arrangement == CardArrangement.Beside
    // why: sized in sp, so the disc grows with the reader's text size along with the
    // glyph in it — a fixed disc would crop the picture at the larger settings.
    val slot = with(LocalDensity.current) { (if (shared) EMOJI_SLOT else EMOJI_HERO).toDp() }
    val glyph = if (shared) EMOJI_GLYPH else EMOJI_HERO_GLYPH
    CardFace(
        // why: a shared-screen card holds one height whether the prompt is a word, a word
        // under an area label, or the replay glyph of a by-ear question; a card that owns
        // the screen has nothing to keep still for and is free to grow into it.
        modifier = if (shared) modifier.heightIn(min = Theme.reserve.reviewCard) else modifier,
        padding = if (shared) Theme.spacing.lg else Theme.spacing.xl,
    ) {
        // why: the growth is animated INSIDE the face, so the edge and the shadow
        // are never clipped mid-reveal — the card simply gets taller under them.
        val grow = Modifier.fillMaxWidth().animateContentSize()
        when (arrangement) {
            CardArrangement.Beside -> Column(
                modifier = grow,
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                ) {
                    if (hasEmoji) EmojiSlot(emoji.orEmpty(), emojiShown, slot, glyph)
                    CardWords(Modifier.weight(1f), Theme.spacing.xs, content)
                    // why: the picture's mirror on the trailing edge, so the headwords are
                    // centered in the CARD rather than in what is left of it — the picture
                    // then lands roughly parallel to the reveal's divider instead of
                    // floating above the whole stack. The width this costs is given back
                    // below, where the long lines take the card whole.
                    if (hasEmoji) Spacer(Modifier.width(slot))
                }
                ClosingLines(closingLines, note)
            }

            CardArrangement.Above -> Column(
                modifier = grow,
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (hasEmoji) EmojiSlot(emoji.orEmpty(), emojiShown, slot, glyph)
                CardWords(Modifier.fillMaxWidth(), Theme.spacing.lg, content)
                ClosingLines(closingLines, note)
            }
        }
    }
}

/** The headword block, as one centered column — what a picture may stand beside. */
@Composable
private fun CardWords(
    modifier: Modifier,
    spacing: Dp,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(spacing),
    horizontalAlignment = Alignment.CenterHorizontally,
    content = content,
)

/**
 * What the card says ABOUT its answer rather than as the answer: the grammar, the other
 * forms, the literal gloss. These are the long lines and they stand beside nothing, so
 * they take the card's full width.
 */
@Composable
private fun ClosingLines(lines: List<String>, note: String?) {
    if (lines.isEmpty() && note == null) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lines.forEach { CardLine(it) }
        note?.let { PauseLine(it) }
    }
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
private fun EmojiSlot(emoji: String, shown: Boolean, size: Dp, glyph: TextUnit) {
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
 * Two sizes, one per [CardArrangement]: small where the picture rides beside the words and
 * takes as little of their width as it can, full size where it stands above them with
 * nothing to make room for.
 */
private val EMOJI_SLOT = 52.sp
private val EMOJI_GLYPH = 28.sp
private val EMOJI_HERO = 96.sp
private val EMOJI_HERO_GLYPH = 52.sp

/**
 * The one card shadow — soft and low, so the card LIFTS rather than casting a box.
 *
 * An elevation shadow is the platform's, cut for the platform's own depth ladder: tight,
 * dark, and hard at the edge. The canonical one is a wide bloom at 8 % black, dropped six
 * below the card (iOS `cardShadow`), and it is drawn here rather than asked for so the
 * two cuts lift their cards the same amount.
 */
private val CARD_SHADOW = Shadow(
    radius = 16.dp,
    color = Color.Black,
    offset = DpOffset(0.dp, 6.dp),
    alpha = 0.08f,
)
