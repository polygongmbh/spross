package net.spross.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import net.spross.app.Chrome
import net.spross.app.countLine
import net.spross.kern.box.ActivityDay
import net.spross.kern.box.StreakHealth

/** The gutter between two columns; a joined run rules straight across it. */
private val GUTTER = 6.dp

/** How thick the run underline is drawn. */
private val RUN_THICKNESS = 2.5.dp

/**
 * The fortnight of practice, one column per day: how much was answered, and which days
 * the streak rule counts.
 *
 * [days] is kern's own walk ([net.spross.kern.box.streakWindow], held on
 * `AppModel.activityWindow`), oldest day first and today last, and [streakDays] is
 * `BoxStatistics.streak`: the strip NEVER counts a run of its own, or the flame in the
 * header and the underline below could disagree. [health] grades that flame the same way.
 * The arithmetic is [ActivityBars]; this file only draws it.
 *
 * Bars, underline and weekday letters are three rows sharing one weighting, so a column's
 * three parts line up without any of them being told where it is.
 */
@Composable
fun ActivityStrip(
    days: List<ActivityDay>,
    streakDays: Int,
    health: StreakHealth,
    chrome: Chrome,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val bars = remember(days) { ActivityBars.of(days) }
    val palette = Dl.colors
    val label = remember(bars, streakDays, chrome) {
        val activity = chrome.activityDays.format(ActivityBars.activeDays(bars))
        if (streakDays > 0) {
            "$activity. ${countLine(chrome.streakDaysOne, chrome.streakDays, streakDays)}"
        } else {
            activity
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().panel().semantics(mergeDescendants = true) {
            contentDescription = label
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(DlSpace.l),
            verticalArrangement = Arrangement.spacedBy(DlSpace.m),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    chrome.last14Days,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (streakDays > 0) {
                    val unit = if (streakDays == 1) chrome.dayOne else chrome.dayMany
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DlSpace.xs),
                    ) {
                        StreakFlame(health, MaterialTheme.typography.bodySmall)
                        Text(
                            "$streakDays $unit",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.accent,
                            maxLines = 1,
                        )
                    }
                }
            }
            // why: one label for the whole strip — fourteen columns each announcing a
            // date and a count is a minute of speech for a picture of a fortnight.
            Column(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                BarRow(bars, palette)
                RunRule(bars, palette)
                WeekdayRow(bars, locale, palette)
            }
        }
    }
}

@Composable
private fun BarRow(bars: List<ActivityBar>, palette: DlColors) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ActivityBars.MAX_HEIGHT_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(GUTTER),
        verticalAlignment = Alignment.Bottom,
    ) {
        val shape = RoundedCornerShape(3.dp)
        bars.forEach { bar ->
            val column = Modifier.weight(1f).height(bar.heightDp.dp)
            val hue = if (bar.isToday) palette.accent else palette.success
            when {
                bar.reviews > 0 -> Box(column.background(hue.copy(alpha = bar.fillOpacity), shape))
                // why: an empty today reads as "nothing yet", not as a gap — an outline
                // keeps the column present without claiming a review.
                bar.isEmptyToday -> Box(column.border(1.5.dp, palette.accent.copy(alpha = 0.5f), shape))
                else -> Box(column.background(palette.separator, shape))
            }
        }
    }
}

/**
 * The runs under the bars, drawn as one rule per stretch rather than one tick per column:
 * a canvas spanning the whole row can cross the gutters, which a weighted child cannot do
 * without taking the width back out of its neighbor.
 */
@Composable
private fun RunRule(bars: List<ActivityBar>, palette: DlColors) {
    val current = palette.accent
    val past = palette.success
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(top = DlSpace.xs)
            .height(RUN_THICKNESS),
    ) {
        val gutter = GUTTER.toPx()
        val width = (size.width - gutter * (bars.size - 1)) / bars.size
        val radius = CornerRadius(size.height / 2f)
        var index = 0
        while (index < bars.size) {
            val run = bars[index].run
            if (run == StripRun.None) {
                index += 1
                continue
            }
            var last = index
            while (last + 1 < bars.size && bars[last + 1].run == run) last += 1
            val left = index * (width + gutter)
            val right = last * (width + gutter) + width
            drawRoundRect(
                color = if (run == StripRun.Current) current else past,
                topLeft = Offset(left, 0f),
                size = Size(right - left, size.height),
                cornerRadius = radius,
            )
            index = last + 1
        }
    }
}

@Composable
private fun WeekdayRow(bars: List<ActivityBar>, locale: Locale, palette: DlColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = DlSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(GUTTER),
    ) {
        bars.forEach { bar ->
            Text(
                weekdayLetter(bar.dayStartEpochMillis, locale),
                style = MaterialTheme.typography.labelSmall,
                color = if (bar.isToday) palette.accent else palette.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The day's letter, read off kern's own local midnight — never a calendar walk of this
 * side's, which would drift from the window the streak was counted over.
 */
private fun weekdayLetter(dayStartEpochMillis: Long, locale: Locale): String =
    Instant.ofEpochMilli(dayStartEpochMillis)
        .atZone(ZoneId.systemDefault())
        .dayOfWeek
        .getDisplayName(TextStyle.NARROW, locale)
