package net.spross.kern.box

import net.spross.kern.model.Card

internal object Exposure {

    /**
     * Cards worth surfacing for passive exposure (widgets, watch), most urgent first:
     * queued cards (most recently queued first) — no memory at all, the weakest there is —
     * then every scheduled card by [Urgency.weakestFirst],
     * then upcoming introducible cards in seed order, so the list is never empty.
     * Display surfaces always render the TARGET realization.
     *
     * [eligible] rejects cards a surface cannot render — it gates before the
     * limit, so a surface still gets a full [limit] of the cards it can show.
     */
    fun exposureCards(state: BoxState, limit: Int, eligible: (Card) -> Boolean = { true }): List<Card> {
        if (limit <= 0) return emptyList()
        val queued = Growth.queuedEligible(state).map { state.cards.getValue(it) }.filter(eligible)
        val scheduled = Inventory.active(state)
            .filter { it.memory != null && eligible(state.cards.getValue(it.cardId)) }
            .sortedWith(Urgency.weakestFirst)
            .map { state.cards.getValue(it.cardId) }
        val ranked = queued + scheduled
        if (ranked.size >= limit) return ranked.take(limit)

        val rankedIds = ranked.mapTo(mutableSetOf()) { it.id }
        val upcoming = Inventory.joinedCards(state).asSequence()
            .filter {
                state.scheduling[it.id] == null && it.id !in rankedIds &&
                    Growth.isIntroducible(state, it) && eligible(it)
            }
            .take(limit - ranked.size)
        return ranked + upcoming
    }
}
