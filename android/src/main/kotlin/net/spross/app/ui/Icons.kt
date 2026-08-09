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
 * word around them; and an emoji is multi-colour artwork that cannot take a tint, so the one
 * speaker on a stone-and-moss card was rendered in Noto's blues.
 *
 * These are cut to one system instead: a 24 unit box, a 2 unit stroke, round caps and joins.
 * They are drawn in black and TINTED at the call site — `Icon` colour-filters the whole
 * vector — so every glyph takes `Dl.colors` like the text it sits with.
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

    /** This block folds open. */
    val ChevronDown = stroked("ChevronDown") {
        moveTo(5f, 9.5f); lineTo(12f, 16.5f); lineTo(19f, 9.5f)
    }

    /** Add a word of the learner's own. */
    val Plus = stroked("Plus") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
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
