package net.spross.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import net.spross.kern.box.AreaStatistics
import net.spross.kern.design.AreaBar
import net.spross.kern.design.AreaStretch
import net.spross.kern.design.SegmentsBar as KernSegmentsBar
import net.spross.kern.session.AnswerOutcome

/**
 * Answer-colored progress bar: one segment per answer, a recessed track for the rest.
 *
 * ONE capsule carrying hairline-parted segments, never a row of loose dots — the round is
 * a single stretch of work, and the bar is what says how much of it is behind the learner.
 * The unanswered remainder is one undivided run, so a long round does not dissolve into
 * specks; the window of answers drawn and the parting are kern's [KernSegmentsBar].
 *
 * The brick is the AGGREGATE's alone — this bar is the only place a wrong answer is shown
 * as one, and no card ever repeats it back at the learner.
 */
@Composable
fun SegmentsBar(
    segments: List<AnswerOutcome>,
    remaining: Int,
    modifier: Modifier = Modifier,
    /** Running down rather than across, for a run's chrome standing on its side. */
    vertical: Boolean = false,
) {
    val bar = KernSegmentsBar(segments.size, remaining)
    if (vertical) {
        Column(
            modifier = modifier.fillMaxHeight().width(10.dp)
                .clip(CircleShape).background(Theme.colors.separator)
                .clearAndSetSemantics { },
            verticalArrangement = Arrangement.spacedBy(bar.gap.dp),
        ) {
            Segments(segments, bar) { weight -> Modifier.weight(weight).fillMaxWidth() }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().height(10.dp)
                .clip(CircleShape).background(Theme.colors.separator)
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(bar.gap.dp),
        ) {
            Segments(segments, bar) { weight -> Modifier.weight(weight).fillMaxHeight() }
        }
    }
}

/** The answered segments and the unanswered rest, each sized along the bar by [cell]. */
@Composable
private fun Segments(segments: List<AnswerOutcome>, bar: KernSegmentsBar, cell: (Float) -> Modifier) {
    val palette = Theme.colors
    segments.takeLast(bar.shown).forEachIndexed { offset, tone ->
        val index = bar.firstShown + offset
        val color = when (tone) {
            AnswerOutcome.Right -> palette.success
            AnswerOutcome.Almost -> palette.amber
            AnswerOutcome.Wrong -> palette.wrong
        }
        // why: keyed on the run-wide index, so a segment already on screen holds its settled
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
            Box(cell(grown.value).background(eased))
        }
    }
    if (bar.remaining > 0) {
        Box(cell(bar.remaining.toFloat()).background(palette.separator))
    }
}

/**
 * An area's progress bar: kern's [AreaBar] — its stretches, their order and fades, and how much
 * of the track they fill — drawn in the stage colors.
 * No amber stretch: amber stays a badge-only color, distinguishing Fresh/Lapsed from Growing
 * at the per-card level ([StageBadge]) without the bar needing that fine a grain.
 */
@Composable
fun AreaProgressBar(stats: AreaStatistics, modifier: Modifier = Modifier) {
    val palette = Theme.colors
    val shape = RoundedCornerShape(percent = 50)
    // why: each share eases to a new count instead of jumping to it.
    val settled by animateFloatAsState(stats.allSettled.toFloat(), turnTween(), label = "areaSettled")
    val growing by animateFloatAsState(stats.allGrowing.toFloat(), turnTween(), label = "areaGrowing")
    val queued by animateFloatAsState(stats.queued.toFloat(), turnTween(), label = "areaQueued")
    val bar = AreaBar(settled.toDouble(), growing.toDouble(), queued.toDouble(), stats.progressTotal)
    // why: the track is the shelf's untouched rest — without it the stretches would
    // end in the card's own background and the bar would read as full.
    Box(modifier.fillMaxWidth().height(6.dp).background(palette.separator, shape)) {
        if (bar.fill > 0.0) {
            val stops = bar.stops.map { stop ->
                val color = when (stop.stretch) {
                    AreaStretch.Settled -> palette.settled
                    AreaStretch.Growing -> palette.success
                    AreaStretch.Queued -> palette.accent
                }
                stop.location.toFloat() to color
            }
            Box(
                Modifier.fillMaxWidth(bar.fill.toFloat()).fillMaxHeight()
                    .background(Brush.horizontalGradient(*stops.toTypedArray()), shape),
            )
        }
    }
}
