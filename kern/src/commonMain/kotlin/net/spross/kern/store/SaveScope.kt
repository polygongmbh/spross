package net.spross.kern.store

/**
 * What one save of the box writes: the box document, the snapshots drawn from it, or both.
 *
 * The snapshots are the widget's and the watch's (`kern/docs/snapshots.md`).
 * Building them walks the exposure ranking, the active cards and every day the box has tallied,
 * and what they show is long-term exposure, which a round's staleness does not touch —
 * so an answer inside a round and a change nothing derived reads (an export stamp) write the box alone,
 * and a round's end, a change to what the box holds, a language opened or switched
 * and the app leaving the screen carry the snapshots too.
 * A box opened unchanged owes the disk nothing, but the surfaces still learn which box is on screen.
 *
 * WHEN a save reaches the disk is the platform store's: every save is written,
 * one that has not started yet gives way to a newer one, and their scopes add up ([plus]).
 */
enum class SaveScope(val writesBox: Boolean, val writesSnapshots: Boolean) {
    BOX(writesBox = true, writesSnapshots = false),
    SNAPSHOTS(writesBox = false, writesSnapshots = true),
    BOX_AND_SNAPSHOTS(writesBox = true, writesSnapshots = true),
    ;

    /** Two saves written as one: whatever either of them owed. */
    operator fun plus(other: SaveScope): SaveScope {
        val box = writesBox || other.writesBox
        val snapshots = writesSnapshots || other.writesSnapshots
        return entries.first { it.writesBox == box && it.writesSnapshots == snapshots }
    }
}
