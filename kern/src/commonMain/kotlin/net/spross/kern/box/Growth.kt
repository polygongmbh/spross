package net.spross.kern.box

import kotlin.math.max
import kotlin.math.min
import kotlin.time.Instant
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.CardPhase

/** New-card candidates: automatic unlock fast-path phrases separate from the rest. */
internal data class NewCandidates(
    val unlockedPhrases: List<String>,
    val newCards: List<String>,
) {
    companion object {
        val empty = NewCandidates(emptyList(), emptyList())
    }
}

internal object Growth {

    /**
     * Health gate: projected post-session backlog < dueSoftCap, and relearning
     * share < 20 % of active cards — the sub-gate applies only once active >= 10
     * (else it passes; day-one bootstrap).
     */
    fun healthGateOpen(state: BoxState, nowEpochMillis: Long): Boolean {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        val active = Inventory.active(state)
        val dueCount = active.count { it.due != null && it.due <= now }
        val projectedBacklog = dueCount - min(dueCount, state.config.sessionCap)
        if (projectedBacklog >= state.config.dueSoftCap) return false
        if (active.size >= 10) {
            val relearning = active.count { it.phase == CardPhase.Relearning }
            if (relearning >= 0.2 * active.size) return false
        }
        return true
    }

    /** Free learning-pool slots, ignoring the health gate. */
    fun learningPoolBudget(state: BoxState): Int =
        max(0, state.config.maxLearning - Inventory.cardsInLearning(state).size)

    /** Budget after the health gate: 0 when the gate is closed. */
    fun gatedNewBudget(state: BoxState, nowEpochMillis: Long): Int =
        if (healthGateOpen(state, nowEpochMillis)) learningPoolBudget(state) else 0

    // Phrase unlock reads component schedules RAW BY CARD ID — join- and
    // source-independent, so a source switch can never re-lock a phrase.
    fun isComponentStable(state: BoxState, componentId: String): Boolean {
        val sched = state.scheduling[componentId] ?: return false
        if (sched.suspended || sched.phase != CardPhase.Review) return false
        return (sched.memory?.stability ?: 0.0) >= state.config.phraseUnlockStability
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
     * Enqueued cards lead and bypass the health gate; with the gate open,
     * unlocked phrases enter next, then seed-order cards (locked phrases wait
     * for their components).
     */
    fun newCandidates(
        state: BoxState,
        budget: Int,
        gateOpen: Boolean,
        capacity: Int,
    ): NewCandidates {
        var slots = min(budget, capacity)
        if (slots <= 0) return NewCandidates.empty

        val taken = mutableSetOf<String>()
        val unlockedPhrases = mutableListOf<String>()
        val newCards = mutableListOf<String>()

        // 1. Enqueued lead — within the pool throttle, bypassing the health gate.
        for (id in enqueuedEligible(state)) {
            if (slots <= 0) break
            if (!taken.add(id)) continue
            newCards += id
            slots -= 1
        }
        if (!gateOpen) return NewCandidates(unlockedPhrases, newCards)

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
