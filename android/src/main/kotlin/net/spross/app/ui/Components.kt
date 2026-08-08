package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import net.spross.app.CardDisplay
import net.spross.app.Chrome
import net.spross.kern.box.AreaStatistics
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Language
import net.spross.kern.model.Rating
import net.spross.kern.model.Realization
import net.spross.kern.session.AnswerTone

/**
 * Answer-colored progress bar: one segment per answer, a recessed track for the rest.
 *
 * ONE capsule carrying hairline-parted segments, never a row of loose dots — the round is
 * a single stretch of work, and the bar is what says how much of it is behind the learner.
 * The unanswered remainder is one undivided run, so a long round does not dissolve into
 * specks; the parting closes entirely past the count where it stops reading as a gap.
 *
 * The brick is the AGGREGATE's alone — this bar is the only place a wrong answer is shown
 * as one, and no card ever repeats it back at the learner.
 */
@Composable
fun SegmentsBar(segments: List<AnswerTone>, remaining: Int, modifier: Modifier = Modifier) {
    val palette = Dl.colors
    val slots = segments.size + remaining
    Row(
        modifier = modifier.fillMaxWidth().height(10.dp)
            .clip(CircleShape).background(palette.separator),
        horizontalArrangement = Arrangement.spacedBy(if (slots > 40) 0.dp else 1.dp),
    ) {
        segments.forEach { tone ->
            val color = when (tone) {
                AnswerTone.Right -> palette.success
                AnswerTone.Tough -> palette.amber
                AnswerTone.Wrong -> palette.wrong
            }
            Box(Modifier.weight(1f).fillMaxHeight().background(color))
        }
        if (remaining > 0) {
            Box(Modifier.weight(remaining.toFloat()).fillMaxHeight().background(palette.separator))
        }
    }
}

/**
 * The one tinted capsule the app's standings wear: a word (never a colour alone) over its
 * accent's own 14 % wash, so a badge reads the same on a card as on a recessed row.
 */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        modifier = modifier
            .background(Dl.colors.wash(color), RoundedCornerShape(percent = 50))
            .padding(horizontal = DlSpace.m, vertical = DlSpace.xs + 1.dp),
    )
}

/**
 * The ♀ a demoted feminine wears beside its headword — decorative grammar, never graded.
 *
 * A badge rather than a bare glyph: it marks the word without joining it, so the headword
 * is still read (and heard) as the word it is.
 */
@Composable
fun FeminineBadge(modifier: Modifier = Modifier) {
    Pill("♀", Dl.colors.die, modifier)
}

/**
 * Where one card stands on the ladder.
 *
 * The mark follows [consolidated] — kern's stricter bar — and NEVER the phase: a card
 * reaches Review well below it, so a seal keyed to the phase would mark words the shelf
 * above leaves out of its consolidated count, and the row would disagree with the shelf
 * on sight. A card with nothing behind it gets no badge at all; that absence is what
 * says "new" (kern `CardRowState.Plain`), so this is never asked about one.
 */
@Composable
fun PhaseBadge(phase: CardPhase, consolidated: Boolean, chrome: Chrome) {
    val palette = Dl.colors
    val color = when (phase) {
        CardPhase.Learning -> palette.der
        CardPhase.Review -> palette.success
        CardPhase.Relearning -> palette.amber
        CardPhase.New -> palette.textSecondary
    }
    val word = when (phase) {
        CardPhase.Learning -> chrome.phaseLearning
        CardPhase.Review -> chrome.phaseReview
        CardPhase.Relearning -> chrome.phaseRelearning
        CardPhase.New -> chrome.newLabel
    }
    Pill("${if (consolidated) SEAL else LEAF} $word", color)
}

/** The consolidated mark; the same glyph the area's own count row leads with. */
const val SEAL = "✔"

/** …and the one for a word still on its way in. */
const val LEAF = "🌱"

/** Phrases waiting on their components — the only count that is not about a schedule. */
const val LOCK = "🔒"

/**
 * An area's cards as three stretches: consolidated, still learning, never introduced —
 * measured against the area's FULL card count, so the untouched rest of a shelf stays
 * visible instead of a bar that always reads as full.
 *
 * The split and the denominator are the box's rulings ([AreaStatistics]); empty stretches
 * are dropped, and an area with nothing in any of them draws one neutral rule rather than
 * a full amber bar claiming everything is being learnt.
 */
