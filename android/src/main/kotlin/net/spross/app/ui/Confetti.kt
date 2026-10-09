package net.spross.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import net.spross.app.TrainerStanding
import net.spross.kern.design.ConfettiFrame
import net.spross.kern.design.ConfettiInk

/**
 * Paper confetti falling across a whole screen, drawn as ONE canvas rather than as a
 * composable per piece: kern's [ConfettiFrame] places every piece; this runs the clock,
 * inks its pieces in the theme's colors and scales its dp to px.
 *
 * A wave is thrown on entering composition and again whenever [run] changes; waves ADD,
 * so a replay lands in whatever is still in the air. With animations switched off in the
 * system settings nothing is thrown.
 */
@Composable
fun Confetti(run: Int = 0, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    val palette = remember(colors) { ConfettiFrame.INKS.map { color(it, colors) } }
    val frame = remember { ConfettiFrame() }
    val waves = remember { mutableStateListOf<Wave>() }
    var nextWave by remember { mutableIntStateOf(0) }
    var clock by remember { mutableLongStateOf(0L) }

    LaunchedEffect(run) {
        // why: the frame clock's duration scale is the system animator scale — the switch
        // that also stands the summary's tree up finished — so 0 throws nothing.
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
        waves += Wave(nextWave++, withFrameNanos { it })
        if (waves.size > ConfettiFrame.MAX_WAVES) waves.removeAt(0)
    }
    val airborne = waves.isNotEmpty()
    // why: frames are asked for only while a wave is in the air, and each wave retires
    // itself once its last piece has left the screen.
    LaunchedEffect(airborne) {
        while (waves.isNotEmpty()) {
            withFrameNanos { now ->
                clock = now
                waves.removeAll { ConfettiFrame.retired((now - it.start) / 1e9) }
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val now = clock
        for (wave in waves) {
            val elapsed = (now - wave.start) / 1e9
            drawWave(frame, wave.id, elapsed, ConfettiFrame.fade(elapsed).toFloat(), palette)
        }
    }
}

/** One handful, identified so its retirement removes exactly it. */
private class Wave(val id: Int, val start: Long)

private fun color(ink: ConfettiInk, colors: ThemeColors): Color = when (ink) {
    ConfettiInk.ACCENT -> colors.accent
    ConfettiInk.TEAL -> colors.teal
    ConfettiInk.SUCCESS -> colors.success
    ConfettiInk.AMBER -> colors.amber
    ConfettiInk.DER -> colors.der
    ConfettiInk.DIE -> colors.die
    ConfettiInk.DAS -> colors.das
}

private fun DrawScope.drawWave(frame: ConfettiFrame, wave: Int, elapsed: Double, fade: Float, palette: List<Color>) {
    val d = density
    val count = frame.fill(wave, elapsed, (size.width / d).toDouble(), (size.height / d).toDouble())
    val v = frame.values
    for (i in 0 until count) {
        val o = i * ConfettiFrame.STRIDE
        val w = (v[o + ConfettiFrame.WIDTH] * d).toFloat()
        val h = (v[o + ConfettiFrame.HEIGHT] * d).toFloat()
        val topLeft = Offset(-w / 2, -h / 2)
        val color = palette[v[o + ConfettiFrame.INK].toInt()]
        val alpha = fade * v[o + ConfettiFrame.ALPHA].toFloat()
        withTransform({
            translate((v[o + ConfettiFrame.X] * d).toFloat(), (v[o + ConfettiFrame.Y] * d).toFloat())
            rotate((v[o + ConfettiFrame.ROTATION] * 180 / PI).toFloat(), pivot = Offset.Zero)
            scale(v[o + ConfettiFrame.TUMBLE].toFloat(), 1f, pivot = Offset.Zero)
        }) {
            if (v[o + ConfettiFrame.OVAL] > 0) {
                drawOval(color, topLeft, Size(w, h), alpha)
            } else {
                drawRoundRect(color, topLeft, Size(w, h), CornerRadius((ConfettiFrame.CORNER * d).toFloat()), alpha = alpha)
            }
        }
    }
}

/**
 * The rain a celebrated drill close leaves for the screen it lands on — an overview page or,
 * for a scramble, Home. Taken once per close ([TrainerStanding.takeConfetti]), so coming back
 * to the screen later throws nothing.
 */
@Composable
fun ClosedRunConfetti(trainer: TrainerStanding) {
    var run by remember { mutableIntStateOf(0) }
    LaunchedEffect(trainer.confettiDue) { if (trainer.takeConfetti()) run++ }
    // why: absent until the first celebration — [Confetti] throws a wave on entering.
    if (run > 0) Confetti(run)
}
