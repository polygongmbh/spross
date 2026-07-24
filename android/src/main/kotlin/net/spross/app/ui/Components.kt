package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import net.spross.app.AnswerTone
import net.spross.app.CardDisplay
import net.spross.app.Chrome
import net.spross.kern.model.Rating
import net.spross.kern.model.Realization

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

/** Leading article rendered in its color when the grammar carries a gender. */
fun articleColoredText(realization: Realization): AnnotatedString {
    val tint = articleTint(CardDisplay.gender(realization))
    val firstSpace = realization.text.indexOf(' ')
    return buildAnnotatedString {
        if (tint != null && firstSpace > 0) {
            withStyle(SpanStyle(color = tint)) { append(realization.text.take(firstSpace)) }
            append(realization.text.substring(firstSpace))
        } else {
            append(realization.text)
        }
    }
}

/** Target-side reveal: colored text, plural line, synonym family, note. */
@Composable
fun TargetReveal(target: Realization, chrome: Chrome, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(articleColoredText(target), style = MaterialTheme.typography.headlineMedium)
        CardDisplay.pluralLine(target, chrome)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CardDisplay.alsoLine(target, chrome)?.let {
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
