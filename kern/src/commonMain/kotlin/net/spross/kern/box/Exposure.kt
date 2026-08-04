package net.spross.kern.box

import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase

internal object Exposure {

    private data class Ranked(val tier: Int, val order: Double, val card: Card)

    /**
     * Cards worth surfacing for passive exposure (widgets, watch), most urgent first.
     * v1's tiers:
     *   0 relearning · 1 enqueued-eligible new (queue order) · 2 learning ·
     *   3 review (weakest stability first) · 4 upcoming introducible cards in seed order.
     * Display surfaces always render the TARGET realization.
     *
     * [eligible] rejects cards a surface cannot render — it gates before the
     * limit, so a surface still gets a full [limit] of the cards it can show.
     */
    fun exposureCards(state: BoxState, limit: Int, eligible: (Card) -> Boolean = { true }): List<Card> {
        if (limit <= 0) return emptyList()
        val ranked = mutableListOf<Ranked>()

        for (sched in Inventory.scheduled(state)) {
            if (sched.suspended || sched.memory == null) continue
            if (!eligible(state.cards.getValue(sched.cardId))) continue
            val tier = when (sched.phase) {
                CardPhase.Relearning -> 0
                CardPhase.Learning -> 2
                else -> 3
            }
            ranked += Ranked(tier, sched.memory.stability, state.cards.getValue(sched.cardId))
        }

        Growth.enqueuedEligible(state)
            .map { state.cards.getValue(it) }
            .filter(eligible)
            .forEachIndexed { index, card ->
                ranked += Ranked(1, index.toDouble(), card)
            }

        // Preview: the next introducible cards in seed order, so the list is never empty.
        val rankedIds = ranked.mapTo(mutableSetOf()) { it.card.id }
        Inventory.joinedCards(state)
            .filter {
                state.scheduling[it.id] == null && it.id !in rankedIds &&
                    Growth.isIntroducible(state, it) && eligible(it)
            }
            .take(limit)
            .forEachIndexed { index, card ->
                ranked += Ranked(4, index.toDouble(), card)
            }

        return ranked
            .sortedWith(compareBy({ it.tier }, { it.order }, { it.card.id }))
            .take(limit)
            .map { it.card }
    }
}
