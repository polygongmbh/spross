package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.CardDisplay
import net.spross.app.Chrome
import net.spross.kern.model.Realization

/**
 * The words a card is made of: the headword the whole card is about, the speaker beside
 * it, the reveal it grows, and the small print under that.
 *
 * They compose INSIDE a face ([CardFace]) and know nothing about it, so a screen that is
 * not a card — a reference row, a summary — can say a word in the card's voice without
 * wearing a card.
 */

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
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs, Alignment.CenterHorizontally),
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
                tint = Theme.colors.teal,
                modifier = Modifier.size(SPEAKER_GLYPH),
            )
        }
    }
}

/**
 * THE headword of a card — the one word the whole card is about, on either side of it.
 *
 * It steps down to fit rather than breaking a word in half. The line bound is what makes
 * the step-down bite: with lines unbounded, a word wider than the card simply wraps
 * mid-word ("Sprach" / "e") and the paragraph reports no overflow at all, so the step
 * search would leave it at full size. A single token gets ONE line, because the only wrap
 * available to it IS a broken word; anything with a space keeps a second line and breaks
 * there. The verdict labels and the Sprossen chips already wear the same pair.
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

/**
 * Target-side reveal: the word in the accent, its article in its own tint.
 *
 * The accent is the REVEAL's, not the target language's — a card is styled by role, so
 * the same word is neutral ink where it stands as the prompt. This is a headword and
 * nothing else: the grammar and the family it carries are [targetLines], handed to the
 * card so they close it at full width instead of wrapping in the picture's row.
 */
@Composable
fun TargetReveal(
    target: Realization,
    chrome: Chrome,
    modifier: Modifier = Modifier,
    pronounce: (() -> Unit)? = null,
) {
    SpokenWord(pronounce, chrome, modifier) {
        Headword(
            localizedTarget(Theme.colors.articleColoredText(target), target.lang),
            color = Theme.colors.accent,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * The small print a target word owes its reveal: the plural, then the synonym family.
 * Grammar renders here and nowhere else, because it is the target side's alone.
 *
 * [alsoShown] names forms of this word standing ELSEWHERE on the screen — a rotated
 * recognition prompt, say. The citation form is always one of them, since the reveal
 * draws it.
 */
fun targetLines(
    target: Realization,
    chrome: Chrome,
    alsoShown: List<String> = emptyList(),
): List<String> = listOfNotNull(
    CardDisplay.pluralLine(target, chrome),
    CardDisplay.alsoLine(target, chrome, alsoShown + target.text),
)

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
        modifier = modifier.fillMaxWidth().padding(top = Theme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(44.dp).height(2.dp)
                .background(Theme.colors.separator, RoundedCornerShape(1.dp))
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

/**
 * Where a shrinking headword stops. iOS bottoms out at 0.85 of a 22 pt headword; this
 * lands no smaller, so the same long word is never tinier here than it is there.
 */
private val HEADWORD_FLOOR = 19.sp
