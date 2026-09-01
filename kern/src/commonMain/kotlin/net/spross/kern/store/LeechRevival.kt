package net.spross.kern.store

import net.spross.kern.box.BoxState

/**
 * One-time migration for a box that loaded under a build older than the 2026-09-01 ruling
 * that removed the leech auto-suspend: revives every card still carrying that rule's mark,
 * so a word it silently pushed out of rotation does not stay suspended forever now that
 * nothing pushes new ones out. Suspended with 2+ lifetime lapses is the exact condition the
 * removed rule used to trigger — the same shape a learner's own hand-suspend of an
 * already-twice-lapsed word would have, so this is a best-effort sweep, not a precise one;
 * a learner who re-suspends a revived word after this runs is making a fresh, current choice.
 *
 * Call this once per load, alongside [withProductCalibration]. DELETE this file and its two
 * call sites (`AppModel.kt`, `AppModel.swift`) once the app is comfortably past 7.0 — by then
 * every box that could still carry a leech-era suspension will have loaded through it.
 */
fun BoxState.revivingLeechSuspensions(): BoxState = copy(
    scheduling = scheduling.mapValues { (_, sched) ->
        if (sched.suspended && sched.lapses >= 2) sched.copy(suspended = false) else sched
    },
)
