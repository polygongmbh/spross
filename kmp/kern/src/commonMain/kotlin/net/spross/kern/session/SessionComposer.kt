package net.spross.kern.session

import kotlin.math.max
import kotlin.math.min
import net.spross.kern.box.BoxState
import net.spross.kern.box.Growth
import net.spross.kern.box.Inventory
import net.spross.kern.model.SessionPlan
import net.spross.kern.model.UnitKey
import net.spross.kern.model.UnitScheduling

/**
 * Session composition over [BoxState] (contract §6). Pure: same state + now → same plan.
 *
 * Workload is denominated in UNITS; introductions honor the CONCEPT pool budget.
 * Every composed plan carries AT MOST ONE unit per card — a due or new sibling defers
 * to the next composition. Only the drain loop ([net.spross.kern.box.BoxEngine.dueNow])
 * is exempt: learning steps of both roles may legitimately interleave there.
 * Plans carry the state's join stamp; the app recomposes when it goes stale.
 */
object SessionComposer {

    /** Up to this many session slots are reserved so a full due queue can't starve growth. */
    private const val GROWTH_RESERVE_UNITS = 5

    /**
     * Today's plan: due units oldest-first (ties by key), review slots capped at
     * `sessionCap − growthReserve`, then new candidates fill the remaining capacity —
     * enqueued produce units lead (health-gate bypass, pool-throttled), unlocked
     * phrases next, then seed-order units (recognize backfill of graduated material
     * naturally leads via its lower seed index).
     */
    fun composeSession(state: BoxState, nowEpochMillis: Long): SessionPlan {
        val cap = state.config.sessionCap
        val due = dueOnePerCard(state, nowEpochMillis)
        val loadBudget = Growth.learningPoolBudget(state)
        val gateOpen = Growth.healthGateOpen(state, nowEpochMillis)
        val autoBudget = if (gateOpen) loadBudget else 0

        // Reserve headroom only for new work that will actually appear: automatic
        // units (health-gated) or enqueued ones (within the concept throttle).
        // A closed gate with nothing packed reserves nothing.
        val enqueuedReady = min(Growth.enqueuedEligible(state).size, loadBudget)
        val growthReserve = min(max(autoBudget, enqueuedReady), GROWTH_RESERVE_UNITS)
        val reviews = due.take(max(0, cap - growthReserve))

        val candidates = Growth.newCandidates(
            state,
            conceptBudget = loadBudget,
            gateOpen = gateOpen,
            capacityUnits = max(0, cap - reviews.size),
            excludedCards = reviews.mapTo(mutableSetOf()) { it.cardId },
            onePerCard = true,
        )
        return SessionPlan(
            reviews = reviews.map { it.key },
            unlockedPhrases = candidates.unlockedPhrases,
            newUnits = candidates.newUnits,
            joinStamp = state.joinStamp,
        )
    }

    /**
     * On-demand extra round (user agency): everything due, then enqueued produce units
     * within the pool budget (health-gate bypass), then review-ahead by soonest due so
     * the round is never empty while the box holds active units — early reviews are
     * honest FSRS reviews (short elapsed → small stability gain). NO automatic
     * seed-order growth: unrequested growth stays governed by the daily budget and
     * health gate, so `unlockedPhrases` is always empty.
     */
    fun composeExtraSession(state: BoxState, nowEpochMillis: Long): SessionPlan {
        val cap = state.config.sessionCap
        val due = dueOnePerCard(state, nowEpochMillis)
        val enqueuedNew = Growth.enqueuedEligible(state)
            .take(Growth.learningPoolBudget(state))
            .map { UnitKey.produce(it).encoded }

        val dueCards = due.mapTo(mutableSetOf()) { it.cardId }
        val remaining = max(0, cap - due.size - enqueuedNew.size)
        val ahead = Inventory.active(state)
            .filter { it.due != null && it.cardId !in dueCards }
            .sortedWith(compareBy({ it.due }, { it.key }))
            .let(::onePerCard)
            .take(remaining)

        return SessionPlan(
            reviews = (due + ahead).take(cap).map { it.key },
            unlockedPhrases = emptyList(),
            newUnits = enqueuedNew,
            joinStamp = state.joinStamp,
        )
    }

    /**
     * Endless-practice refill: due units (oldest first) plus new candidates within the
     * pool budget and health gate. Nothing is ever pulled ahead of its due time —
     * spacing is preserved, and an empty plan legitimately means "come back later".
     */
    fun composeEndless(state: BoxState, nowEpochMillis: Long): SessionPlan {
        val cap = state.config.sessionCap
        val due = dueOnePerCard(state, nowEpochMillis).take(cap)
        val candidates = Growth.newCandidates(
            state,
            conceptBudget = Growth.learningPoolBudget(state),
            gateOpen = Growth.healthGateOpen(state, nowEpochMillis),
            capacityUnits = max(0, cap - due.size),
            excludedCards = due.mapTo(mutableSetOf()) { it.cardId },
            onePerCard = true,
        )
        return SessionPlan(
            reviews = due.map { it.key },
            unlockedPhrases = candidates.unlockedPhrases,
            newUnits = candidates.newUnits,
            joinStamp = state.joinStamp,
        )
    }

    /** Due units with the sibling of an earlier (older-due) unit of the same card dropped. */
    private fun dueOnePerCard(state: BoxState, nowEpochMillis: Long): List<UnitScheduling> =
        onePerCard(Inventory.due(state, nowEpochMillis))

    private fun onePerCard(units: List<UnitScheduling>): List<UnitScheduling> {
        val seen = mutableSetOf<String>()
        return units.filter { seen.add(it.cardId) }
    }
}
