package net.spross.kern.box

/**
 * What an area's own control in the box browser offers — the area is the unit it acts on.
 *
 * While anything is left to take in, it queues the shelf ([Queue]).
 * Once nothing is, a shelf holding more than a couple of words still queued offers to take
 * them back out as a batch ([Unqueue]); below that the per-word rows unqueue instead,
 * and the shelf wears a mark: [Settled] once nothing is queued and every active card has
 * settled — the shelf's counts and bar then have nothing left to say and step aside —
 * [AllQueued] otherwise.
 */
enum class ShelfControl {
    Queue,
    Unqueue,
    AllQueued,
    Settled,
    ;

    /** The area is done: its counts and bar step aside for the mark. */
    val hidesProgress: Boolean get() = this == Settled

    companion object {
        /** The fewest queued words the shelf offers to take back out at once. */
        private const val BULK_UNQUEUE_MIN = 3

        /** [counts] is [BoxBrowser.shelfCounts]'s entry for the area, null when it has none. */
        fun of(counts: ShelfCounts?, stats: AreaStatistics?): ShelfControl {
            val queueable = counts?.queueable ?: 0
            val queued = counts?.queued ?: 0
            return when {
                queueable > 0 -> Queue
                queued >= BULK_UNQUEUE_MIN -> Unqueue
                queued == 0 && stats?.fullySettled == true -> Settled
                else -> AllQueued
            }
        }
    }
}
