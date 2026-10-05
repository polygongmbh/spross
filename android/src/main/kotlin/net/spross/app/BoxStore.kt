package net.spross.app

import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.spross.kern.box.BoxState
import net.spross.kern.snapshot.WidgetSnapshotBuilder
import net.spross.kern.store.SaveScope
import net.spross.kern.store.StoreCodec
import net.spross.kern.store.StoredBox
import net.spross.kern.store.StoredBoxes

/**
 * Every language's stored box on disk ([files]), what this launch has read or written of them,
 * and when a save reaches the disk — what it writes is kern's [SaveScope].
 *
 * [save] hands the box over and returns: one writer thread encodes and writes it,
 * since the encode is the expensive half — every card's log in this language — and belongs
 * off the thread that has to draw the next card. Every save is written; one the writer has
 * not started yet gives way to a newer one, the two scopes added up. [saveNow] writes before
 * it returns, for the caller that may not outlive a queued write.
 *
 * The widget's snapshot is built and written here too, beside the box, and [redrawWidget]
 * tells the placed tiles once it is on disk (`kern/docs/snapshots.md`).
 */
class BoxStore(val files: BoxFiles, private val redrawWidget: suspend () -> Unit) {

    private class Waiting(val state: BoxState, val box: StoredBox, val scope: SaveScope, val at: Long)

    // why: the writer outlives the screen that asked — a write racing activity teardown still lands.
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Held while a save is taken and written, so the newest one is always the last on disk. */
    private val writing = Any()

    /** Saves handed over and not written yet, by target. Latest wins. */
    private var waiting: Map<String, Waiting> = emptyMap()

    /** The last [answerDays] asked: its target, time zone and answer. */
    @Volatile private var siblingDays: Triple<String, String, Map<String, Int>>? = null

    /** What has been read or written this launch, by target ([StoredBoxes]). */
    var boxes: StoredBoxes = StoredBoxes.EMPTY
        private set

    /** Every box the device holds, read in where this launch has not read it yet. */
    fun everyLanguage(): StoredBoxes {
        files.targets().filter { it !in boxes.boxes }.forEach { runCatching { load(it) } }
        return boxes
    }

    /**
     * One language's stored box, or null where the device holds none.
     * Throws a [net.spross.kern.store.StoreFormatException] where the file exists but cannot be read.
     * A v1 document converts as it is read and is written back under the same name —
     * a conversion that never reaches disk would convert again on every launch.
     */
    fun load(target: String): StoredBox? {
        val json = files.read(target) ?: return null
        val loaded = StoreCodec.load(json)
        if (loaded.converted) files.write(target, StoreCodec.encode(loaded.box))
        synchronized(this) { boxes = StoredBoxes(boxes.boxes + (target to loaded.box)) }
        return loaded.box
    }

    /**
     * Answers per day in every language but [excluding] — the cross-language streak's input.
     * A sibling that cannot be read is skipped:
     * its own load path surfaces the real error when the learner switches to it.
     */
    fun answerDays(excluding: String, tzId: String): Map<String, Int> {
        files.targets().filter { it != excluding && it !in boxes.boxes }
            .forEach { runCatching { load(it) } }
        return boxes.answerDaysExcept(excluding, tzId).also { siblingDays = Triple(excluding, tzId, it) }
    }

    /** Hands [state] over to be written behind the caller, with what [scope] carries. */
    fun save(state: BoxState, scope: SaveScope) {
        hold(state, scope)
        writer.launch { flush() }
    }

    /** Writes [state], with what [scope] carries, before it returns. */
    fun saveNow(state: BoxState, scope: SaveScope) {
        hold(state, scope)
        flush()
    }

    /**
     * A restore: the languages [imported] carries replace the ones held and are written,
     * the rest stay. A save still waiting goes out first, so the restored box is the one left on disk.
     */
    fun restore(imported: StoredBoxes) = synchronized(writing) {
        flush()
        synchronized(this) { boxes = boxes.restoring(imported) }
        siblingDays = null
        imported.boxes.forEach { (target, box) -> files.write(target, StoreCodec.encode(box)) }
    }

    /** Writes whatever a save left waiting; nothing waiting is nothing to do. */
    fun flush() = synchronized(writing) {
        val due = synchronized(this) { waiting.also { waiting = emptyMap() } }
        due.forEach { (target, save) -> write(target, save) }
    }

    private fun hold(state: BoxState, scope: SaveScope) = synchronized(this) {
        val target = state.joinStamp.target
        boxes = boxes.with(state)
        val owed = waiting[target]?.scope?.plus(scope) ?: scope
        waiting = waiting + (target to Waiting(state, boxes.boxes.getValue(target), owed, System.currentTimeMillis()))
    }

    private fun write(target: String, save: Waiting) {
        if (save.scope.writesBox) files.write(target, StoreCodec.encode(save.box))
        if (save.scope.writesSnapshots) {
            files.writeWidgetSnapshot(widgetSnapshot(save.state, save.at))
            writer.launch { redrawWidget() }
        }
    }

    /**
     * What the home-screen widget draws, resolved HERE because the widget cannot run the join —
     * it decodes this and nothing else. Carries the other languages' days for the same reason
     * Home's strip does: the run is one commitment across every box.
     */
    private fun widgetSnapshot(state: BoxState, nowEpochMillis: Long): String {
        val target = state.joinStamp.target
        val tzId = TimeZone.getDefault().id
        val others = siblingDays?.takeIf { it.first == target && it.second == tzId }?.third
            ?: answerDays(target, tzId)
        return WidgetSnapshotBuilder.build(state, nowEpochMillis, tzId, otherLanguagesAnswerDays = others)
    }
}
