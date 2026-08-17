package net.spross.app.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import net.spross.kern.box.StreakHealth

/**
 * The app's affordances, drawn.
 *
 * Everything a learner is meant to ACT on — close, go deeper, hear it, the three verdicts —
 * used to be a character in a `Text`: `✕ › ✓ ✗` and the 🔊/🔇 emoji. Two things were wrong
 * with that. The glyphs are not in Nunito (the theme's own note says so for `♀` and `✔`), so
 * they dropped to the platform symbol font and sat at a different optical weight beside every
 * word around them; and an emoji is multi-color artwork that cannot take a tint, so the one
 * speaker on a stone-and-moss card was rendered in Noto's blues.
 *
 * These are cut to one system instead: a 24 unit box, a 2 unit stroke, round caps and joins.
 * They are drawn in black and TINTED at the call site — `Icon` color-filters the whole
 * vector — so every glyph takes `Dl.colors` like the text it sits with.
 *
 * PICTURES are not affordances and stay emoji: the card's own illustration, the sprout, the
 * Workshop's 🔢 and 🔤. Those carry the app's warmth and the iOS cut keeps them too — it
 * spends SF Symbols on exactly the same set as this file.
 *
 * The flame is the exception that proves it: it is a picture, and it was 🔥 until the run
 * gained a STATE ([net.spross.kern.box.StreakHealth]) — solid, pale, hollow. Multi-color
 * artwork renders none of that, so a mark that has to say WHICH grade it is becomes a
 * glyph here, however picture-like it reads.
 */
object SprossIcons {

    /** Dismiss — the session's corner, a sheet, a search field. Also the "missed it" verdict. */
    val Close = stroked("Close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    /** Confirmed — a right answer, a consolidated card, the verdict that says it came. */
    val Check = stroked("Check") {
        moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 6.5f)
    }

    /** The middle verdict: it came, but it cost something. A mark with no direction to it. */
    val Dot = filled("Dot") {
        moveTo(12f, 7f)
        arcToRelative(5f, 5f, 0f, true, false, 0f, 10f)
        arcToRelative(5f, 5f, 0f, true, false, 0f, -10f)
        close()
    }

    /** This row opens something. */
    val ChevronRight = stroked("ChevronRight") {
        moveTo(9.5f, 5f); lineTo(16.5f, 12f); lineTo(9.5f, 19f)
    }

    /** This block folds open. */
    val ChevronDown = stroked("ChevronDown") {
        moveTo(5f, 9.5f); lineTo(12f, 16.5f); lineTo(19f, 9.5f)
    }

