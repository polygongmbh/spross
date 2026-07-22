package net.spross.kern.box

import kotlin.time.Instant
import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling

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

    /** Active cards with `due <= now`, oldest first, ties broken by card id. */
    fun due(state: BoxState, nowEpochMillis: Long): List<CardScheduling> {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        return active(state)
            .filter { it.due != null && it.due <= now }
            .sortedWith(compareBy({ it.due }, { it.cardId }))
    }

    /** Cards holding a learning-pool slot: joined, active, Learning phase. */
    fun cardsInLearning(state: BoxState): Set<String> =
        active(state)
            .filter { it.phase == CardPhase.Learning }
            .mapTo(mutableSetOf()) { it.cardId }
}
