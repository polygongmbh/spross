package net.spross.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.spross.app.Chrome
import net.spross.kern.box.AreaStatistics
import net.spross.kern.session.AnswerOutcome

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
fun SegmentsBar(
    segments: List<AnswerOutcome>,
    remaining: Int,
    chrome: Chrome,
    modifier: Modifier = Modifier,
) {
    val palette = Theme.colors
    val slots = segments.size + remaining
    // why: colored stretches are the whole of what the bar says, and a color says nothing
    // to TalkBack — so it speaks the tally it is drawing, or where the round stands before
    // there is one.
    val spoken = if (segments.isEmpty()) {
        chrome.sessionCardPosition.format(1, slots)
    } else {
        chrome.a11yCountSessionTally.format(
            segments.count { it == AnswerOutcome.Right },
            segments.count { it == AnswerOutcome.Almost },
            segments.count { it == AnswerOutcome.Wrong },
        )
    }
    Row(
        modifier = modifier.fillMaxWidth().height(10.dp)
            .clip(CircleShape).background(palette.separator)
            .semantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(if (slots > 40) 0.dp else 1.dp),
    ) {
        segments.forEachIndexed { index, tone ->
            val color = when (tone) {
                AnswerOutcome.Right -> palette.success
                AnswerOutcome.Almost -> palette.amber
                AnswerOutcome.Wrong -> palette.wrong
            }
            // why: keyed on the index, so a segment already on screen holds its settled
            // weight and color instead of replaying the entrance on every answer that
            // follows it — only the newest slot grows in and eases into its tone.
            key(index) {
                val grown = remember { Animatable(0.001f) }
                var settled by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    settled = true
                    grown.animateTo(1f, turnTween())
                }
                val eased by animateColorAsState(
                    if (settled) color else palette.separator,
                    turnTween(),
                    label = "segmentColor",
                )
                Box(Modifier.weight(grown.value).fillMaxHeight().background(eased))
            }
        }
        if (remaining > 0) {
            Box(Modifier.weight(remaining.toFloat()).fillMaxHeight().background(palette.separator))
        }
    }
}

/**
 * An area's cards as a two-way split (matches the counts row) plus queued: settled,
 * everything else active, then queued-but-unintroduced — measured against the area's
 * FULL card count, so the untouched rest of a shelf stays visible instead of a bar
 * that always reads as full.
 *
 * One continuous capsule whose stretches fade into each other. No amber stretch: amber
 * stays a badge-only color, distinguishing Fresh/Lapsed from Growing at the per-card
 * level ([StageBadge]) without the bar needing that fine a grain.
 * A card never queued at all gets no stretch: the neutral track under them is what the
 * untouched rest of the shelf reads as.
 *
 * The split and the denominator are the box's rulings ([AreaStatistics]); an area with
 * nothing in any of them leaves the track bare rather than drawing a full bar claiming
 * everything is being learned.
 */
@Composable
fun AreaProgressBar(stats: AreaStatistics, modifier: Modifier = Modifier) {
    val palette = Theme.colors
    val shape = RoundedCornerShape(percent = 50)
    // why: each share eases to a new count instead of jumping to it.
    val settled by animateFloatAsState(stats.allSettled.toFloat(), turnTween(), label = "areaSettled")
    val growing by animateFloatAsState(stats.allGrowing.toFloat(), turnTween(), label = "areaGrowing")
    val queued by animateFloatAsState(stats.queued.toFloat(), turnTween(), label = "areaQueued")
    val total = stats.progressTotal.coerceAtLeast(1).toFloat()
    val filled = settled + growing + queued
    // why: the track is the shelf's untouched rest — without it the stretches would
    // end in the card's own background and the bar would read as full.
    Box(modifier.fillMaxWidth().height(6.dp).background(palette.separator, shape)) {
        if (filled > 0f) {
            val stops = blendedStops(
                listOf(settled to palette.settled, growing to palette.success, queued to palette.accent),
                filled, halfBlend = AREA_BLEND * total / filled,
            )
            Box(
                Modifier.fillMaxWidth((filled / total).coerceAtMost(1f)).fillMaxHeight()
                    .background(Brush.horizontalGradient(*stops), shape),
            )
        }
    }
}

/** Half the fade between two stretches, as a share of the whole track. */
private const val AREA_BLEND = 0.01f

/** Each stretch holds its color up to [halfBlend] (a share of [filled]) short of a neighbor. */
private fun blendedStops(stretches: List<Pair<Float, Color>>, filled: Float, halfBlend: Float): Array<Pair<Float, Color>> {
    var start = 0f
    return stretches.filter { it.first > 0f }.flatMap { (count, color) ->
        val from = start / filled
        val to = (start + count) / filled
        start += count
        val inset = minOf(halfBlend, (to - from) / 2)
        listOf(from + inset to color, to - inset to color)
    }.toTypedArray()
}
