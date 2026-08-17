package net.spross.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import net.spross.app.Chrome
import net.spross.app.SprossActivity

/**
 * Passive exposure on the home screen: words out of the learner's own box, with what
 * today still owes the run.
 *
 * Decode-only — the tile reads the snapshot the app wrote on its last persist and never
 * runs the join (`kern/docs/snapshots.md`). Where the three families differ, and why, is
 * `docs/surfaces.md` § Watch & widgets; [WidgetLayouts] draws them.
 */
class WordWidget : GlanceAppWidget() {

    /**
     * The three families, by the size the host actually hands over. Glance picks the
     * largest bucket that fits and gives the composition that size, so a tile resized on
     * the home screen changes KIND rather than just reflowing.
     */
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val chrome = WidgetFaces.chrome(context)
        provideContent {
            // The snapshot is read INSIDE the composition, keyed on the stamp [refresh]
            // moves: `provideGlance` runs once per session, so a face resolved out here
            // would be the one the tile kept for as long as the app process lived. The
            // read is a couple of kilobytes off app-private storage, and Glance composes
            // on a background coroutine, so it costs no frame anywhere.
            currentState(REVISION)
            val face = WidgetFaces.load(LocalContext.current, System.currentTimeMillis())
            Tile(face, chrome)
        }
    }

    @Composable
    private fun Tile(face: WidgetFace?, chrome: Chrome) {
        val size = LocalSize.current
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(WidgetColors.background)
                .cornerRadius(TILE_RADIUS)
                .padding(12.dp)
                // why: names the tap's destination rather than leaning on a default, so
                // the tile opens the app from the faces that have nothing to show too.
                .clickable(actionStartActivity<SprossActivity>()),
            contentAlignment = Alignment.TopStart,
        ) {
            when {
                face == null -> AwaitingFace(chrome)
                size.height >= LARGE.height -> LargeFace(face)
                size.width >= MEDIUM.width -> MediumFace(face)
                else -> SmallFace(face)
            }
        }
    }

    companion object {
        /**
         * When the app last wrote a snapshot.
         *
         * A tile redraws on a change to its GLANCE STATE, not on a change to a file it
         * happens to read, so this stamp is what a live tile learns the news by; the value
         * itself is never rendered.
         */
        private val REVISION = longPreferencesKey("snapshotRevision")

        /**
         * Tell every placed tile that its snapshot moved.
         *
         * Both halves are needed: the stamp is what makes the composition recompose, and
         * `updateAll` is what pushes the result out to the hosts.
         */
        suspend fun refresh(context: Context) {
            GlanceAppWidgetManager(context).getGlanceIds(WordWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { it[REVISION] = System.currentTimeMillis() }
            }
            WordWidget().updateAll(context)
        }

        /** The tile corner, from the app's own tile radius (`SprossShapes.medium`). */
        private val TILE_RADIUS = 20.dp

        /**
         * The smallest a family fits in, which is what Glance matches a host's tile
         * against — the largest bucket that fits wins.
         *
         * Cut from what a launcher CELL is worth rather than from round numbers: a phone
         * grid gives a column something like 75 dp and a row something like 93, so a
         * two-cell square stays a single word, three columns is the narrowest a two-sided
         * row reads at, and the poster waits for a FOURTH row of grid: a launcher will
         * not place or resize a tile below three, so a poster that opened at three would
         * be the only face the wide tile could ever show.
         */
        private val SMALL = DpSize(120.dp, 120.dp)
        private val MEDIUM = DpSize(200.dp, 120.dp)
        private val LARGE = DpSize(200.dp, 290.dp)
    }
}

/** The host's door to [WordWidget]; the manifest points its provider meta-data here. */
class WordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WordWidget()
}
