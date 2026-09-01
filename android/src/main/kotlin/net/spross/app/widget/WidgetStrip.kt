package net.spross.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import kotlin.math.max
import kotlin.math.roundToInt
import net.spross.app.Chrome
import net.spross.app.ui.ActivityBar
import net.spross.app.ui.ActivityBars
import net.spross.app.ui.DlColors
import net.spross.app.ui.DlDark
import net.spross.app.ui.DlLight
import net.spross.kern.box.ActivityDay

/** The tallest a bar grows here — a header's worth, not the app strip's 52. */
private val HEIGHT = 16.dp

/** A day with reviews is never shorter than this; a day with none is a rule, not a gap. */
private val MIN_BAR = 3.dp
private val STUB = 1.5.dp

private val BAR_WIDTH = 3.dp
private val GUTTER = 1.5.dp

/**
 * The fortnight of practice in the header, one bar per day.
 *
 * The arithmetic is the app's own ([ActivityBars]) so the two strips agree on what a bar
 * height means — the √-scale in particular, which keeps one huge day from flattening the
 * rest. What this cut drops is the weekday letters and the run underline: both are
 * illegible beside a caption-height flame, and the run the flame counts is the header's
 * own business anyway.
 *
 * It is ONE image rather than fourteen views: a Glance container is translated into a
 * generated RemoteViews layout that holds at most ten children, and a fifteenth is not
 * squeezed but dropped — a fortnight drawn as views silently loses half its days.
 */
@Composable
fun ActivityStrip(days: List<ActivityDay>, chrome: Chrome) {
    if (days.isEmpty()) return
    val context = LocalContext.current
    val bars = remember(days) { ActivityBars.of(days) }
    val bitmap = remember(bars, context.isNight) { render(bars, context) }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        // why: one label for the strip — fourteen bars each announcing a date and a count
        // is a minute of speech for a picture of a fortnight.
        modifier = GlanceModifier
            .width(BAR_WIDTH * bars.size + GUTTER * (bars.size - 1))
            .height(HEIGHT)
            .semantics {
                contentDescription = chrome.a11yCountActivity14Days.format(ActivityBars.activeDays(bars))
            },
    )
}

/**
 * The bars painted at the host's pixel density.
 *
 * A raster carries one scheme rather than both, so the column is picked here instead of
 * being left to a `ColorProvider`: the tile redraws on every persist and every update
 * period, which is when a phone that changed scheme picks the other column up.
 */
private fun render(bars: List<ActivityBar>, context: Context): Bitmap {
    val palette = if (context.isNight) DlDark else DlLight
    val density = context.resources.displayMetrics.density
    val barPx = BAR_WIDTH.value * density
    val gutterPx = GUTTER.value * density
    val heightPx = HEIGHT.value * density
    val bitmap = Bitmap.createBitmap(
        (barPx * bars.size + gutterPx * (bars.size - 1)).roundToInt(),
        heightPx.roundToInt(),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = barPx / 2f
    bars.forEachIndexed { index, bar ->
        paint.color = fill(bar, palette).toArgb()
        val left = index * (barPx + gutterPx)
        val top = heightPx - barHeight(bar) * density
        canvas.drawRoundRect(left, top, left + barPx, heightPx, radius, radius, paint)
    }
    return bitmap
}

/** Which column of the table the host is asking for. */
private val Context.isNight: Boolean
    get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

private fun barHeight(bar: ActivityBar): Float =
    if (bar.reviews > 0) max(MIN_BAR.value, HEIGHT.value * bar.scaled) else STUB.value

/** Today takes the accent, every other worked day the forest green, an empty day the rule. */
private fun fill(bar: ActivityBar, palette: DlColors) = when {
    bar.reviews == 0 -> palette.separator
    bar.isToday -> palette.accent.copy(alpha = bar.fillOpacity)
    else -> palette.success.copy(alpha = bar.fillOpacity)
}
