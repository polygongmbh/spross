package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import net.spross.kern.model.Language
import net.spross.kern.model.Rating
import net.spross.kern.model.Realization
import net.spross.kern.session.AnswerTone

/** Answer-colored progress bar: one segment per answer, grey track for the rest. */
@Composable
fun SegmentsBar(segments: List<AnswerTone>, remaining: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEach { tone ->
            val color = when (tone) {
                AnswerTone.Right -> ToneRight
                AnswerTone.Tough -> ToneTough
                AnswerTone.Wrong -> ToneWrong
            }
            Box(Modifier.weight(1f).height(6.dp).background(color, RoundedCornerShape(3.dp)))
        }
        repeat(remaining) {
            Box(Modifier.weight(1f).height(6.dp).background(TrackGrey, RoundedCornerShape(3.dp)))
        }
    }
}

/** Again/Hard/Good/Easy self-grade row (recognize + produce fallback). */
@Composable
fun RatingButtons(chrome: Chrome, onRate: (Rating) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RatingButton(chrome.again, ToneWrong, Modifier.weight(1f)) { onRate(Rating.Again) }
        RatingButton(chrome.hard, ToneTough, Modifier.weight(1f)) { onRate(Rating.Hard) }
        RatingButton(chrome.good, ToneRight, Modifier.weight(1f)) { onRate(Rating.Good) }
        RatingButton(chrome.easy, Color(0xFF3B6FCB), Modifier.weight(1f)) { onRate(Rating.Easy) }
    }
}

@Composable
private fun RatingButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
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
fun articleColoredText(realization: Realization): AnnotatedString {
    val article = CardDisplay.article(realization) ?: return AnnotatedString(realization.text)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = articleTint(article) ?: Color.Unspecified)) { append(article) }
        append(" ${realization.text}")
    }
}

/**
 * Target-side reveal: colored text, plural line, synonym family, note.
 *
 * [alsoShown] names forms of this word standing ELSEWHERE on the screen — a rotated
 * recognition prompt, say. The citation form is always one of them, since this very
 * composable draws it.
 */
@Composable
fun TargetReveal(
    target: Realization,
    chrome: Chrome,
    modifier: Modifier = Modifier,
    pronounce: (() -> Unit)? = null,
    alsoShown: List<String> = emptyList(),
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            localizedTarget(articleColoredText(target), target.lang),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.pronounceOnTap(pronounce, chrome),
        )
        CardDisplay.pluralLine(target, chrome)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CardDisplay.alsoLine(target, chrome, alsoShown + target.text)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        target.note?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}