    /** Add a word of the learner's own. */
    val Plus = stroked("Plus") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }

    /** What the answer owes back: the turn from what was written down to the form beside it. */
    val CornerDownRight = stroked("CornerDownRight") {
        moveTo(6f, 5f); lineTo(6f, 15f); lineTo(18f, 15f)
        moveTo(14f, 11f); lineTo(18f, 15f); lineTo(14f, 19f)
    }

    /** Write to the address the app answers on. The iOS cut spends `envelope` here. */
    val Envelope = stroked("Envelope") {
        moveTo(3.5f, 6f); lineTo(20.5f, 6f); lineTo(20.5f, 18f); lineTo(3.5f, 18f); close()
        moveTo(3.5f, 6.5f); lineTo(12f, 13f); lineTo(20.5f, 6.5f)
    }

    /**
     * What this build is made of — the voices, their licenses, the Impressum.
     * `info.circle` on the other phone. The tittle is a capped hair of a stroke, which the
     * round cap renders as the dot; a filled one would need a second path.
     */
    val Info = stroked("Info") {
        moveTo(12f, 3.5f)
        arcToRelative(8.5f, 8.5f, 0f, true, false, 0f, 17f)
        arcToRelative(8.5f, 8.5f, 0f, true, false, 0f, -17f)
        close()
        moveTo(12f, 11f); lineTo(12f, 16.5f)
        moveTo(12f, 7.6f); lineTo(12f, 7.7f)
    }

    /** Hear it. The cone is filled and the waves are stroked, as one glyph. */
    val Speaker = ImageVector.Builder(
        name = "Speaker",
        defaultWidth = SIZE.dp,
        defaultHeight = SIZE.dp,
        viewportWidth = SIZE,
        viewportHeight = SIZE,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 4.5f); lineTo(12f, 19.5f)
            lineTo(7f, 15f); lineTo(3f, 15f); close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(15.5f, 9f); quadTo(17.5f, 12f, 15.5f, 15f)
            moveTo(18.5f, 6.5f); quadTo(22f, 12f, 18.5f, 17.5f)
        }
    }.build()

    /** Silenced — the same cone, with the waves struck out rather than absent. */
    val SpeakerOff = ImageVector.Builder(
        name = "SpeakerOff",
        defaultWidth = SIZE.dp,
        defaultHeight = SIZE.dp,
        viewportWidth = SIZE,
        viewportHeight = SIZE,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 4.5f); lineTo(12f, 19.5f)
            lineTo(7f, 15f); lineTo(3f, 15f); close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(16f, 9.5f); lineTo(21.5f, 14.5f)
            moveTo(21.5f, 9.5f); lineTo(16f, 14.5f)
        }
    }.build()

    /**
     * The run's flame, cut once so the two weights below are one shape.
     *
     * A tip that leans over a round belly, with the tongue and its notch on the left; the
     * notch is held wide enough that the outline's own stroke does not close it. The iOS
     * cut is SF's `flame.fill` / `flame`.
     */
    private val flamePath: PathBuilder.() -> Unit = {
        moveTo(12.4f, 2.2f)
        curveTo(12.9f, 7.4f, 17.6f, 9.2f, 17.6f, 14f)
        curveTo(17.6f, 18.1f, 14.9f, 21.6f, 11.7f, 21.6f)
        curveTo(8.3f, 21.6f, 5.9f, 18.7f, 5.9f, 15.4f)
        curveTo(5.9f, 13.2f, 6.9f, 11.5f, 8.4f, 10.2f)
        curveTo(8.5f, 12.2f, 9.8f, 13.6f, 11.9f, 14f)
        curveTo(10.8f, 10f, 11.1f, 5.8f, 12.4f, 2.2f)
        close()
    }

    /** The run's mark with a body to it — a run still burning, at full or half strength. */
    val Flame = filled("Flame", flamePath)

    /** The same flame emptied out, for a run with nothing behind today. */
    val FlameOutline = stroked("FlameOutline", flamePath)
}

/**
 * The flame at the grade today has earned it — the widget's three weights, in the app.
 *
 * Solid where the day is answered; pale where a miss would only spend the run's one bridge;
 * HOLLOW where it would end the run, which is the loud one — an outline in full accent reads
 * as a thing about to go out, where a fainter fill would read as less important.
 * The rule itself is [net.spross.kern.box.StreakHealth]; this only dresses it.
 */
@Composable
fun StreakFlame(health: StreakHealth, modifier: Modifier = Modifier) {
    val palette = Dl.colors
    val (glyph, tint) = when (health) {
        StreakHealth.Earned -> SprossIcons.Flame to palette.accent
        StreakHealth.Bridgeable -> SprossIcons.Flame to palette.accent.copy(alpha = 0.5f)
        StreakHealth.Ending -> SprossIcons.FlameOutline to palette.accent
        StreakHealth.None -> SprossIcons.FlameOutline to palette.textSecondary
    }
    Icon(glyph, contentDescription = null, tint = tint, modifier = modifier)
}

private const val SIZE = 24f
private const val STROKE = 2f

/** One stroked glyph on the shared grid — the weight and the caps are not per-icon choices. */
private fun stroked(name: String, path: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = SIZE.dp,
        defaultHeight = SIZE.dp,
        viewportWidth = SIZE,
        viewportHeight = SIZE,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = path,
        )
    }.build()

/** …and one solid glyph, for the marks that are a shape rather than a stroke. */
private fun filled(name: String, path: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = SIZE.dp,
        defaultHeight = SIZE.dp,
        viewportWidth = SIZE,
        viewportHeight = SIZE,
    ).apply {
        path(fill = SolidColor(Color.Black), pathBuilder = path)
    }.build()
