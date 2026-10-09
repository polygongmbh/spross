package net.spross.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import net.spross.kern.box.StreakHealth

/**
 * The run's mark, rasterized.
 *
 * The flame is the 🔥 emoji, here as everywhere else in the app, and the grade today has
 * earned it is carried by light and COLOR rather than by shape.
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
                ColorMatrix().apply { setSaturation(health.flameSaturation.toFloat()) },
            )
            alpha = (health.flameOpacity * 255).roundToInt()
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
}
