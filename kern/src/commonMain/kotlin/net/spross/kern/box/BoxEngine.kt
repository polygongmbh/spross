package net.spross.kern.box

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.DayStats
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Rating

/**
 * The growing-box engine: pure functions over [BoxState].
 *
 * Time discipline: every API takes `nowEpochMillis`/`tzId` from the caller —
 * nothing in here ever reads a clock or the device calendar.
 * One schedule per card; every count is denominated in CARDS.
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
     * Append card ids to the user priority queue. Enqueuing a phrase auto-prepends
     * its missing (unscheduled) components ahead of it. Unknown/non-joining ids,
     * already-scheduled cards, and duplicates are skipped. Enqueued cards lead
     * composition and bypass the health gate, but respect the load throttle: a pack
     * enrolls and drips in at the pool rate, it is not dumped at once.
     */
    fun enqueue(state: BoxState, cardIds: List<String>): BoxState {
        val queued = state.enqueued.toMutableList()
        val seen = state.enqueued.toMutableSet()

        fun append(id: String) {
            if (id in seen || state.cards[id] == null) return
            if (state.scheduling[id] != null) return
            queued += id
            seen += id
        }

        for (id in cardIds) {
            state.cards[id]?.components?.forEach(::append)
            append(id)
        }
        return state.copy(enqueued = queued)
    }

    /** Suspend or revive ONE card; no-op when the id has no schedule. */
    fun setSuspended(state: BoxState, cardId: String, suspended: Boolean): BoxState {
        val sched = state.scheduling[cardId] ?: return state
        return state.copy(
            scheduling = state.scheduling + (cardId to sched.copy(suspended = suspended)),
        )
    }

    /**
     * Apply one answer to a card. Introduction = the card's first answer: creates its
     * schedule, counts it introduced, and dequeues it. Introductions re-check
     * eligibility and the pool budget at answer time ([AnswerStatus.DroppedIneligible]
     * / [AnswerStatus.DroppedPoolFull]) — plans outlive phase changes. Review-phase
     * Again answers count lapses; 8 lapses auto-suspend the card (leech). Non-joining
     * or unknown ids are a defined no-op ([AnswerStatus.StaleCard]).
     */
    fun answer(
        state: BoxState,
        cardId: String,
        rating: Rating,
        nowEpochMillis: Long,
        tzId: String,
    ): AnswerOutcome = Answering.answer(state, cardId, rating, nowEpochMillis, tzId)

    /**
     * Fold the session into `dailyStats` and prune `newIntroduced` to the trailing
     * 60 days.
     */
    fun endSession(state: BoxState, reviewsDone: Int, nowEpochMillis: Long, tzId: String): BoxState {
        val day = dayKey(nowEpochMillis, tzId)
        val previous = state.dailyStats[day] ?: DayStats()
        val folded = DayStats(
            reviews = previous.reviews + reviewsDone,
            introduced = state.newIntroduced[day] ?: 0,
            activeCount = Inventory.active(state).size,
        )
        // why: yyyy-MM-dd keys compare chronologically as strings, so pruning is a
        // plain string comparison.
        val cutoff = localDate(nowEpochMillis, tzId).minus(59, DateTimeUnit.DAY).toString()
        return state.copy(
            dailyStats = state.dailyStats + (day to folded),
            newIntroduced = state.newIntroduced.filterKeys { it >= cutoff },
        )
    }

    /** Joined, active card ids due at `now`, oldest day first — the drain-loop feed. */
    fun dueNow(state: BoxState, nowEpochMillis: Long): List<String> =
        Inventory.due(state, nowEpochMillis).map { it.cardId }

    fun statistics(state: BoxState, nowEpochMillis: Long, tzId: String): BoxStatistics =
        Statistics.statistics(state, nowEpochMillis, tzId)

    /**
     * Has this card sat down? See [Statistics.isSitting] — the one threshold
     * behind phrase unlock, the sitting/fresh split, and the presentation
     * support a word gets while it is still landing. Unknown ids read as false:
     * a card with no schedule has certainly not landed.
     */
    fun isSitting(state: BoxState, cardId: String): Boolean =
        state.scheduling[cardId]?.let { Statistics.isSitting(state, it) } ?: false

    /** See [Exposure.exposureCards]; `nowEpochMillis` reserved for future due-weighting. */
    fun exposureCards(state: BoxState, nowEpochMillis: Long, limit: Int): List<Card> =
        Exposure.exposureCards(state, limit)
}
