package net.spross.app

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spross.app.widget.WordWidget
import net.spross.kern.box.BoxState
import net.spross.kern.snapshot.WidgetSnapshotBuilder
import net.spross.kern.store.BoxBackup
import net.spross.kern.store.StoredBox
import net.spross.kern.store.StoredBoxes

/**
 * Every answer persists (small doc, IO thread) — process death mid-session then costs
 * at most the in-flight card, matching iOS's debounced-save guarantee.
 *
 * [blocking] is `onStop`'s fold asking to be on disk BEFORE the caller returns: the
 * process may not live long enough for a queued write, and a fold that never reaches
 * disk is no fold. Nothing else asks for it — the encode and the tile's snapshot
 * together are the better part of a frame, and the last answer of a round would pay
 * them where the summary is waiting to be drawn (`docs/performance.md`).
 *
 * [widget] rebuilds the tile's snapshot. Off for the answers inside a round and only
 * those: building it walks the exposure ranking, every active card and every day the
 * box has tallied, and the tile's worth is long-term exposure — a round's staleness
 * does not touch it, while a rebuild per card is the same order of work as the box
 * document itself (`kern/docs/snapshots.md`).
 */
internal fun AppModel.persist(state: BoxState, widget: Boolean = true, blocking: Boolean = false) {
    val target = state.joinStamp.target
    val box = disk.record(state)
    val stamp = now()
    if (blocking) {
        disk.write(target, box)
        if (widget) {
            disk.files.writeWidgetSnapshot(widgetSnapshot(state, stamp))
            nudgeWidget()
        }
        return
    }
    // why: NonCancellable — a write racing activity teardown must still land.
    viewModelScope.launch(Dispatchers.IO + NonCancellable) {
        // why: the encode is the expensive half — every card's log in this language —
        // and it belongs on this thread with the write, not on the one that has to
        // draw the next card.
        disk.write(target, box)
        if (widget) {
            disk.files.writeWidgetSnapshot(widgetSnapshot(state, stamp))
            WordWidget.refresh(getApplication())
        }
    }
}

/**
 * Backgrounding (`SprossActivity.onStop`): every answer is already in the box, so this
 * only makes sure it reaches disk before the process can be taken away.
 */
fun AppModel.persistNow() {
    val state = box ?: return
    persist(state, blocking = true)
}

/**
 * Redraw of the placed tiles, for the path that cannot wait for one.
 *
 * `updateAll` suspends and `onStop` returns before it could finish; the snapshot is
 * already on disk by then, so a nudge that loses the race costs nothing but
 * promptness — the tile's own update period redraws it either way.
 */
private fun AppModel.nudgeWidget() {
    viewModelScope.launch(Dispatchers.IO + NonCancellable) {
        WordWidget.refresh(getApplication())
    }
}

/**
 * What the home-screen widget draws, resolved HERE because the widget cannot run
 * the join (`kern/docs/snapshots.md`) — it decodes this and nothing else.
 * Carries the other languages' days for the same reason Home's strip does: the
 * run is one commitment across every box.
 */
private fun AppModel.widgetSnapshot(state: BoxState, nowEpochMillis: Long): String =
    WidgetSnapshotBuilder.build(
        state,
        nowEpochMillis,
        tz(),
        otherLanguagesAnswerDays = otherLanguagesAnswerDays,
    )

/**
 * The backup file's text: [only], or every language, without what belongs to this
 * device ([BoxBackup]).
 */
fun AppModel.backupJson(only: String? = null): String = BoxBackup.encode(disk.openEvery(), only)

/**
 * The languages an export would carry: a box the learner only ever opened is not one of
 * them, so the export neither offers it nor lands it empty on the other phone.
 */
fun AppModel.backupLanguages(): List<String> = BoxBackup.carried(disk.openEvery())

/**
 * Writes the languages a backup restored, then re-opens the pair on screen from the
 * store, so the box drawn is the restored one and not the one it replaced.
 *
 * The pair it opens is the one the FILE names ([StoredBox.source]): the progress was
 * made under that known language, and re-reading it under this device's would leave
 * every own word written in the old one unpaired and untrained. A file from before the
 * store recorded it names none, and the device's own setting stands.
 *
 * On a first run there is no pair on screen yet: the file's last-studied language becomes
 * it, with [firstRunSource] standing in for a file that names no known language, and the
 * onboarding it was picked from gives way to Home.
 */
fun AppModel.restoreBoxes(imported: StoredBoxes, firstRunSource: String? = null) {
    val stamp = box?.joinStamp
    val target = stamp?.target ?: imported.lastStudied() ?: return
    val source = imported.boxes[target]?.source ?: stamp?.source ?: firstRunSource ?: return
    // why: a first run has no profile for the launch after this one to reopen.
    if (stamp == null) profile.set(source, target)
    viewModelScope.launch {
        disk.restoring(imported)
        withContext(Dispatchers.IO) {
            imported.boxes.keys.forEach { language ->
                disk.write(language, disk.boxes.boxes.getValue(language))
            }
        }
        activate(source, target, if (stamp == null) Screen.Home else Screen.Box())
    }
}
