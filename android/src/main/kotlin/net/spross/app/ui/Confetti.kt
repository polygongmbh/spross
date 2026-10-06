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
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import net.spross.app.TrainerStanding

/**
 * Paper confetti falling across a whole screen, drawn as ONE canvas rather than as a
 * composable per piece — iOS's `ConfettiView`, with its sizes, speeds and timings in dp.
 *
 * Nothing is stored per piece: every property — lane, speed, sway, spin, color — is derived
 * from (index, wave) through a hash, so the pieces are varied but reproducible.
 *
 * A wave is thrown on entering composition and again whenever [run] changes; waves ADD,
 * so a replay lands in whatever is still in the air. With animations switched off in the
 * system settings nothing is thrown.
 */
@Composable
fun Confetti(run: Int = 0, modifier: Modifier = Modifier, pieceCount: Int = 130, emission: Double = 4.0) {
    // Poster palette, minus the wrong-answer brick. Paper, not signal color.
    val colors = Theme.colors
    val palette = remember(colors) {
        listOf(colors.accent, colors.teal, colors.success, colors.amber, colors.der, colors.die, colors.das)
    }
    // Emission window plus the longest fall still to come after it.
    val life = emission + 3.2
    val waves = remember { mutableStateListOf<Wave>() }
    var nextWave by remember { mutableIntStateOf(0) }
    var clock by remember { mutableLongStateOf(0L) }

    LaunchedEffect(run) {
        // why: the frame clock's duration scale is the system animator scale — the switch
        // that also stands the summary's tree up finished — so 0 throws nothing.
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
        waves += Wave(nextWave++, withFrameNanos { it })
        // why: taps can come faster than waves retire; past a few in the air the oldest is
        // the thinnest, so it is the one to drop.
        if (waves.size > 4) waves.removeAt(0)
    }
    val airborne = waves.isNotEmpty()
    // why: frames are asked for only while a wave is in the air, and each wave retires
    // itself once its last piece has left the screen.
    LaunchedEffect(airborne) {
        while (waves.isNotEmpty()) {
            withFrameNanos { now ->
                clock = now
                waves.removeAll { (now - it.start) / 1e9 >= life }
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val now = clock
        for (wave in waves) {
            val elapsed = (now - wave.start) / 1e9
            // why: the tail fade catches whatever is still airborne as a wave retires,
            // so nothing pops out mid-screen.
            val fade = ((life - elapsed) / 1.0).coerceIn(0.0, 1.0).toFloat()
            drawWave(wave.id, elapsed, fade, pieceCount, emission, palette)
        }
    }
}

/** One handful, identified so its retirement removes exactly it. */
private class Wave(val id: Int, val start: Long)

/** Draws in dp: every number below is iOS's in points. */
private fun DrawScope.drawWave(
    wave: Int, elapsed: Double, fade: Float, pieceCount: Int, emission: Double, palette: List<Color>,
) {
    val d = density
    val width = size.width / d
    val height = size.height / d
    for (index in 0 until pieceCount) {
        // Squaring the launch spread front-loads it: most of the handful is away in the
        // first moment, the rest keeps trickling after it.
        val age = elapsed - random(wave, index, 1).pow(2.2) * emission
        if (age <= 0) continue

        // Constant fall plus a gentle pull, so late pieces are visibly quicker than they
        // started — paper does not settle into one terminal speed.
        val speed = 210 + random(wave, index, 2) * 210
        val y = -40 + age * speed + 30 * age * age
        if (y >= height + 40) continue

        val sway = 6 + random(wave, index, 4) * 26
        val phase = (age + random(wave, index, 6) * 6) * (0.35 + random(wave, index, 5) * 0.7)
        val x = random(wave, index, 3) * width + sin(phase * 2 * PI) * sway

        val scale = 0.7 + random(wave, index, 10) * 0.7
        val w = (7.0 * scale * d).toFloat()
        val h = ((if (index % 3 == 2) 16.0 else 10.0) * scale * d).toFloat()
        val topLeft = Offset(-w / 2, -h / 2)
        val color = palette[index % palette.size]
        val alpha = fade * (0.75f + random(wave, index, 11).toFloat() * 0.25f)
        // why: the horizontal squash IS the tumble — a piece turning edge-on narrows to
        // nothing and flickers back, which is the whole difference from a spinning sticker.
        val tumble = cos(age * (1.6 + random(wave, index, 8) * 3.4)).toFloat()
        withTransform({
            translate((x * d).toFloat(), (y * d).toFloat())
            rotate((random(wave, index, 9) * 180).toFloat(), pivot = Offset.Zero)
            scale(tumble, 1f, pivot = Offset.Zero)
        }) {
            if (index % 3 == 1) {
                drawOval(color, topLeft, Size(w, h), alpha)
            } else {
                // card-parity: a paper scrap's corner, not a card radius
                drawRoundRect(color, topLeft, Size(w, h), CornerRadius(1.5f * d), alpha = alpha)
            }
        }
    }
}

/**
 * Stable 0..<1 noise for one (wave, piece, property) — SplitMix64 finish, which
 * decorrelates neighboring indices well enough that the pieces never fall in visible rows.
 */
private fun random(wave: Int, index: Int, salt: Int): Double {
    var x = (index * 0x9E3779B1L + salt * 0x85EBCA77L + wave * 0x2545F491L).toULong()
    x = (x xor (x shr 33)) * 0xFF51AFD7ED558CCDuL
    x = (x xor (x shr 33)) * 0xC4CEB9FE1A85EC53uL
    x = x xor (x shr 33)
    return (x shr 11).toDouble() / (1L shl 53).toDouble()
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
