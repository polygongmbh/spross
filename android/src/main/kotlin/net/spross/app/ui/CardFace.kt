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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.CardDisplay
import net.spross.app.Chrome
import net.spross.kern.model.EmojiCue
import net.spross.kern.model.Realization

/**
 * The card face and what a card is made of: the surface every prompt wears, the picture's
 * fixed slot beside the words, and the reveal a card grows downward.
 *
 * `docs/surfaces.md` § Trainers & the letter drill: a drill card is a review card — same
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
fun CardFace(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .panel(MaterialTheme.shapes.large)
            .padding(DlSpace.l),
        // why: a card holds a reserved minimum height, so before the reveal its content is
        // shorter than the card it sits in. Arranged from the top, the question hung off
        // the ceiling with the reserve pooled underneath it; the block is read from the
        // middle out, so it is centered in whatever height the card currently has.
        verticalArrangement = Arrangement.spacedBy(DlSpace.s, Alignment.CenterVertically),
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
 * edge only closes it (iOS `dlCardSurface`, separator @ 0.6).
 */
@Composable
fun Modifier.panel(shape: Shape = MaterialTheme.shapes.medium): Modifier = this
    .dropShadow(shape, CARD_SHADOW)
    .background(MaterialTheme.colorScheme.surface, shape)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)

/**
 * A review card: the picture in a slot BESIDE the words, the words in the middle.
 *
 * Vertical space is the scarce axis — card, field, button and keyboard share one screen —
 * so the picture never sits above the headword. The slot is held for the card's whole
 * life and only its contents fade in, which is what lets a reveal grow the card downward
 * without moving a line that was already there. It is mirrored on the trailing edge so
 * the words stay centered in the card rather than pushed off by the picture.
 *
 * A word with no picture drops the slot entirely and centers on itself.
 */
@Composable
fun VocabCard(
    emoji: String?,
    emojiShown: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hasEmoji = !emoji.isNullOrEmpty()
    // why: sized in sp, so the disc grows with the reader's text size along with the
    // glyph in it — a fixed disc would crop the picture at the larger settings.
    val slot = with(LocalDensity.current) { EMOJI_SLOT.toDp() }
    CardFace(modifier.heightIn(min = DlReserve.reviewCard)) {
        Row(
            // why: the growth is animated INSIDE the face, so the edge and the shadow
            // are never clipped mid-reveal — the card simply gets taller under them.
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
        ) {
            if (hasEmoji) EmojiSlot(emoji.orEmpty(), emojiShown, slot)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DlSpace.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
            if (hasEmoji) Spacer(Modifier.width(slot))
        }
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
private fun EmojiSlot(emoji: String, shown: Boolean, size: Dp) {
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
            fontSize = EMOJI_GLYPH,
            // Decorative: the headword beside it carries the content, and a screen
            // reader announcing "thinking face" before the word helps nobody.
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * A headword with the speaker that says it beside it.
 *
 * The tap is on the whole row, so the word keeps its size and its named action; the glyph
 * is what makes that tap FINDABLE — an affordance nobody can see is no affordance. It
 * drops entirely where the word cannot be heard, rather than offering a speaker that
 * would do nothing.
 */
@Composable
fun SpokenWord(
    pronounce: (() -> Unit)?,
    chrome: Chrome,
    modifier: Modifier = Modifier,
    word: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.pronounceOnTap(pronounce, chrome),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.xs, Alignment.CenterHorizontally),
    ) {
        // why: a hidden, inert copy of the glyph on the LEADING edge. Without the ballast
        // the word sits half a speaker left of center — off the plural line under it and
        // off the same word on the card's other face, since only the target side is heard.
        if (pronounce != null) Spacer(Modifier.size(SPEAKER_GLYPH))
        word()
        if (pronounce != null) {
            // Decorative: the tap and the action naming it live on the row around it,
            // so TalkBack reads the word and its action, never a loudspeaker picture.
            Icon(
                SprossIcons.Speaker,
                contentDescription = null,
                tint = Dl.colors.teal,
                modifier = Modifier.size(SPEAKER_GLYPH),
            )
        }
    }
}

/**
 * Target-side reveal: the word in the accent, its article in its own tint, the plural
 * line and the synonym family under it.
 *
 * The accent is the REVEAL's, not the target language's — a card is styled by role, so
 * the same word is neutral ink where it stands as the prompt. Grammar renders here and
 * nowhere else, because it is the target side's alone. The note is the CARD's last line
 * ([CardReveal]), which is why it is not drawn here.
 *
 * [alsoShown] names forms of this word standing ELSEWHERE on the screen — a rotated
 * recognition prompt, say. The citation form is always one of them, since this very
 * composable draws it.
 */
/**
 * THE headword of a card — the one word the whole card is about, on either side of it.
 *
 * It steps down to fit rather than breaking a word in half. The line bound is what makes
 * the step-down bite: with lines unbounded, a word wider than the card simply wraps
 * mid-word ("Sprach" / "e") and the paragraph reports no overflow at all, so the step
 * search would leave it at full size. A single token gets ONE line, because the only wrap
 * available to it IS a broken word; anything with a space keeps a second line and breaks
 * there. The verdict labels and the Werkstatt entries already wear the same pair.
 *
 * The floor is where iOS bottoms out — `minimumScaleFactor(0.85)` on its own headword
 * (`VocabCardView.swift`) — so a shrunken word never lands smaller here than it can there.
 * Both cuts treat this as insurance for the rare long word, not as the way words are sized.
 */
@Composable
fun Headword(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val style = MaterialTheme.typography.headlineMedium
    Text(
        text,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = if (text.text.any(Char::isWhitespace)) 2 else 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = HEADWORD_FLOOR,
            maxFontSize = style.fontSize,
        ),
    )
}

