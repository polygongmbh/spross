package net.spross.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

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
 * vector — so every glyph takes `Theme.colors` like the text it sits with.
 *
 * PICTURES are not affordances and stay emoji: the card's own illustration, the sprout, the
 * Workshop's 🔢 and 🔤. Those carry the app's warmth and the iOS cut keeps them too — it
 * spends SF Symbols on exactly the same set as this file.
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

    /** Back out of a screen the run pushed. The bar's own way out, where a ✕ would be a blob. */
    val ArrowLeft = stroked("ArrowLeft") {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
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

    /** Pack a word into the box: a tray, and the word dropping into it. */
    val PackIn = stroked("PackIn") {
        moveTo(5f, 14f); lineTo(5f, 19f); lineTo(19f, 19f); lineTo(19f, 14f)
        moveTo(12f, 4f); lineTo(12f, 14f)
        moveTo(8f, 10f); lineTo(12f, 14f); lineTo(16f, 10f)
    }

    /** Take a word back out — [PackIn] run in reverse. */
    val PackOut = stroked("PackOut") {
        moveTo(5f, 14f); lineTo(5f, 19f); lineTo(19f, 19f); lineTo(19f, 14f)
        moveTo(12f, 14f); lineTo(12f, 4f)
        moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f)
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

    /** Let it run on. A transport control, so it is a shape rather than a stroke. */
    val Play = filled("Play") {
        moveTo(7.5f, 4.5f); lineTo(19f, 12f); lineTo(7.5f, 19.5f); close()
    }

    /** Hold it where it is. */
    val Pause = filled("Pause") {
        moveTo(7.5f, 5f); lineTo(10.5f, 5f); lineTo(10.5f, 19f); lineTo(7.5f, 19f); close()
        moveTo(13.5f, 5f); lineTo(16.5f, 5f); lineTo(16.5f, 19f); lineTo(13.5f, 19f); close()
    }

    /** The next word, asked for. */
    val SkipNext = filled("SkipNext") {
        moveTo(5.5f, 5f); lineTo(15f, 12f); lineTo(5.5f, 19f); close()
        moveTo(16.5f, 5f); lineTo(19f, 5f); lineTo(19f, 19f); lineTo(16.5f, 19f); close()
    }

    /**
     * That one again. The ring runs BACKWARDS — counterclockwise, opening at the top — so
     * the glyph says "over again" rather than "onward", which a clockwise loop would.
     *
     * Both endpoints sit on the circle around (12,12), and the SWEEP is what picks which of
     * the two circles through them is drawn: taken the other way it centers at (16.95, 0.05),
     * almost entirely outside the box, and what survives the viewport is a crescent.
     * `res/drawable/ic_listen_again.xml` carries the same path for the lock screen.
     */
    val Again = stroked("Again") {
        moveTo(12f, 5f)
        arcTo(7f, 7f, 0f, true, false, 18.58f, 9.61f)
        moveTo(15f, 3.2f); lineTo(12f, 5f); lineTo(15f, 6.8f)
    }

    /**
     * The bedtime. A crescent rather than 🌙: an affordance is a tintable vector here, and
     * the emoji it replaces was multi-color artwork that took no tint at all.
     */
    val Moon = stroked("Moon") {
        moveTo(21f, 12.79f)
        arcTo(9f, 9f, 0f, true, true, 11.21f, 3f)
        arcTo(7f, 7f, 0f, false, false, 21f, 12.79f)
        close()
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
