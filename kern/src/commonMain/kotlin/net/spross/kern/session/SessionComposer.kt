package net.spross.kern.session

import kotlin.math.max
import kotlin.math.min
import net.spross.kern.box.BoxState
import net.spross.kern.box.Growth
import net.spross.kern.box.Inventory
import net.spross.kern.model.SessionPlan

/**
 * Session composition over [BoxState]. Pure: same state + now → same plan.
 *
 * All entries are card ids; composition is role-agnostic — production vs
 * recognition is resolved at render time from the card's log count
 * ([net.spross.kern.model.presentationRole]). Plans carry the state's join
 * stamp; the app recomposes when it goes stale.
 */
object SessionComposer {

    /** Up to this many session slots are reserved so a full due queue can't starve growth. */
    private const val GROWTH_RESERVE_CARDS = 5

    /**
     * Today's plan: due cards oldest-first (ties by id), review slots capped at
     * `sessionCap − growthReserve`, then new candidates fill the remaining capacity —
     * enqueued cards lead (health-gate bypass, budget-throttled), unlocked phrases
     * next, then seed-order cards.
     */
    fun composeSession(state: BoxState, nowEpochMillis: Long): SessionPlan {
        val cap = state.config.sessionCap
        val due = Inventory.due(state, nowEpochMillis)
        val loadBudget = Growth.newBudget(state)
        val gateOpen = Growth.healthGateOpen(state, nowEpochMillis)
        val autoBudget = if (gateOpen) loadBudget else 0

        // Reserve headroom only for new work that will actually appear: automatic
        // cards (health-gated) or enqueued ones (within the new-word budget).
        // A closed gate with nothing packed reserves nothing.
        val enqueuedReady = min(Growth.enqueuedEligible(state).size, loadBudget)
        val growthReserve = min(max(autoBudget, enqueuedReady), GROWTH_RESERVE_CARDS)
        val reviews = due.take(max(0, cap - growthReserve))

        val candidates = Growth.newCandidates(
            state,
            budget = loadBudget,
            gateOpen = gateOpen,
            capacity = max(0, cap - reviews.size),
        )
        return SessionPlan(
            reviews = reviews.map { it.cardId },
            unlockedPhrases = candidates.unlockedPhrases,
            newCards = candidates.newCards,
            joinStamp = state.joinStamp,
        )
    }

    /**
     * On-demand extra round (user agency): everything due, then enqueued cards
     * within the new-word budget (health-gate bypass), then review-ahead by soonest due so
     * the round is never empty while the box holds active cards — early reviews are
     * honest FSRS reviews (short elapsed → small stability gain). NO automatic
     * seed-order growth: unrequested growth stays governed by the daily budget and
     * health gate, so `unlockedPhrases` is always empty.
     */
    fun composeExtraSession(state: BoxState, nowEpochMillis: Long): SessionPlan {
        val cap = state.config.sessionCap
        val due = Inventory.due(state, nowEpochMillis)
        val enqueuedNew = Growth.enqueuedEligible(state)
            .take(Growth.newBudget(state))

        val dueCards = due.mapTo(mutableSetOf()) { it.cardId }
        val remaining = max(0, cap - due.size - enqueuedNew.size)
        val ahead = Inventory.active(state)
            .filter { it.due != null && it.cardId !in dueCards }
            .sortedWith(compareBy({ it.due }, { it.cardId }))
            .take(remaining)

        return SessionPlan(
            reviews = (due + ahead).take(cap).map { it.cardId },
            unlockedPhrases = emptyList(),
            newCards = enqueuedNew,
            joinStamp = state.joinStamp,
        )
    }

    /**
     * Endless-practice refill: due cards (oldest first) plus new candidates within the
     * new-word budget and health gate. Nothing is ever pulled ahead of its due time —
     * spacing is preserved, and an empty plan legitimately means "come back later".
     */
    fun composeEndless(state: BoxState, nowEpochMillis: Long): SessionPlan {
        val cap = state.config.sessionCap
        val due = Inventory.due(state, nowEpochMillis).take(cap)
        val candidates = Growth.newCandidates(
            state,
            budget = Growth.newBudget(state),
            gateOpen = Growth.healthGateOpen(state, nowEpochMillis),
            capacity = max(0, cap - due.size),
        )
        return SessionPlan(
            reviews = due.map { it.cardId },
            unlockedPhrases = candidates.unlockedPhrases,
            newCards = candidates.newCards,
            joinStamp = state.joinStamp,
        )
    }
}
