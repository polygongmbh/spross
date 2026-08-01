package net.spross.kern.session

import kotlin.math.max
import kotlin.math.min
import net.spross.kern.box.BoxState
import net.spross.kern.box.Growth
import net.spross.kern.box.Inventory
import net.spross.kern.box.dayKey
import net.spross.kern.box.endOfTomorrow
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
     * A round shorter than this is not worth sitting down for: two or three cards
     * presented as the day's work read as the app having nothing to offer, so a short
     * round is filled out with reviews pulled forward instead. Also what a day has to
     * hold before it counts as worked (see [workedARound]).
     */
    const val SESSION_FLOOR_CARDS: Int = 7

    /**
     * A round introduces at most this many unseen cards — a round's worth of first
     * sights, the same size as [SESSION_FLOOR_CARDS] measures a round to be.
     * The rest is not withdrawn, only deferred — the next round offers it again.
     * This and the health gate are the whole of the intake policy: nothing throttles
     * on how shaky the material is (`docs/growth-evidence.md`).
     */
    const val NEW_CARDS_PER_ROUND: Int = 7

    /**
     * Today's plan: due cards oldest-first (ties by id), review slots capped at
     * `sessionCap − growthReserve`, then new candidates fill the remaining capacity —
     * enqueued cards lead (health-gate bypass), unlocked phrases next, then seed-order
     * cards. A short round is filled out to [SESSION_FLOOR_CARDS] (see [fillOut]).
     *
     * A quiet day — nothing due at all — is built rather than found: half the round is
     * held for cards that come due tomorrow, and new words take the rest. See [reservedForTomorrow].
     */
    fun composeSession(state: BoxState, nowEpochMillis: Long, tzId: String): SessionPlan {
        val cap = state.config.sessionCap
        val due = Inventory.due(state, nowEpochMillis)
        val gateOpen = Growth.healthGateOpen(state, nowEpochMillis)
        val newBudget = if (due.isEmpty()) {
            min(SESSION_FLOOR_CARDS, cap) - reservedForTomorrow(state, nowEpochMillis, tzId)
        } else {
            NEW_CARDS_PER_ROUND
        }

        // The day is done once nothing is due and the learner has worked a round's worth:
        // "nothing more right now" is a real answer, and manufacturing another round would
        // turn every visit into a treadmill. Cards the learner PACKED themselves still enter —
        // that is an explicit ask, not automatic growth.
        val dayDone = due.isEmpty() && workedARound(state, nowEpochMillis, tzId)
        val autoOpen = gateOpen && !dayDone

        // Reserve headroom only for new work that will actually appear — a box with
        // nothing left to introduce hands every slot back to the review queue. Costs a
        // second candidate pass, which keeps the precedence inside [Growth.newCandidates]
        // as the one place that decides WHICH cards enter.
        val available = Growth.newCandidates(state, newBudget, autoOpen, capacity = cap).count
        val growthReserve = min(available, GROWTH_RESERVE_CARDS)
        val reviews = due.take(max(0, cap - growthReserve))

        val candidates = Growth.newCandidates(
            state,
            budget = newBudget,
            gateOpen = autoOpen,
            capacity = max(0, cap - reviews.size),
        )
        val plan = SessionPlan(
            reviews = reviews.map { it.cardId },
            ahead = emptyList(),
            unlockedPhrases = candidates.unlockedPhrases,
            newCards = candidates.newCards,
            joinStamp = state.joinStamp,
        )
        return if (dayDone) plan else fillOut(state, plan, nowEpochMillis)
    }

    /**
     * How much of a quiet round is held back for cards that come due tomorrow, at most half.
     *
     * Pulling tomorrow's card forward costs almost no spacing; pulling one due in three weeks
     * burns real spacing, so the reservation only counts what sits inside that horizon.
     * Nothing due tomorrow also means nothing was recently missed — then the round is new
     * words alone, which is the honest offer on a box that has caught up.
     */
    private fun reservedForTomorrow(state: BoxState, nowEpochMillis: Long, tzId: String): Int {
        val horizon = endOfTomorrow(nowEpochMillis, tzId)
        val soon = Inventory.scheduledAhead(state).count { it.due!! <= horizon }
        return min(soon, min(SESSION_FLOOR_CARDS, state.config.sessionCap) / 2)
    }

    /** A round's worth of answers is what closes a day — one tap never did. */
    private fun workedARound(state: BoxState, nowEpochMillis: Long, tzId: String): Boolean =
        (state.dailyStats[dayKey(nowEpochMillis, tzId)]?.reviews ?: 0) >=
            min(SESSION_FLOOR_CARDS, state.config.sessionCap)

    /**
     * Fill a short round out with reviews pulled forward, soonest due first — honest FSRS
     * reviews (short elapsed → small stability gain), never new words: those are already
     * capped at [NEW_CARDS_PER_ROUND] on purpose, and the floor must not talk over that.
     *
     * Soonest-first means tomorrow's cards come first on their own; reaching past them
     * happens only when there is nothing else left to fill the round with, which is the
     * exhausted-catalog case rather than an everyday one.
     */
    private fun fillOut(state: BoxState, plan: SessionPlan, nowEpochMillis: Long): SessionPlan {
        val target = min(SESSION_FLOOR_CARDS, state.config.sessionCap)
        if (plan.cardCount >= target) return plan
        val taken = plan.queue.toSet()
        val ahead = Inventory.scheduledAhead(state)
            .filter { it.cardId !in taken && it.due!!.toEpochMilliseconds() > nowEpochMillis }
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
            .take(NEW_CARDS_PER_ROUND)

        val dueCards = due.mapTo(mutableSetOf()) { it.cardId }
        val remaining = max(0, cap - due.size - enqueuedNew.size)
        val ahead = Inventory.scheduledAhead(state)
            .filter { it.cardId !in dueCards }
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
            budget = NEW_CARDS_PER_ROUND,
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
