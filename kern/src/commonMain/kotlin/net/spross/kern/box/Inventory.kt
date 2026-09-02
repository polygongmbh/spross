package net.spross.kern.box

import kotlin.time.Instant
import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.fnv1a64

/**
 * Join-filtered card inventory. Composition, dueNow, statistics, and exposure all read
 * through here; only the phrase-unlock gate and `answer()` history reads touch
 * `state.scheduling` raw by card id.
 */
internal object Inventory {

    /** Pinned card order — map iteration order never leaks. */
    val seedOrder: Comparator<Card> = compareBy({ it.seedIndex }, { it.id })

    /** Every card of the current join, in seed order. */
    fun joinedCards(state: BoxState): List<Card> =
        state.cards.values.sortedWith(seedOrder)

    /** Schedules whose card exists under the current join, sorted by id (deterministic). */
    fun scheduled(state: BoxState): List<CardScheduling> =
        state.scheduling.entries
            .filter { it.key in state.cards }
            .sortedBy { it.key }
            .map { it.value }

    fun active(state: BoxState): List<CardScheduling> =
        scheduled(state).filter { !it.suspended }

    /**
     * Active schedules in no promised order — for callers that impose a TOTAL
     * order of their own, so the id sort [scheduled] pays for determinism is
     * one both of them would only throw away.
     */
    private fun unordered(state: BoxState): List<CardScheduling> =
        state.scheduling.entries
            .filter { it.key in state.cards && !it.value.suspended }
            .map { it.value }

    /** Active cards carrying a due date, soonest first — the pull-forward pool. */
    fun scheduledAhead(state: BoxState): List<CardScheduling> =
        unordered(state)
            .filter { it.due != null }
            .sortedWith(compareBy({ it.due }, { it.cardId }))

    /**
     * Active cards with `due <= now`: words not yet consolidated first, then oldest DAY,
     * shuffled within the day.
     *
     * Delay is not one cost. `R(t) = (1 + factor·t/S)^decay` flattens in proportion to
     * STABILITY, so a month late leaves a mature word barely under its target and a word
     * met once far below it — and the shipped weights, fitted over collections mostly made
     * of mature cards, over-predict recall exactly where stability is lowest. So the queue
     * spends a capped sitting where a review still changes the outcome: a shaky word waits
     * behind nothing, and a settled one can afford the wait it is being asked for
     * (`docs/growth-evidence.md`).
     */
    fun due(state: BoxState, nowEpochMillis: Long): List<CardScheduling> {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        return unordered(state)
            .filter { it.due != null && it.due <= now }
            // why: the shuffle key hashes a string, so it is built ONCE per card
            // here rather than inside the comparator, which would pay for it
            // O(n log n) times over.
            .map { DueKey(it, Statistics.isConsolidated(state, it)) }
            .sortedWith(dueKeyOrder)
            .map { it.entry }
    }

    /** How many joined schedules sleep — [scheduled] minus [active], counted. */
    fun suspendedCount(state: BoxState): Int =
        state.scheduling.entries.count { it.key in state.cards && it.value.suspended }

    /**
     * How many active cards stand due — the same population [due] lists, counted.
     *
     * A caller that only wants the number never pays for the order: no shuffle keys,
     * no sort. Whether the backlog is empty is this asked as `> 0`.
     */
    fun dueCount(state: BoxState, nowEpochMillis: Long): Int {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        return unordered(state).count { it.due != null && it.due <= now }
    }

    private const val MILLIS_PER_DAY = 86_400_000L

    private fun dueEpochDay(entry: CardScheduling): Long =
        entry.due!!.toEpochMilliseconds() / MILLIS_PER_DAY

    /**
     * Backlog fairness at DAY granularity, de-correlated inside the day.
     * Cards introduced together are answered seconds apart, so a timestamp sort
     * keeps them adjacent for the life of the box and the learner answers from
     * sequence ("the one after *young*") instead of from memory. Hashing the id
     * with the card's OWN due day reshuffles each bucket — and reshuffles it
     * differently from one day to the next, since the day feeds the hash —
     * while staying pure: no clock read, no randomness. The trailing id keeps
     * the order total under a hash collision.
     *
     * The id is folded to its own hash BEFORE the day re-hashes it: FNV-1a
     * barely avalanches the last bytes it consumes, so hashing day and raw id
     * concatenated leaves whichever part trails poorly mixed — a trailing day
     * yields the same order every day, a trailing id keeps seed neighbors
     * adjacent within the day. Both halves must arrive well spread.
     */
    private class DueKey(val entry: CardScheduling, val consolidated: Boolean) {
        val day: Long = dueEpochDay(entry)
        val shuffle: ULong = fnv1a64("$day:${fnv1a64(entry.cardId)}")
    }

    private val dueKeyOrder: Comparator<DueKey> = compareBy(
        { it.consolidated },
        { it.day },
        { it.shuffle },
        { it.entry.cardId },
    )
}
