package net.spross.kern.box

import kotlin.math.min
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind

/** New-card candidates: automatic unlock fast-path phrases separate from the rest. */
internal data class NewCandidates(
    val unlockedPhrases: List<String>,
    val newCards: List<String>,
) {
    val count: Int get() = unlockedPhrases.size + newCards.size

    companion object {
        val empty = NewCandidates(emptyList(), emptyList())
    }
}

internal object Growth {

    // Phrase unlock reads component schedules RAW BY CARD ID — join- and
    // source-independent, so a source switch can never re-lock a phrase.
    // Uses the stricter isConsolidated (not isSettled/budget's bar): a phrase
    // should wait for its components to have genuinely landed.
    fun isComponentStable(state: BoxState, componentId: String): Boolean {
        val sched = state.scheduling[componentId] ?: return false
        return !sched.suspended && Statistics.isConsolidated(state, sched)
    }

    /** Zero-component phrases never take the fast path (they follow seed order). */
    fun isPhraseUnlocked(state: BoxState, card: Card): Boolean =
        card.kind == CardKind.Phrase &&
            card.components.isNotEmpty() &&
            card.components.all { isComponentStable(state, it) }

    /** Whether the card may enter at all: word, component-free phrase, or unlocked phrase. */
    fun isIntroducible(state: BoxState, card: Card): Boolean =
        card.kind != CardKind.Phrase || card.components.isEmpty() || isPhraseUnlocked(state, card)

    /** Enqueued card ids that could enter now: joined, unscheduled, not locked. */
    fun enqueuedEligible(state: BoxState): List<String> = state.enqueued.filter { id ->
        val card = state.cards[id] ?: return@filter false
        state.scheduling[id] == null && isIntroducible(state, card)
    }

    /**
     * Candidate selection in card ids, capped by min([budget], [capacity]).
     * Enqueued cards lead and enter whatever [autoGrowth] says — packing a word is an
     * explicit ask. With automatic growth on, unlocked phrases enter next, then
     * seed-order cards (locked phrases wait for their components).
     */
    fun newCandidates(
        state: BoxState,
        budget: Int,
        autoGrowth: Boolean,
        capacity: Int,
    ): NewCandidates {
        var slots = min(budget, capacity)
        if (slots <= 0) return NewCandidates.empty

        val taken = mutableSetOf<String>()
        val unlockedPhrases = mutableListOf<String>()
        val newCards = mutableListOf<String>()

        // 1. Enqueued lead — within the new-word budget, whatever automatic growth does.
        for (id in enqueuedEligible(state)) {
            if (slots <= 0) break
            if (!taken.add(id)) continue
            newCards += id
            slots -= 1
        }
        if (!autoGrowth) return NewCandidates(unlockedPhrases, newCards)

        val unscheduled = Inventory.joinedCards(state).filter { state.scheduling[it.id] == null }

        // 2a. Unlock fast path: component phrases whose components all sit stable.
        for (card in unscheduled) {
            if (slots <= 0) break
            if (card.kind != CardKind.Phrase || card.components.isEmpty()) continue
            if (card.id in taken || !isPhraseUnlocked(state, card)) continue
            unlockedPhrases += card.id
            taken += card.id
            slots -= 1
        }

        // 2b. Automatic seed-order growth.
        for (card in unscheduled) {
            if (slots <= 0) break
            if (card.id in taken || !isIntroducible(state, card)) continue
            newCards += card.id
            taken += card.id
            slots -= 1
        }
        return NewCandidates(unlockedPhrases, newCards)
    }
}
