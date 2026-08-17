package net.spross.app.widget

import android.content.Context
import java.io.File
import java.util.Locale
import java.util.TimeZone
import net.spross.app.BoxFiles
import net.spross.app.Chrome
import net.spross.app.ProfileStore
import net.spross.kern.box.ActivityDay
import net.spross.kern.box.StreakHealth
import net.spross.kern.snapshot.WidgetSnapshotBuilder

/** One row of a tile: the picture, the article that tints the word, and the pair itself. */
data class WidgetWord(
    val emoji: String,
    /** The article word, which is also what colors it; null where the box names no gender. */
    val article: String?,
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
 * last persist (`kern/docs/snapshots.md`), and the only work left is the handful of
 * answers that move with the clock.
 */
class WidgetFace(
    val words: List<WidgetWord>,
    val dueCount: Int,
    val streak: Int,
    val health: StreakHealth,
    val days: List<ActivityDay>,
    val chrome: Chrome,
)

/** How a tile gets from the snapshot on disk to the face it draws. */
object WidgetFaces {

    /** Cells the poster holds — and so the width of the rotating window. */
    const val WINDOW: Int = 6

    /**
     * How long one window stands before the head moves on.
     *
     * Half an hour rather than the iOS timeline's quarter: a Glance tile has no timeline
     * of future entries to hand the host, so a window only changes when the tile is
     * redrawn, and the shortest period the platform will schedule is thirty minutes.
     */
    const val ROTATION_MILLIS: Long = 30 * 60 * 1000

    /** The fortnight the header strip draws. */
    const val ACTIVITY_DAYS: Int = 14

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
        val words = view.entries.map {
            WidgetWord(it.emoji ?: FALLBACK_PICTURE, it.articleTint, it.text, it.sourceText)
        }
        if (words.isEmpty()) return null
        val tz = TimeZone.getDefault().id
        return WidgetFace(
            words = window(words, nowEpochMillis),
            dueCount = view.dueCount(nowEpochMillis),
            streak = view.streak(nowEpochMillis, tz),
            health = view.streakHealth(nowEpochMillis, tz),
            days = view.activityWindow(ACTIVITY_DAYS, nowEpochMillis, tz),
            chrome = chrome(context),
        )
    }

    /**
     * The window this moment shows: the head advances one card every [ROTATION_MILLIS]
     * through kern's attention ranking, and the rows are then laid out SHORTEST PAIR
     * FIRST, so a list opens into a cone around its emoji spine and the poster fills
     * reading order short-to-long. Which cards travel is kern's; where they land is here.
     */
    fun window(words: List<WidgetWord>, nowEpochMillis: Long): List<WidgetWord> {
        val head = ((nowEpochMillis / ROTATION_MILLIS) % words.size).toInt()
        return (0 until minOf(WINDOW, words.size))
            .map { words[(head + it) % words.size] }
            .sortedWith(compareBy({ it.word.length + it.meaning.length }, { it.word.length }))
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
