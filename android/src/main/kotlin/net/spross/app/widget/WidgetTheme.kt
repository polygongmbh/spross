package net.spross.app.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.color.ColorProvider as dayNight
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import net.spross.app.ui.DlColors
import net.spross.app.ui.DlDark
import net.spross.app.ui.DlLight
import net.spross.app.ui.articleTint

/**
 * The tile's colors, read off the app's own table rather than copied: `DlLight`/`DlDark`
 * in `ui/Theme.kt` are plain values with no composition behind them, so a Glance surface
 * can hold BOTH columns and let the host pick the one the phone is in. (The iOS widget
 * target keeps a copy of the same table only because an extension links neither the
 * design tokens nor Kotlin.)
 */
object WidgetColors {

    val background = pair { it.surface }
    val textPrimary = pair { it.textPrimary }
    val textSecondary = pair { it.textSecondary }
    val accent = pair { it.accent }
    val separator = pair { it.separator }

    /** A day's bar in the header strip: the run's own hue at the day's own weight. */
    fun bar(isToday: Boolean, opacity: Float) =
        washed({ if (isToday) it.accent else it.success }, opacity)

    /**
     * The hue an article wears, both columns at once. Which article marks which gender is
     * kern's ([net.spross.app.ui.articleTint]); null where the box names no gender.
     */
    fun article(article: String?): ColorProvider? {
        val day = DlLight.articleTint(article) ?: return null
        val night = DlDark.articleTint(article) ?: return null
        return dayNight(day = day, night = night)
    }

    private fun pair(pick: (DlColors) -> Color) =
        dayNight(day = pick(DlLight), night = pick(DlDark))

    private fun washed(pick: (DlColors) -> Color, alpha: Float) = dayNight(
        day = pick(DlLight).copy(alpha = alpha).compositeOver(DlLight.surface),
        night = pick(DlDark).copy(alpha = alpha).compositeOver(DlDark.surface),
    )
}

/**
 * The type the tile is set in.
 *
 * Its own small ramp rather than the app's: Glance draws through RemoteViews, which
 * carries no custom typeface and no shrink-to-fit, so the sizes are picked for the widths
 * a tile actually has. Nothing longer than `WidgetSnapshotBuilder.MAX_TEXT_CHARS` ever
 * reaches a row, which is what keeps one line enough.
 */
object WidgetType {
    val hero = TextStyle(WidgetColors.textPrimary, 20.sp, FontWeight.Bold)
    val word = TextStyle(WidgetColors.textPrimary, 16.sp, FontWeight.Medium)
    val meaning = TextStyle(WidgetColors.textSecondary, 14.sp)
    val meaningSmall = TextStyle(WidgetColors.textSecondary, 13.sp)
    val stat = TextStyle(WidgetColors.accent, 13.sp, FontWeight.Medium)

    fun article(color: ColorProvider, size: TextUnit) =
        TextStyle(color, size, FontWeight.Medium)
}
