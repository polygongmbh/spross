package net.spross.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextStyle
import net.spross.kern.box.StreakHealth

/**
 * The run's mark at the grade today has earned it — the one flame all three surfaces read,
 * so no two of them can say different things about one day.
 *
 * 🔥 is multi-color artwork, so the grade is worn as light and COLOR rather than as a second
 * shape; the grade itself is [StreakHealth]'s, and this only dresses it.
 */
@Composable
fun StreakFlame(health: StreakHealth, style: TextStyle, modifier: Modifier = Modifier) {
    val alpha = health.flameOpacity.toFloat()
    val saturation = health.flameSaturation.toFloat()
    Text(
        "🔥",
        style = style,
        modifier = modifier.alpha(alpha)
            .then(if (saturation < 1f) Modifier.withSaturation(saturation) else Modifier),
    )
}

/** A color emoji takes no tint, so the color has to be taken out of it instead. */
private fun saturatedPaint(amount: Float) = Paint().apply {
    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(amount) })
}

/**
 * Compose has no grayscale modifier for text: the content is drawn into a layer of its own
 * and that layer's paint drains the color on the way out.
 */
private fun Modifier.withSaturation(amount: Float): Modifier = drawWithContent {
    val paint = saturatedPaint(amount)
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
        drawContent()
        canvas.restore()
    }
}
