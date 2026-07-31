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
     * Never quite zero: a session with nothing new in it is a grind on the very
     * words that are not landing, which is the opposite of what a loaded box
     * needs. Breadth is the point — see [newBudget].
     */
    const val TRICKLE_CARDS: Int = 2

    /**
     * Health gate: projected post-session backlog stays under `dueSoftCap`.
     * Time debt only — how much material is unsettled is [newBudget]'s job.
     */
    fun healthGateOpen(state: BoxState, nowEpochMillis: Long): Boolean {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        val dueCount = Inventory.active(state).count { it.due != null && it.due <= now }
        val projectedBacklog = dueCount - min(dueCount, state.config.sessionCap)
        return projectedBacklog < state.config.dueSoftCap
    }

    /** Active cards that have not sat down yet — the words actually in flight. */
    fun unsettledLoad(state: BoxState): Int =
        Inventory.active(state).count { !Statistics.isSettled(state, it) }

    /**
     * Room for new words, ignoring the health gate. Measured against how much of
     * the box is genuinely unsettled rather than how many cards were started:
     * words answered on sight sit down immediately and cost nothing, so a day of
     * easy material opens the way for more, while a pile of words at low
     * stability closes it down to a [TRICKLE_CARDS] of variety.
     */
    fun newBudget(state: BoxState): Int {
        val cap = state.config.maxUnsettled
        // why: a cap of 0 is the learner saying "stop growing" — the trickle is
        // for a box that is merely loaded, and must not talk over that.
        if (cap <= 0) return 0
        return max(TRICKLE_CARDS, cap - unsettledLoad(state))
    }

    /** Budget after the health gate: 0 when the gate is closed. */
    fun gatedNewBudget(state: BoxState, nowEpochMillis: Long): Int =
        if (healthGateOpen(state, nowEpochMillis)) newBudget(state) else 0

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

        // 1. Enqueued lead — within the new-word budget, bypassing the health gate.
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
