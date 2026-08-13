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
     * on switch-back. The learner's own words re-join under the new pair by the same
     * coverage rule the catalog uses.
     */
    fun rejoin(state: BoxState, cards: List<Card>, joinStamp: JoinStamp): BoxState =
        state.copy(
            cards = (cards + OwnWords.cards(state.ownWords, joinStamp.source, joinStamp.target))
                .associateBy { it.id },
            joinStamp = joinStamp,
        )

    /**
     * Destructive fresh start: every schedule, queue entry and tally goes; the join,
     * the configuration and the learner's own words stay. Their words are content
     * they authored, not progress — clearing what the box KNOWS must never delete
     * what it HOLDS.
     */
    fun reset(state: BoxState): BoxState = BoxState(
        config = state.config,
        cards = state.cards,
        joinStamp = state.joinStamp,
        ownWords = state.ownWords,
    )

    /**
     * Take in a word the learner wrote and pack it. Packing is not a separate step:
     * they named this word themselves, so waiting for growth to walk to it would be
     * absurd. A word already known by id, or one the current profile cannot join
     * (written in only one of its two languages), leaves the state untouched.
     */
    fun addOwnWord(state: BoxState, word: OwnWord): BoxState {
        require(OwnWords.owns(word.id)) { "own word id must start with \"${OwnWords.ID_PREFIX}\"" }
        if (state.ownWords.any { it.id == word.id }) return state
        val words = state.ownWords + word
        val next = state.copy(ownWords = words, cards = rebuilt(state, words))
        return if (next.cards[word.id] == null) next else enqueue(next, listOf(word.id))
    }

    /**
     * Take a word the learner wrote back out, with its schedule and its place in the
     * queue. Catalog words are never removable this way — a word the box did not get
     * from the learner is not theirs to delete, only to suspend ([setSuspended]).
     */
    fun removeOwnWord(state: BoxState, wordId: String): BoxState {
        if (state.ownWords.none { it.id == wordId }) return state
        val words = state.ownWords.filterNot { it.id == wordId }
        return state.copy(
            ownWords = words,
            cards = rebuilt(state, words),
            scheduling = state.scheduling - wordId,
            enqueued = state.enqueued.filterNot { it == wordId },
        )
    }

    /** The card map with every own-word card re-derived; the catalog half is untouched. */
    private fun rebuilt(state: BoxState, words: List<OwnWord>): Map<String, Card> =
        state.cards.filterKeys { !OwnWords.owns(it) } +
            OwnWords.cards(words, state.joinStamp.source, state.joinStamp.target)
                .associateBy { it.id }

    /**
     * Append card ids to the user priority queue. Enqueuing a phrase auto-prepends
     * its missing (unscheduled) components ahead of it. Unknown/non-joining ids,
     * already-scheduled cards, and duplicates are skipped. Enqueued cards lead
     * composition but respect the per-round cap: a pack enrolls and drips in at the
     * growth rate, it is not dumped at once.
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
     * eligibility at answer time ([AnswerStatus.DroppedIneligible]) — plans
     * outlive phase changes. Any
     * Again past introduction counts a lapse; 2 lapses auto-suspend the card (leech). Non-joining
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
            consolidated = state.consolidatedCrossed[day] ?: 0,
            activeCount = Inventory.active(state).size,
        )
        // why: yyyy-MM-dd keys compare chronologically as strings, so pruning is a
        // plain string comparison.
        val cutoff = localDate(nowEpochMillis, tzId).minus(59, DateTimeUnit.DAY).toString()
        return state.copy(
            dailyStats = state.dailyStats + (day to folded),
            newIntroduced = state.newIntroduced.filterKeys { it >= cutoff },
            consolidatedCrossed = state.consolidatedCrossed.filterKeys { it >= cutoff },
        )
    }

    /** Joined, active card ids due at `now`, oldest day first — the drain-loop feed. */
    fun dueNow(state: BoxState, nowEpochMillis: Long): List<String> =
        Inventory.due(state, nowEpochMillis).map { it.cardId }

    fun statistics(state: BoxState, nowEpochMillis: Long, tzId: String): BoxStatistics =
        Statistics.statistics(state, nowEpochMillis, tzId)

    /** What the learner did today, live from the logs and the day counters. */
    fun today(state: BoxState, nowEpochMillis: Long, tzId: String): TodayReport =
        todayReport(state, nowEpochMillis, tzId)

    /**
     * Where every card of the join stands on the growth ladder, in seed order —
     * see [boxGrowth]. The whole-box read behind a surface that draws the box
     * itself, rather than the counts [statistics] aggregates it into.
     */
    fun growth(state: BoxState, nowEpochMillis: Long, tzId: String): List<CardGrowth> =
        boxGrowth(state, nowEpochMillis, tzId)

    /**
     * Has this card landed? See [Statistics.isConsolidated] — the one threshold
     * behind the fresh/consolidated stats split, phrase unlock, the drill pools and
     * the presentation support a word gets while it is still on its way in.
     * Unknown ids read as false: a card with no schedule has certainly not landed.
     */
    fun isConsolidated(state: BoxState, cardId: String): Boolean =
        state.scheduling[cardId]?.let { Statistics.isConsolidated(state, it) } ?: false

    /**
     * Every consolidated card id, in seed order — the words the box may hand to a
     * drill that practices only material the learner already holds (letter-drill
     * dictation is the first caller).
     *
     * Which words those are is an ENGINE rule, not a caller's filter: this reads
     * through [Inventory.active] like every other inventory query, so a suspended,
     * non-joining, or never-scheduled card is never offered, and a lapse drops a
     * card out on its own — [Statistics.isConsolidated] wants the Review phase, and
     * a lapsed card sits in Relearning until it earns the stability back. Restating
     * that predicate app-side would let two platforms drift on what "known" means.
     *
     * Seed order, not the due shuffle: a drill samples with its own `Random`, so it
     * wants a list that is stable under it rather than a second ordering rule.
     * The query is read-only — drills stay stateless and never book a review.
     */
    fun consolidatedCardIds(state: BoxState): List<String> =
        Inventory.active(state)
            .filter { Statistics.isConsolidated(state, it) }
            .map { state.cards.getValue(it.cardId) }
            .sortedWith(Inventory.seedOrder)
            .map { it.id }

    /** See [Exposure.exposureCards]; `nowEpochMillis` reserved for future due-weighting. */
    fun exposureCards(
        state: BoxState,
        nowEpochMillis: Long,
        limit: Int,
        eligible: (Card) -> Boolean = { true },
    ): List<Card> = Exposure.exposureCards(state, limit, eligible)
}
