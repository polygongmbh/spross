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

    /** Active cards carrying a due date, soonest first — the pull-forward pool. */
    fun scheduledAhead(state: BoxState): List<CardScheduling> =
        active(state)
            .filter { it.due != null }
            .sortedWith(compareBy({ it.due }, { it.cardId }))

    /** Active cards with `due <= now`, oldest DAY first, shuffled within the day. */
    fun due(state: BoxState, nowEpochMillis: Long): List<CardScheduling> {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        return active(state)
            .filter { it.due != null && it.due <= now }
            .sortedWith(dueOrder)
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
    private val dueOrder: Comparator<CardScheduling> = compareBy(
        { dueEpochDay(it) },
        { fnv1a64("${dueEpochDay(it)}:${fnv1a64(it.cardId)}") },
        { it.cardId },
    )
}
