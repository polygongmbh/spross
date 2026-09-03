package net.spross.kern.store

import net.spross.kern.box.BoxState

/**
 * One-time migration for a box written before the 2026-09-03 ruling that every verb concept
 * slug carries a `to-` prefix: moves progress stored under the bare slug onto the prefixed one,
 * so a renamed verb keeps the schedule, the queue place and the report the learner earned for it.
 *
 * The mapping is read off the join rather than a rename table: an id the current catalog no
 * longer knows, whose `to-` form it DOES know, is that rename and nothing else — which means a
 * verb renamed later needs no edit here, and a noun that kept its bare slug (`help`, `work`,
 * `practice`, all still cards) is never touched because it still joins. An own word cannot
 * collide either; its id is prefixed, so it joins as a card of its own.
 * Where the prefixed id already carries progress, that progress wins and the bare entry is left
 * as it is — the box the learner actually used is never overwritten by an orphan.
 *
 * Call this once per load, alongside [revivingLeechSuspensions]. DELETE this file and its two
 * call sites (`AppModel.kt`, `AppModel.swift`) once the app is comfortably past 7.0 — by then
 * every box that could still hold a bare verb slug will have loaded through it.
 */
fun BoxState.rekeyingPrefixedVerbs(): BoxState {
    val moves = (scheduling.keys + enqueued + reportedIssues.keys)
        .filter { it !in cards && "to-$it" in cards }
        .associateWith { "to-$it" }
    if (moves.isEmpty()) return this
    return copy(
        scheduling = scheduling.entries.associate { (id, sched) ->
            val to = moves[id]
            if (to != null && to !in scheduling) to to sched.copy(cardId = to) else id to sched
        },
        enqueued = enqueued.map { moves[it] ?: it }.distinct(),
        reportedIssues = reportedIssues.entries.associate { (id, issue) ->
            val to = moves[id]
            if (to != null && to !in reportedIssues) to to issue.copy(cardId = to) else id to issue
        },
    )
}
