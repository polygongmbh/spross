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
 * shape: full strength where the day is answered, pale where a miss would only spend the
 * run's one bridge, and drained to gray where it would end the run — a flame gone cold,
 * which is the loud one. The rule itself is [StreakHealth]; this only dresses it.
 */
@Composable
fun StreakFlame(health: StreakHealth, style: TextStyle, modifier: Modifier = Modifier) {
    val alpha = when (health) {
        StreakHealth.Earned -> 1f
        StreakHealth.Bridgeable -> 0.55f
        StreakHealth.Ending -> 0.9f
        StreakHealth.None -> 0.4f
    }
    val cold = health == StreakHealth.Ending || health == StreakHealth.None
    Text(
        "🔥",
        style = style,
        modifier = modifier.alpha(alpha).then(if (cold) Modifier.desaturated() else Modifier),
    )
}

/** Saturation 0 — a color emoji takes no tint, so the color has to be taken out of it. */
private val COLD_PAINT = Paint().apply {
    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
}

/**
 * Compose has no grayscale modifier for text: the content is drawn into a layer of its own
 * and that layer's paint drains the color on the way out.
 */
private fun Modifier.desaturated(): Modifier = drawWithContent {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), COLD_PAINT)
        drawContent()
        canvas.restore()
    }
}