@Composable
fun Headword(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    Headword(AnnotatedString(text), modifier, color)

@Composable
fun TargetReveal(
    target: Realization,
    chrome: Chrome,
    modifier: Modifier = Modifier,
    pronounce: (() -> Unit)? = null,
    alsoShown: List<String> = emptyList(),
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DlSpace.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpokenWord(pronounce, chrome) {
            Headword(
                localizedTarget(Dl.colors.articleColoredText(target), target.lang),
                color = Dl.colors.accent,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        CardDisplay.pluralLine(target, chrome)?.let { CardLine(it) }
        CardDisplay.alsoLine(target, chrome, alsoShown + target.text)?.let { CardLine(it) }
    }
}

/**
 * What a card GROWS when the answer comes out: a short rule, the answer, and the note
 * last. Always below the prompt, always the same shape — a vocabulary card and a drill
 * card reveal alike, so the two never drift into two ideas of "the answer".
 */
@Composable
fun CardReveal(
    modifier: Modifier = Modifier,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = DlSpace.xs),
        verticalArrangement = Arrangement.spacedBy(DlSpace.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(44.dp).height(2.dp)
                .background(Dl.colors.separator, RoundedCornerShape(1.dp))
        )
        content()
        note?.let { PauseLine(it) }
    }
}

/** The card's small print: the plural, the "auch:" family, the meaning a heard card owes. */
@Composable
fun CardLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/**
 * A label ABOVE a headword — the area an ambiguous prompt is asking within.
 *
 * Smaller than [CardLine] on purpose: it reads as a label on the word, and it must not
 * be mistaken for the plural/alternates region that belongs to the reveal.
 */
@Composable
fun CardCue(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** The picture's disc and the glyph in it, both in sp so they scale together. */
private val EMOJI_SLOT = 52.sp
private val EMOJI_GLYPH = 28.sp

/**
 * Where a shrinking headword stops. iOS bottoms out at 0.85 of a 22 pt headword; this
 * lands no smaller, so the same long word is never tinier here than it is there.
 */
private val HEADWORD_FLOOR = 19.sp

/**
 * The one card shadow — soft and low, so the card LIFTS rather than casting a box.
 *
 * An elevation shadow is the platform's, cut for the platform's own depth ladder: tight,
 * dark, and hard at the edge. The canonical one is a wide bloom at 8 % black, dropped six
 * below the card (iOS `dlCardShadow`), and it is drawn here rather than asked for so the
 * two cuts lift their cards the same amount.
 */
private val CARD_SHADOW = Shadow(
    radius = 16.dp,
    color = Color.Black,
    offset = DpOffset(0.dp, 6.dp),
    alpha = 0.08f,
)
