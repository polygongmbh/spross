package net.spross.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.util.concurrent.ConcurrentHashMap
import net.spross.kern.box.StreakHealth

/**
 * The run's mark, rasterized.
 *
 * The flame is the 🔥 emoji, here as everywhere else in the app, and the grade today has
 * earned it is carried by COLOR rather than by shape: full color while the day is
 * answered, half-cooled while a miss would only spend the run's one bridge — a flame asking
 * for renewal without being faded out — and cold — drained of all color — where a miss
 * would end it, which is the loud one.
 *
 * It is a bitmap because Glance draws through RemoteViews, where a `Text` takes neither an
 * alpha nor a color filter: the emoji is multi-color artwork the platform paints itself,
 * and the only place a saturation matrix can reach it is a canvas of our own.
 * The rule the grades come from is [StreakHealth]; this only dresses it.
 */
object WidgetFlame {

    private const val GLYPH = "🔥"

    /**
     * Emoji sit inside their em box with room above and below, so the glyph is set larger
     * than the square it is drawn into or it arrives visibly smaller than the text beside it.
     */
    private const val FILL = 0.92f

    // why: one bitmap per (grade, size) — a widget redraws far more often than the four
    // grades change, and rasterizing an emoji per recomposition is work for nothing.
    private val cache = ConcurrentHashMap<Pair<StreakHealth, Int>, Bitmap>()

    fun bitmap(health: StreakHealth, sizePx: Int): Bitmap =
        cache.getOrPut(health to sizePx) { render(health, sizePx) }

    private fun render(health: StreakHealth, sizePx: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx * FILL
            textAlign = Paint.Align.CENTER
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply { setSaturation(saturation(health)) },
            )
            alpha = alpha(health)
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val metrics = paint.fontMetrics
        Canvas(bitmap).drawText(
            GLYPH,
            sizePx / 2f,
            sizePx / 2f - (metrics.ascent + metrics.descent) / 2f,
            paint,
        )
        return bitmap
    }

    /** A run in danger goes cold rather than dim — gray is the one state color cannot say. */
    private fun saturation(health: StreakHealth): Float = when (health) {
        StreakHealth.Earned -> 1f
        StreakHealth.Bridgeable -> 0.5f
        StreakHealth.Ending, StreakHealth.None -> 0f
    }

    private fun alpha(health: StreakHealth): Int = when (health) {
        StreakHealth.Earned -> 255
        StreakHealth.Bridgeable -> 230
        StreakHealth.Ending -> 235
        // No run to protect: the mark stays on the line as a restart nudge, but faint,
        // and the stats line drops the count beside it rather than printing a zero.
        StreakHealth.None -> 90
    }
}