@Composable
fun AreaProgressBar(stats: AreaStatistics, modifier: Modifier = Modifier) {
    val palette = Dl.colors
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier.fillMaxWidth().height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val stretches = listOf(
            stats.consolidated to palette.success,
            stats.learning to palette.amber,
            stats.notIntroduced to palette.separator,
        ).filter { it.first > 0 }
        if (stretches.isEmpty()) {
            Box(Modifier.weight(1f).height(6.dp).background(palette.separator, shape))
            return@Row
        }
        stretches.forEach { (count, color) ->
            Box(Modifier.weight(count.toFloat()).height(6.dp).background(color, shape))
        }
    }
}

/** Again/Hard/Good/Easy self-grade row (recognize + produce fallback). */
@Composable
fun RatingButtons(chrome: Chrome, onRate: (Rating) -> Unit, modifier: Modifier = Modifier) {
    val palette = Dl.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RatingButton(chrome.again, palette.wrong, Modifier.weight(1f)) { onRate(Rating.Again) }
        RatingButton(chrome.hard, palette.amber, Modifier.weight(1f)) { onRate(Rating.Hard) }
        RatingButton(chrome.good, palette.success, Modifier.weight(1f)) { onRate(Rating.Good) }
        // The fourth verdict this platform still offers takes the secondary accent: the
        // article blue is the grammar's, and a rating must never borrow it.
        RatingButton(chrome.easy, palette.teal, Modifier.weight(1f)) { onRate(Rating.Easy) }
    }
}

@Composable
private fun RatingButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        // The ink cut for accent fills, never white: in the dark the accents are pastels
        // and white sinks to about 1.8:1 on them.
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Dl.colors.onColor,
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) {
        Text(label, maxLines = 1)
    }
}

/**
 * Tap-to-replay on a headword. Deliberately not a button: TalkBack must keep reading
 * the word as the word it is, so the replay is a custom ACTION on the text and the
 * click carries no indication — no ripple over the hero of the card.
 *
 * The 48 dp floor is UNCONDITIONAL, so a card measures the same whether or not its
 * word can be heard; only the gesture and its action are conditional. Otherwise a
 * card would change height between reviews because the synonym rotation happened to
 * land on an unrecorded form.
 */
@Composable
fun Modifier.pronounceOnTap(pronounce: (() -> Unit)?, chrome: Chrome): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val tappable = if (pronounce == null) {
        Modifier
    } else {
        Modifier
            .semantics {
                customActions = listOf(CustomAccessibilityAction(chrome.pronounce) {
                    pronounce()
                    true
                })
            }
            .clickable(interactionSource = interaction, indication = null, onClick = pronounce)
    }
    return this.sizeIn(minHeight = 48.dp).then(tappable)
}

/**
 * Target text tagged with the language it is written in, so TalkBack reads a
 * Ukrainian word in a Ukrainian voice rather than the chrome's — the reading a
 * screen-reader user gets in place of the autoplay that is suppressed for them.
 */
fun localizedTarget(text: AnnotatedString, lang: Language): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(localeList = LocaleList(lang))) { append(text) }
    }

fun localizedTarget(text: String, lang: Language): AnnotatedString =
    localizedTarget(AnnotatedString(text), lang)

/**
 * The citation form as one line — "el frigorífico" — with the leading article in its
 * color where the grammar carries a gender.
 *
 * The article comes from `grammar.gender` and is PREPENDED; it is never sliced out of
 * `text`, which carries the bare word in every language. Reading the first word as an
 * article held only because German nouns are one word: es has 32 multi-word nouns, and
 * *pasta de dientes* would have rendered its own head tinted as though *pasta* were an
 * article. Genderless targets render exactly the text and nothing else.
 */
fun DlColors.articleColoredText(realization: Realization): AnnotatedString {
    val article = CardDisplay.article(realization) ?: return AnnotatedString(realization.text)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = articleTint(article) ?: Color.Unspecified)) { append(article) }
        append(" ${realization.text}")
    }
}
