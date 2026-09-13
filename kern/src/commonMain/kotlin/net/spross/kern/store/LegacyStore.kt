package net.spross.kern.store

import net.spross.kern.box.fsrsParameters
import net.spross.kern.box.replayed
import net.spross.kern.fsrs.FsrsScheduler
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.CardScheduling

/**
 * The one-way door out of the v1 schema: a `box-<target>.json` as the previous build wrote
 * it, read into the box that replaces it under the very same name.
 *
 * Schedules are replayed from their logs rather than carried, so a box converts to exactly
 * what it would be if this build had written it all along — with its stored `due` kept,
 * which is the one thing a replay cannot work out.
 *
 * Delete this file, and the v1 reader beside it, once no device can still be holding a v1
 * box: every install converts on its first launch after the change.
 */
internal object LegacyStore {

    @Throws(StoreFormatException::class)
    fun convert(json: String): StoredBox = converted(decodeLegacyBox(json))

    private fun converted(box: DecodedBox): StoredBox {
        val scheduler = FsrsScheduler(BoxConfig.product().fsrsParameters())
        val scheduling = box.scheduling.mapValues { (cardId, sched) ->
            val suspended = sched.suspended && !leechSuspension(sched)
            val due = sched.due
            if (sched.log.isEmpty() || due == null) {
                CardScheduling(cardId = cardId, suspended = suspended)
            } else {
                replayed(cardId, due, sched.log, suspended, scheduler)
            }
        }
        return StoredBox(
            scheduling = scheduling.filterValues { it.log.isNotEmpty() || it.suspended },
            enqueued = box.enqueued,
            ownWords = box.ownWords,
            reportedIssues = box.reportedIssues,
            lastExportAt = box.lastExportAt,
            // why: the day counters a v1 box folded are dropped. The streak reads the logs
            // now, and "consolidated today" is worth less than a clock in the converter.
            consolidatedToday = null,
        )
    }

    /**
     * The mark of the leech rule removed on 2026-09-01: suspended with 2+ lifetime lapses is
     * exactly what it used to do by itself, so those suspensions are lifted on the way over.
     * A learner's own hand-suspend of a twice-lapsed word goes with them — a best-effort
     * sweep, and re-suspending afterwards is a fresh, current choice.
     *
     * The v1 count is what decides, since a replay under today's rule counts differently.
     */
    private fun leechSuspension(sched: CardScheduling): Boolean =
        sched.suspended && sched.lapses >= 2
}
