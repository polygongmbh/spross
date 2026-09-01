package net.spross.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import net.spross.kern.box.StreakHealth

/** The flame, cut down to the line it stands on. */
private val FLAME = 16.dp

/**
 * What today still owes the run, and what the box is holding for it.
 *
 * A run of zero shows the flame cold and faint with NO number: there is nothing to count
 * yet, and a "0" beside a dead flame reads as a score rather than an invitation.
 */
@Composable
fun StatsLine(face: WidgetFace, withLabel: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Flame(face.health)
        if (face.streak > 0) {
            Spacer(GlanceModifier.width(4.dp))
            Text("${face.streak}", style = WidgetType.stat, maxLines = 1)
        }
        if (face.dueCount > 0) {
            Spacer(GlanceModifier.width(10.dp))
            val due = if (withLabel) "${face.dueCount} ${face.chrome.boxCardDue}" else "${face.dueCount}"
            Text(due, style = WidgetType.stat, maxLines = 1)
        }
    }
}

/** The line the grid reserves for the stats, whatever shape the tile is. */
val STATS_HEIGHT = 20.dp

/**
 * Every tile's header: the run and the due count on the left, and — where the width
 * carries it — the fortnight on the right, which is the room the bottom of a tile does not
 * have. A tile too narrow for the strip loses the word beside the count with it — the bare
 * flame and number is what the smallest tile has room for, and one line says both things.
 */
@Composable
fun StatsHeader(face: WidgetFace, full: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(STATS_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatsLine(face, withLabel = full)
        if (full) {
            Spacer(GlanceModifier.defaultWeight())
            ActivityStrip(face.days, face.chrome)
        }
    }
}

@Composable
private fun Flame(health: StreakHealth) {
    // why: [WidgetFlame] paints in pixels, so the density comes from the host rather than
    // from a dp the layout would still have to scale.
    val density = LocalContext.current.resources.displayMetrics.density
    Image(
        provider = ImageProvider(WidgetFlame.bitmap(health, (FLAME.value * density).toInt())),
        contentDescription = null,
        modifier = GlanceModifier.size(FLAME),
    )
}
