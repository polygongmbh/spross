package net.spross.kern.session

import kotlin.math.max
import kotlin.math.min
import net.spross.kern.box.BoxState
import net.spross.kern.box.Growth
import net.spross.kern.box.Inventory
import net.spross.kern.box.dayKey
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
     * A round shorter than this is not worth sitting down for.
     * A loaded box throttles growth to [Growth.TRICKLE_CARDS],
     * and two cards presented as the day's work read as the app having nothing to offer —
     * so a short round is filled out with reviews pulled forward instead.
     */
    const val SESSION_FLOOR_CARDS: Int = 7

    /**
     * A round introduces at most this many unseen cards — a round's worth of first
     * sights, the same size as [SESSION_FLOOR_CARDS] measures a round to be.
     * The new-word budget answers "how much may be in flight", and on a settled box
     * it opens nearly to `maxUnsettled`: a rested learner would be handed twenty
     * first sights at once, which reads as a wall rather than an offer.
     * The rest is not withdrawn, only deferred — the next round offers it again.
     */
    const val NEW_CARDS_PER_ROUND: Int = 7

    /** The new-word budget as one round may spend it: load room, ceilinged at [NEW_CARDS_PER_ROUND]. */
    private fun roundBudget(state: BoxState): Int =
        min(Growth.newBudget(state), NEW_CARDS_PER_ROUND)

    /**
     * Today's plan: due cards oldest-first (ties by id), review slots capped at
     * `sessionCap − growthReserve`, then new candidates fill the remaining capacity —
     * enqueued cards lead (health-gate bypass, budget-throttled), unlocked phrases
     * next, then seed-order cards. A short round is filled out to
     * [SESSION_FLOOR_CARDS] (see [withFloor]).
     */
    fun composeSession(state: BoxState, nowEpochMillis: Long, tzId: String): SessionPlan {
        val cap = state.config.sessionCap
        val due = Inventory.due(state, nowEpochMillis)
        val loadBudget = roundBudget(state)
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
        return withFloor(
            state,
            SessionPlan(
                reviews = reviews.map { it.cardId },
                ahead = emptyList(),
                unlockedPhrases = candidates.unlockedPhrases,
                newCards = candidates.newCards,
                joinStamp = state.joinStamp,
            ),
            nowEpochMillis,
            tzId,
        )
    }

    /**
     * Fill a short round out with reviews pulled forward, soonest due first —
     * honest FSRS reviews (short elapsed → small stability gain), never new words:
     * a box small enough to fall under the floor is usually a box whose growth is throttled
     * on purpose, and the floor must not talk over that.
     *
     * A day with no reps in it yet is filled out even from EMPTY (user ruling 2026-07-30):
     * a learner who has not answered anything today should always find a round to do,
     * so nothing due plus nothing done is a round pulled forward, not a closed box.
     * Once the day HAS been worked, an empty plan stays empty —
     * "nothing more right now" is a real answer,
     * and re-filling it would turn every visit into a treadmill
     * and erase the spacing the whole engine exists to keep.
     */
    private fun withFloor(
        state: BoxState,
        plan: SessionPlan,
        nowEpochMillis: Long,
        tzId: String,
    ): SessionPlan {
        val target = min(SESSION_FLOOR_CARDS, state.config.sessionCap)
        val workedToday = (state.dailyStats[dayKey(nowEpochMillis, tzId)]?.reviews ?: 0) > 0
        if (plan.isEmpty && workedToday) return plan
        if (plan.cardCount >= target) return plan
        val taken = plan.queue.toSet()
        val ahead = Inventory.active(state)
            .filter { it.due != null && it.cardId !in taken }
            .sortedWith(compareBy({ it.due }, { it.cardId }))
            .take(target - plan.cardCount)
        return plan.copy(ahead = ahead.map { it.cardId })
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
        val due = Inventory.due(state, nowEpochMillis).take(cap)
        val enqueuedNew = Growth.enqueuedEligible(state)
            .take(roundBudget(state))

        val dueCards = due.mapTo(mutableSetOf()) { it.cardId }
        val remaining = max(0, cap - due.size - enqueuedNew.size)
        val ahead = Inventory.active(state)
            .filter { it.due != null && it.cardId !in dueCards }
            .sortedWith(compareBy({ it.due }, { it.cardId }))
            .take(remaining)

        return SessionPlan(
            reviews = due.map { it.cardId },
            ahead = ahead.map { it.cardId },
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
            budget = roundBudget(state),
            gateOpen = Growth.healthGateOpen(state, nowEpochMillis),
            capacity = max(0, cap - due.size),
        )
        return SessionPlan(
            reviews = due.map { it.cardId },
            ahead = emptyList(),
            unlockedPhrases = candidates.unlockedPhrases,
            newCards = candidates.newCards,
            joinStamp = state.joinStamp,
        )
    }
}
