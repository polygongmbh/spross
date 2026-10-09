package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.spross.app.CardDisplay
import net.spross.app.Chrome
import net.spross.kern.box.ActiveStage
import net.spross.kern.box.CardRowState
import net.spross.kern.box.swatch
import net.spross.kern.model.FormDimension
import net.spross.kern.model.FormTag
import net.spross.kern.model.Language
import net.spross.kern.model.Realization
import net.spross.kern.model.articledForm

/**
 * The one tinted capsule the app's standings wear: a word (never a color alone) over its
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
            .background(Theme.colors.wash(color), RoundedCornerShape(percent = 50))
            .padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.xs + 1.dp),
    )
}

/** Which form a word stands for (`♀`, `Pl.`), tinted by the gender it names where it names one. */
@Composable
fun FormBadge(tag: FormTag, chrome: Chrome, modifier: Modifier = Modifier) {
    val color = when (tag.values[FormDimension.Gender]) {
        "f" -> Theme.colors.die
        "m" -> Theme.colors.der
        "n" -> Theme.colors.das
        else -> Theme.colors.textSecondary
    }
    // why: ♀/♂/⚲ are glyphs TalkBack either skips or reads as a symbol name; the badge says
    // what it marks instead, which is the only way the grammar reaches a spoken card.
    Pill(CardDisplay.marker(tag, chrome), color, modifier.semantics { contentDescription = CardDisplay.markerSpoken(tag, chrome) })
}

/**
 * Where one card stands on the growth ladder: fresh → growing → settled, or lapsed.
 *
 * Four labeled stages, three colors. [CardRowState.Standing.stage] — kern's own
 * ladder — decides the top one directly, exactly as the shelf's own tally does, so a
 * row's seal never claims a word the shelf above does not also count: Settled is a
 * further stage, well past Growing, which is why a Growing card reads its own mark
 * instead of borrowing the seal. Fresh and Lapsed share amber and a glyph, and only the
 * word tells them apart. Settled is the one stage that carries no word at all — the
 * seal alone already says "done".
 *
 * The color comes from [swatch] rather than being picked here, so this badge and the
 * shelf's own [AreaProgressBar] can never disagree about the same stage. A card with
 * nothing behind it gets no badge at all; that absence is what says "new"
 * (kern `CardRowState.Plain`), so this is never asked about one.
 */
@Composable
fun StageBadge(standing: CardRowState.Standing, chrome: Chrome) {
    val color = standing.swatch.tint()
    when (standing.stage) {
        // Settled needs no word: a seal already reads as "done" on its own, where
        // Fresh/Shaky/Growing would be ambiguous glyphs without one.
        ActiveStage.Settled -> Pill(
            SEAL, color,
            modifier = Modifier.semantics { contentDescription = chrome.a11yBoxStageSettled },
        )
        ActiveStage.Growing -> Pill("$HERB ${chrome.boxStageGrowing}", color)
        ActiveStage.Lapsed -> Pill("$LEAF ${chrome.boxStageLapsed}", color)
        ActiveStage.Fresh -> Pill("$LEAF ${chrome.boxStageFresh}", color)
    }
}

/** The settled mark; the same glyph the area's own count row leads with. */
const val SEAL = "✔"

/** …and the one for a word still on its way in. */
const val LEAF = "🌱"

/** …and the one for a word that has cleared the growing bar. */
const val HERB = "🌿"

/** Phrases waiting on their components — the only count that is not about a schedule. */
const val LOCK = "🔒"

/**
 * Tap-to-replay on a headword. Deliberately not a button: TalkBack must keep reading
 * the word as the word it is, so the replay is a custom ACTION on the text and the
 * click carries no indication — no ripple over the hero of the card.
 *
 * [minHeight] is applied whether or not the word can be heard, so a card measures the
 * same between reviews when the synonym rotation lands on an unrecorded form;
 * only the gesture and its action are conditional.
 * A line of reading matter passes 0.dp:
 * the row is already as tall as the text it carries,
 * and a 48 dp floor per row would set the height of the whole table.
 */
@Composable
fun Modifier.pronounceOnTap(
    pronounce: (() -> Unit)?,
    chrome: Chrome,
    minHeight: Dp = 48.dp,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val tappable = if (pronounce == null) {
        Modifier
    } else {
        Modifier
            .semantics {
                customActions = listOf(CustomAccessibilityAction(chrome.a11yActionPronounce) {
                    pronounce()
                    true
                })
            }
            .clickable(interactionSource = interaction, indication = null, onClick = pronounce)
    }
    return this.sizeIn(minHeight = minHeight).then(tappable)
}

/** The speaker glyph text carries: beside a headword, and at the head of the hint line. */
internal val SPEAKER_GLYPH = 18.dp

/**
 * The gesture a reference page discloses ONCE, under its heading, rather than on every row.
 *
 * A reference page is read by running down it,
 * so the CONTENT is the target and no row carries a speaker of its own — this line says so.
 * The credits screen is the one list whose rows each carry an icon, and it is not a speaker:
 * the row plays its recording, and a trailing link opens the file's Commons page.
 * The numbers table and the atlas draw the same one (iOS `ReferenceTapHint`),
 * and only where the device can actually answer.
 *
 * [text] defaults to the reference pages' own wording; the box names its rows "words"
 * rather than a table's, so it passes its own.
 */
@Composable
fun TapToHearHint(chrome: Chrome, text: String = chrome.trainerReferenceTapToHear) {
    Row(
        // Every row below offers hearing as its own named action, so spoken this line is
        // the same thing said a second time.
        modifier = Modifier.clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        Icon(
            SprossIcons.Speaker,
            contentDescription = null,
            tint = Theme.colors.textSecondary,
            modifier = Modifier.size(SPEAKER_GLYPH),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Theme.colors.textSecondary,
        )
    }
}

/**
 * The quiet line a screen pauses on: the note under a reveal, what became of an answer,
 * the question the verdicts answer. Italic and secondary, never a heading — it explains
 * what is already on screen, and every such line on either phone wears this one face
 * (iOS `pauseLine`).
 *
 * Body size, not caption: a post-reveal line is meant to be READ, and secondary text a
 * step smaller is where legibility broke on the iOS card.
 */
@Composable
fun PauseLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
        color = Theme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
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
 * article. How the article joins its word — "l'acqua" onto the noun, "el frigorífico"
 * with a space — is kern's [articledForm]; the tinted span is the article and its join.
 * Genderless targets render exactly the text and nothing else.
 */
fun ThemeColors.articleColoredText(realization: Realization): AnnotatedString =
    articleColoredText(CardDisplay.article(realization), realization.text, realization.lang)

/** [text] behind [article] in its gender's tint; no article renders the text alone. */
fun ThemeColors.articleColoredText(article: String?, text: String, lang: Language?): AnnotatedString {
    if (article == null) return AnnotatedString(text)
    val shown = articledForm(article, text)
    val head = article.trim()
    return buildAnnotatedString {
        withStyle(SpanStyle(color = articleTint(article, lang) ?: Color.Unspecified)) {
            append(head)
        }
        append(shown.removePrefix(head))
    }
}
