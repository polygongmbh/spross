package net.spross.kern.box

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.DayStats
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Rating
import net.spross.kern.model.UnitKey

/**
 * The growing-box engine: pure functions over [BoxState].
 *
 * Time discipline: every API takes `nowEpochMillis`/`tzId` from the caller —
 * nothing in here ever reads a clock or the device calendar.
 * Denomination: growth is counted in CONCEPTS, workload (due/session caps) in UNITS.
 */
object BoxEngine {

    /** Fresh state for a (source, target) join; nothing scheduled yet. */
    fun bootstrap(cards: List<Card>, config: BoxConfig, joinStamp: JoinStamp): BoxState =
        BoxState(config = config, cards = cards.associateBy { it.id }, joinStamp = joinStamp)

    /**
     * Swap the join (source switch or catalog update) keeping every schedule, queue
     * entry, and stat: entries whose card no longer joins turn inert and revive here
     * on switch-back.
     */
    fun rejoin(state: BoxState, cards: List<Card>, joinStamp: JoinStamp): BoxState =
        state.copy(cards = cards.associateBy { it.id }, joinStamp = joinStamp)

    /**
     * Append CONCEPT ids to the user priority queue. Enqueuing a phrase auto-prepends
     * its missing (produce-unscheduled) components ahead of it. Unknown/non-joining ids,
     * already-scheduled concepts, and duplicates are skipped. Enqueued concepts lead
     * composition and bypass the health gate, but respect the load throttle: a pack
     * enrolls and drips in at the pool rate, it is not dumped at once.
     */
    fun enqueue(state: BoxState, conceptIds: List<String>): BoxState {
        val queued = state.enqueued.toMutableList()
        val seen = state.enqueued.toMutableSet()

        fun append(id: String) {
            if (id in seen || state.cards[id] == null) return
            if (state.scheduling[UnitKey.produce(id).encoded] != null) return
            queued += id
            seen += id
        }

        for (id in conceptIds) {
            state.cards[id]?.components?.forEach(::append)
            append(id)
        }
        return state.copy(enqueued = queued)
    }

    /** Suspend or revive ONE unit; no-op when the key has no schedule. */
    fun setSuspended(state: BoxState, unitKey: String, suspended: Boolean): BoxState {
        val sched = state.scheduling[unitKey] ?: return state
        return state.copy(
            scheduling = state.scheduling + (unitKey to sched.copy(suspended = suspended)),
        )
    }

    /**
     * Apply one answer to a unit. Introduction = the unit's first answer: creates its
     * schedule, and on a PRODUCE intro counts the concept introduced and dequeues it.
     * Introductions re-check eligibility and the concept budget at answer time
     * ([AnswerStatus.DroppedIneligible] / [AnswerStatus.DroppedPoolFull]) — plans
     * outlive phase changes. Review-phase Again answers count lapses; 8 lapses
     * auto-suspend the unit (leech). Non-joining or unknown keys are a defined no-op
     * ([AnswerStatus.StaleUnit]).
     */
    fun answer(
        state: BoxState,
        unitKey: String,
        rating: Rating,
        nowEpochMillis: Long,
        tzId: String,
    ): AnswerOutcome = Answering.answer(state, unitKey, rating, nowEpochMillis, tzId)

    /**
     * Fold the session into `dailyStats` (reviews accumulate in UNITS; introduced and
     * activeCount are CONCEPTS) and prune `newIntroduced` to the trailing 60 days.
     */
    fun endSession(state: BoxState, reviewsDone: Int, nowEpochMillis: Long, tzId: String): BoxState {
        val day = dayKey(nowEpochMillis, tzId)
        val previous = state.dailyStats[day] ?: DayStats()
        val folded = DayStats(
            reviews = previous.reviews + reviewsDone,
            introduced = state.newIntroduced[day] ?: 0,
            activeCount = Inventory.activeConceptCount(state),
        )
        // why: yyyy-MM-dd keys compare chronologically as strings, so pruning is a
        // plain string comparison.
        val cutoff = localDate(nowEpochMillis, tzId).minus(59, DateTimeUnit.DAY).toString()
        return state.copy(
            dailyStats = state.dailyStats + (day to folded),
            newIntroduced = state.newIntroduced.filterKeys { it >= cutoff },
        )
    }

    /** Joined, active unit keys due at `now`, oldest first — the drain-loop feed. */
    fun dueNow(state: BoxState, nowEpochMillis: Long): List<String> =
        Inventory.due(state, nowEpochMillis).map { it.key }

    fun statistics(state: BoxState, nowEpochMillis: Long, tzId: String): BoxStatistics =
        Statistics.statistics(state, nowEpochMillis, tzId)

    /** See [Exposure.exposureCards]; `nowEpochMillis` reserved for future due-weighting. */
    fun exposureCards(state: BoxState, nowEpochMillis: Long, limit: Int): List<Card> =
        Exposure.exposureCards(state, limit)
}
