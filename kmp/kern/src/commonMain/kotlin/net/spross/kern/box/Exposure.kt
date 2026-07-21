package net.spross.kern.box

import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase
import net.spross.kern.model.UnitKey

internal object Exposure {

    private data class Ranked(val tier: Int, val order: Double, val key: String, val card: Card)

    /**
     * Cards worth surfacing for passive exposure (widgets, watch), most urgent first.
     * Units rank in v1's tiers:
     *   0 relearning · 1 enqueued-eligible new (queue order) · 2 learning ·
     *   3 review (weakest stability first) · 4 upcoming introducible units in seed order.
     * Deduped by card keeping the unit with the lowest (tier, order, key); `limit`
     * applies AFTER dedup. Display surfaces always render the winning card's TARGET
     * realization regardless of the winning role.
     */
    fun exposureCards(state: BoxState, limit: Int): List<Card> {
        if (limit <= 0) return emptyList()
        val ranked = mutableListOf<Ranked>()

        for (sched in Inventory.scheduled(state)) {
            if (sched.suspended || sched.memory == null) continue
            val tier = when (sched.phase) {
                CardPhase.Relearning -> 0
                CardPhase.Learning -> 2
                else -> 3
            }
            ranked += Ranked(tier, sched.memory.stability, sched.key, state.cards.getValue(sched.cardId))
        }

        Growth.enqueuedEligible(state).forEachIndexed { index, id ->
            ranked += Ranked(1, index.toDouble(), UnitKey.produce(id).encoded, state.cards.getValue(id))
        }

        // Preview: the next introducible units in seed order, so the list is never empty.
        val rankedCards = ranked.mapTo(mutableSetOf()) { it.card.id }
        Inventory.joinedUnits(state)
            .filter {
                state.scheduling[it.key] == null && it.card.id !in rankedCards &&
                    Growth.isIntroducible(state, it)
            }
            .take(limit)
            .forEachIndexed { index, unit ->
                ranked += Ranked(4, index.toDouble(), unit.key, unit.card)
            }

        val seen = mutableSetOf<String>()
        return ranked
            .sortedWith(compareBy({ it.tier }, { it.order }, { it.key }))
            .filter { seen.add(it.card.id) }
            .take(limit)
            .map { it.card }
    }
}
