package net.spross.app.widget

import android.content.Context
import java.io.File
import java.util.Locale
import java.util.TimeZone
import net.spross.app.BoxFiles
import net.spross.app.Chrome
import net.spross.app.ProfileStore
import net.spross.kern.box.StreakHealth
import net.spross.kern.model.Gender
import net.spross.kern.snapshot.WidgetBar
import net.spross.kern.snapshot.WidgetSnapshotBuilder

/** One row of a tile: the picture, the article that tints the word, and the pair itself. */
data class WidgetWord(
    val emoji: String,
    /** The article word shown in front of [word]; null where the box names none. */
    val article: String?,
    /** What [article] marks, which is what colors it; null where the box names no gender. */
    val gender: Gender?,
    /** TARGET-side text — an exposure surface always shows the language being learned. */
    val word: String,
    /** The source meaning, ♀ marker already baked in by the phone. */
    val meaning: String,
)

/**
 * One draw of the tile, resolved before composition: the window of words this moment
 * shows, and the numbers the header states.
 *
 * Nothing here is computed from the catalog — the phone pre-resolved every row on its
 * last snapshot save (`kern/docs/snapshots.md`), and the only work left is the handful of
 * answers that move with the clock.
 */
class WidgetFace(
    val words: List<WidgetWord>,
    val dueCount: Int,
    val streak: Int,
    val health: StreakHealth,
    val bars: List<WidgetBar>,
    val chrome: Chrome,
)

/** How a tile gets from the snapshot on disk to the face it draws. */
object WidgetFaces {

    /**
     * The width of the rotating window: as many words as the largest grid can show
     * ([GRID_CELLS]), and never more than the snapshot carries — the phone writes
     * [WidgetSnapshotBuilder.DEFAULT_EXPOSURE_LIMIT] rows, so a bigger grid would ask for
     * words that were never sent.
     */
    val WINDOW: Int = minOf(GRID_CELLS, WidgetSnapshotBuilder.DEFAULT_EXPOSURE_LIMIT)

    /**
     * How long one window stands before the head moves on ([net.spross.kern.snapshot.WidgetRotation]).
     *
     * Half an hour rather than the iOS timeline's quarter: a Glance tile has no timeline
     * of future entries to hand the host, so a window only changes when the tile is
     * redrawn, and the shortest period the platform will schedule is thirty minutes.
     */
    const val ROTATION_MILLIS: Long = 30 * 60 * 1000

    /** A card with no picture of its own still needs one, and this is the app's own stand-in. */
    private const val FALLBACK_PICTURE = "🗂️"

    /**
     * The face for [nowEpochMillis], or null when there is nothing of the learner's to
     * draw — no snapshot, one this build cannot decode (what an app update leaves behind
     * until the app next runs), or an empty box. Every one of those draws the sprout.
     */
    fun load(context: Context, nowEpochMillis: Long): WidgetFace? {
        val json = BoxFiles(File(context.filesDir, "box")).readWidgetSnapshot() ?: return null
        val view = WidgetSnapshotBuilder.decode(json) ?: return null
        if (view.entries.isEmpty()) return null
        // The window stays in kern's ranking order from its head on: a tile takes as many cells as its shape fits off the FRONT of this list, and a
        // window sorted by length would hand a one-cell tile the shortest word rather than
        // the one most worth seeing. Where the cells then land is [GridFace]'s.
        val words = view.window(nowEpochMillis, WINDOW, ROTATION_MILLIS).map {
            WidgetWord(it.emoji ?: FALLBACK_PICTURE, it.article, it.gender, it.text, it.sourceText)
        }
        val tz = TimeZone.getDefault().id
        return WidgetFace(
            words = words,
            dueCount = view.dueCount(nowEpochMillis),
            streak = view.streak(nowEpochMillis, tz),
            health = view.streakHealth(nowEpochMillis, tz),
            bars = view.activityBars(nowEpochMillis, tz),
            chrome = chrome(context),
        )
    }

    /**
     * Which chrome the tile speaks. The widget is not in the app's locale environment and
     * has no model to ask, so it reads the same profile the model does: the box's SOURCE
     * language decides the chrome, exactly as `Chrome.forSource` decides it for every
     * screen. Before there is a profile there is no box either, so the device's own
     * language stands in — which is what onboarding would offer anyway.
     */
    fun chrome(context: Context): Chrome {
        val prefs = context.getSharedPreferences(ProfileStore.PREFS_NAME, Context.MODE_PRIVATE)
        return Chrome.forSource(ProfileStore(prefs).source ?: Locale.getDefault().language)
    }
}
