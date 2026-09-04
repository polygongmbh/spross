package net.spross.kern.box

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling
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
        reportedIssues = state.reportedIssues,
        lastExportAt = state.lastExportAt,
    )

    /**
     * Take in a word the learner wrote and pack it. Packing is not a separate step:
     * they named this word themselves, so waiting for growth to walk to it would be
     * absurd. A word already known by id leaves the state untouched.
     *
     * One written in only ONE of the profile's languages is still taken in — it is a
     * SUGGESTION ([OwnWord]) — but joins no card and so is never packed or scheduled.
     */
    fun addOwnWord(state: BoxState, word: OwnWord, nowEpochMillis: Long): BoxState {
        require(OwnWords.owns(word.id)) { "own word id must start with \"${OwnWords.ID_PREFIX}\"" }
        if (state.ownWords.any { it.id == word.id }) return state
        // why: the engine stamps it, not the caller — an app that had to remember
        // would be the one place a suggestion's age could go wrong, and it is the
        // only date a suggestion ever gets (it earns no schedule to carry one).
        val words = state.ownWords + word.copy(
            addedAt = Instant.fromEpochMilliseconds(nowEpochMillis),
        )
        val next = state.copy(ownWords = words, cards = rebuilt(state, words))
        return if (next.cards[word.id] == null) next else enqueue(next, listOf(word.id))
    }

    /**
     * Rewrite a word the learner wrote — its texts, its picture, its kind — KEEPING its
     * id, and with the id its schedule, its queue slot and anything filed against it.
     * That is the whole difference from [removeOwnWord] + [addOwnWord]: a typo fixed
     * should not cost the learner the progress they made on the word.
     *
     * [OwnWord.addedAt] is the stored one, never the incoming: it records when the word
     * was WRITTEN, and editing it is not writing it again. An edit that fills in the
     * missing half turns a suggestion into a card and packs it, exactly as [addOwnWord]
     * would have; one that empties a half turns the card back into a suggestion, and its
     * schedule stays behind untouched, inert, the same way a source switch leaves one.
     * An id the learner never wrote leaves the state alone.
     */
    fun updateOwnWord(state: BoxState, word: OwnWord): BoxState {
        val stored = state.ownWords.firstOrNull { it.id == word.id } ?: return state
        val words = state.ownWords.map {
            if (it.id == word.id) word.copy(addedAt = stored.addedAt) else it
        }
        val next = state.copy(ownWords = words, cards = rebuilt(state, words))
        val joinsNow = next.cards[word.id] != null
        return if (joinsNow && next.scheduling[word.id] == null) {
            enqueue(next, listOf(word.id))
        } else {
            next
        }
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
            reportedIssues = state.reportedIssues - wordId,
        )
    }

    /**
     * Empty what waits to be sent on: every suggestion, and every report filed
     * ([Feedback.clearableCount] is what that comes to). The learner has handed the lot
     * to whoever maintains the catalog, and neither entry has anything left to do here.
     *
     * A word written in BOTH languages is untouched — it is a card with a schedule and
     * progress on it, not a note to the maintainer. That is the whole line this verb
     * draws, and the reason it is not [reset]: reset clears what the box KNOWS and keeps
     * what the learner WROTE, while this one keeps the studiable words and clears the
     * notes. Neither reaches a catalog word.
     *
     * [BoxState.lastExportAt] stays: it records that a copy was taken, which emptying
     * the outbox does not undo.
     */
    fun clearFeedback(state: BoxState): BoxState {
        val kept = state.ownWords.filterNot {
            it.isSuggestion(state.joinStamp.source, state.joinStamp.target)
        }
        if (kept.size == state.ownWords.size && state.reportedIssues.isEmpty()) return state
        return state.copy(
            ownWords = kept,
            cards = rebuilt(state, kept),
            reportedIssues = emptyMap(),
        )
    }

    /**
     * File a content problem against ONE card: a wrong translation, a synonym the
     * catalog should accept, a prompt that reads badly. [learnerInput] is whatever they
     * had typed as their answer — see [ReportedIssue].
     *
     * Deliberately independent of [setSuspended]: neither verb implies the other, and
     * reporting never changes what the box schedules. Filing again replaces the earlier
     * report; a card the current profile does not join is refused, since a report
     * nobody can resolve to a word is unreadable to whoever would fix it.
     */
    fun reportIssue(
        state: BoxState,
        cardId: String,
        comment: String?,
        learnerInput: String?,
        nowEpochMillis: Long,
    ): BoxState {
        if (state.cards[cardId] == null) return state
        val issue = ReportedIssue(
            cardId = cardId,
            comment = comment?.takeIf { it.isNotBlank() },
            learnerInput = learnerInput?.takeIf { it.isNotBlank() },
            reportedAt = Instant.fromEpochMilliseconds(nowEpochMillis),
        )
        return state.copy(reportedIssues = state.reportedIssues + (cardId to issue))
    }

    /** Withdraw a report; no-op when the card carries none. */
    fun dismissReportedIssue(state: BoxState, cardId: String): BoxState {
        if (cardId !in state.reportedIssues) return state
        return state.copy(reportedIssues = state.reportedIssues - cardId)
    }

    /**
     * Record that the learner has just copied or mailed their words and reports out —
     * what a later "only what is new" measures against ([Feedback], [BoxState.lastExportAt]).
     * Taking the whole lot rather than the new part still marks it: either way they
     * have now seen everything up to this moment.
     *
     * A [FeedbackScope.Outbox] export leaves the stamp where it is. It went out without the
     * finished word pairs, so a "only what is new" measured from it would carry them never.
     */
    fun markExported(state: BoxState, nowEpochMillis: Long, scope: FeedbackScope): BoxState =
        if (scope == FeedbackScope.Outbox) state
        else state.copy(lastExportAt = Instant.fromEpochMilliseconds(nowEpochMillis))

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

    /**
     * Take a packed word back out of the queue before a round has brought it in — the
     * reverse of [enqueue]. A card a round already introduced has left the queue on its
     * own (`Answering.answer`), so this is a no-op then, and a no-op for any id the queue
     * never held. Unpacking a phrase leaves its auto-prepended component words queued —
     * they are separate cards the learner may still want.
     */
    fun dequeue(state: BoxState, cardId: String): BoxState {
        if (cardId !in state.enqueued) return state
        return state.copy(enqueued = state.enqueued.filterNot { it == cardId })
    }

    /**
     * Take a whole area's queued words back out at once — the reverse of packing a shelf,
     * and [dequeue] applied to every card [BoxBrowser.dequeueableCardIds] lists for it.
     * A card belonging to another area that rode in as a phrase's component is untouched,
     * same as a single [dequeue] leaves it: it is a separate word the learner may still want.
     */
    fun dequeueArea(state: BoxState, area: String): BoxState {
        val leaving = state.enqueued.filterTo(mutableSetOf()) { state.cards[it]?.area == area }
        if (leaving.isEmpty()) return state
        return state.copy(enqueued = state.enqueued.filterNot { it in leaving })
    }

    /**
     * Drop ONE card's schedule: it goes back to New, as if never introduced, and the box
     * may offer it again. The learner's escape hatch for a word they answered wrong on
     * purpose, or one whose meaning they only now understand.
     *
     * Unlike [reset] this is about ONE card, and unlike [removeOwnWord] it keeps the
     * card — a catalog word is not theirs to delete, and their own word survives its
     * progress being cleared. Anything filed against it stays: a report is about the
     * CONTENT, and forgetting the answers does not make the translation right.
     *
     * The day counters ([BoxState.newIntroduced], [BoxState.consolidatedCrossed]) are
     * deliberately left alone. They record what the learner DID on a day, not what the
     * box holds now — the introduction really did happen — and they carry no card ids to
     * undo the right one by. No-op when the id has no schedule.
     */
    fun forget(state: BoxState, cardId: String): BoxState {
        if (state.scheduling[cardId] == null) return state
        return state.copy(scheduling = state.scheduling - cardId)
    }

    /**
     * Suspend or revive ONE card. A card the box has never asked can be suspended too —
     * the learner meets a word mid-round and wants no more of it, and being told to
     * answer it first would be absurd. It gets a New schedule carrying nothing but the
     * suspension, which is inert everywhere: New satisfies the phase/memory/due
     * invariant, and a suspended card is filtered out of every inventory read.
     *
     * Reviving one that was never answered DROPS that schedule rather than clearing its
     * flag, because growth only ever reaches a card with no schedule at all
     * ([Growth.isIntroducible]) — leaving the husk behind would make waking a word the
     * one way to lose it for good. Unknown ids leave the state alone.
     */
    fun setSuspended(
        state: BoxState,
        cardId: String,
        suspended: Boolean,
        nowEpochMillis: Long,
    ): BoxState {
        val sched = state.scheduling[cardId]
        if (sched == null) {
            if (!suspended || state.cards[cardId] == null) return state
            val fresh = CardScheduling(
                cardId = cardId,
                addedAt = Instant.fromEpochMilliseconds(nowEpochMillis),
                suspended = true,
            )
            return state.copy(scheduling = state.scheduling + (cardId to fresh))
        }
        if (!suspended && sched.reviewCount == 0 && sched.phase == CardPhase.New) {
            return state.copy(scheduling = state.scheduling - cardId)
        }
        return state.copy(
            scheduling = state.scheduling + (cardId to sched.copy(suspended = suspended)),
        )
    }

    /**
     * Apply one answer to a card. Introduction = the card's first answer: creates its
     * schedule, counts it introduced, and dequeues it. Any Again past introduction
     * counts a lapse — tracked for drill/listening scoring, never auto-suspending;
     * a lapse grows the wait before its next try instead of repeating the same short
     * one, new word or lapsed alike ([net.spross.kern.fsrs.FsrsScheduler]).
     * An unknown id leaves the state untouched.
     */
    fun answer(
        state: BoxState,
        cardId: String,
        rating: Rating,
        nowEpochMillis: Long,
        tzId: String,
    ): BoxState = Answering.answer(state, cardId, rating, nowEpochMillis, tzId)

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

    /** How many cards stand due — [dueNow]'s size, without composing its order. */
    fun dueCount(state: BoxState, nowEpochMillis: Long): Int =
        Inventory.dueCount(state, nowEpochMillis)

    /**
     * [otherLanguagesDailyStats]: `dailyStats` from every OTHER target-language box
     * the learner has (each keyed by its own day, THIS state's own [BoxState.dailyStats]
     * added in here) — the streak counts the day, not which language it was spent on.
     * Everything else stays scoped to this join.
     */
    fun statistics(
        state: BoxState,
        nowEpochMillis: Long,
        tzId: String,
        otherLanguagesDailyStats: List<Map<String, DayStats>> = emptyList(),
    ): BoxStatistics = Statistics.statistics(state, nowEpochMillis, tzId, otherLanguagesDailyStats)

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
     * Where ONE card stands, asked by name — see [cardGrowthOf]. Null where the join
     * does not carry the id. For a surface holding a single word (a Box row's long
     * press), which would otherwise walk [growth] for one entry.
     */
    fun cardGrowth(
        state: BoxState,
        cardId: String,
        nowEpochMillis: Long,
        tzId: String,
    ): CardGrowth? = cardGrowthOf(state, cardId, nowEpochMillis, tzId)

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
