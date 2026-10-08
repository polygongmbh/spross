package net.spross.kern.box

import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Rating
import net.spross.kern.store.SaveScope
import net.spross.kern.store.StoredBox
import net.spross.kern.store.rekeyingPrefixedVerbs

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
     * A pair's box as a launch or a language switch opens it: [saved] joined with the
     * catalog's [cards] and migrated, or bootstrapped where the device holds none.
     * Only a bootstrapped box owes the disk its document — a re-join reproduces itself from
     * what is already stored ([OpenedBox.save]).
     */
    fun open(saved: StoredBox?, cards: List<Card>, joinStamp: JoinStamp): OpenedBox =
        if (saved == null) {
            OpenedBox(bootstrap(cards, BoxConfig.product(), joinStamp), SaveScope.BOX_AND_SNAPSHOTS)
        } else {
            // rekeyingPrefixedVerbs: TODO remove once the app is past 7.0.
            OpenedBox(saved.join(cards, joinStamp).rekeyingPrefixedVerbs(), SaveScope.SNAPSHOTS)
        }

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

    /** Take in and queue a word the learner wrote; see [LearnerContent.addWord]. */
    fun addOwnWord(state: BoxState, word: OwnWord, nowEpochMillis: Long): BoxState =
        LearnerContent.addWord(state, word, nowEpochMillis)

    /** Rewrite a word the learner wrote, keeping its id and progress; see [LearnerContent.updateWord]. */
    fun updateOwnWord(state: BoxState, word: OwnWord): BoxState = LearnerContent.updateWord(state, word)

    /** Take a word the learner wrote back out, schedule and all; see [LearnerContent.removeWord]. */
    fun removeOwnWord(state: BoxState, wordId: String): BoxState = LearnerContent.removeWord(state, wordId)

    /** Move a word the learner wrote onto the catalog word that caught up; see [LearnerContent.mergeWord]. */
    fun mergeOwnWord(state: BoxState, wordId: String, cardId: String): BoxState =
        LearnerContent.mergeWord(state, wordId, cardId)

    /** Empty the outbox, never a studiable word; see [LearnerContent.clearOutbox]. */
    fun clearFeedback(state: BoxState): BoxState = LearnerContent.clearOutbox(state)

    /** File a content problem against ONE card; see [LearnerContent.report]. */
    fun reportIssue(
        state: BoxState,
        cardId: String,
        comment: String?,
        learnerInput: String?,
        nowEpochMillis: Long,
    ): BoxState = LearnerContent.report(state, cardId, comment, learnerInput, nowEpochMillis)

    /** Withdraw a report; no-op when the card carries none. */
    fun dismissReportedIssue(state: BoxState, cardId: String): BoxState =
        LearnerContent.dismissReport(state, cardId)

    /** Record that the learner just took their words and reports out; see [LearnerContent.markExported]. */
    fun markExported(state: BoxState, nowEpochMillis: Long, scope: FeedbackScope): BoxState =
        LearnerContent.markExported(state, nowEpochMillis, scope)

    /**
     * Append card ids to the user priority queue, stored BACK TO FRONT. Queuing a phrase
     * auto-prepends its missing (unscheduled) components ahead of it. Unknown/non-joining ids,
     * already-scheduled cards, and duplicates are skipped. Queued cards lead composition
     * most recently queued first (`Growth.queuedEligible`) but respect the per-round cap: a
     * queued batch enrolls and drips in at the growth rate, it is not dumped at once.
     *
     * [cardIds] is stored in REVERSE so that reversal reads it back in the order it was given:
     * queuing a whole shelf hands in its words in seed order, and without this a shelf queued
     * in one call would introduce backwards — its last word first — the moment anything else
     * was ever queued on top of it. A single word queued on its own is unaffected either way.
     */
    fun queue(state: BoxState, cardIds: List<String>): BoxState {
        val queued = state.queued.toMutableList()
        val seen = state.queued.toMutableSet()

        fun append(id: String) {
            if (id in seen || state.cards[id] == null) return
            if (state.scheduling[id] != null) return
            queued += id
            seen += id
        }

        for (id in cardIds.asReversed()) {
            state.cards[id]?.components?.forEach(::append)
            append(id)
        }
        return state.copy(queued = queued)
    }

    /**
     * Take a queued word back out of the queue before a round has brought it in — the
     * reverse of [queue]. A card a round already introduced has left the queue on its
     * own (`Answering.answer`), so this is a no-op then, and a no-op for any id the queue
     * never held. Unqueuing a phrase leaves its auto-prepended component words queued —
     * they are separate cards the learner may still want.
     */
    fun unqueue(state: BoxState, cardId: String): BoxState {
        if (cardId !in state.queued) return state
        return state.copy(queued = state.queued.filterNot { it == cardId })
    }

    /**
     * Take a whole area's queued words back out at once — the reverse of queuing a shelf,
     * and [unqueue] applied to every card [BoxBrowser.unqueueableCardIds] lists for it.
     * A card belonging to another area that rode in as a phrase's component is untouched,
     * same as a single [unqueue] leaves it: it is a separate word the learner may still want.
     */
    fun unqueueArea(state: BoxState, area: String): BoxState {
        val leaving = state.queued.filterTo(mutableSetOf()) { state.cards[it]?.area == area }
        if (leaving.isEmpty()) return state
        return state.copy(queued = state.queued.filterNot { it in leaving })
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
     * The day loses this card's answers with it: the days are counted off the logs, so a
     * word started over takes its history along. A streak shifting by a word is the price
     * of the box keeping no tally of its own. No-op when the id has no schedule.
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
     * ([Growth.isIntroducible]) — leaving the husk behind would make unsuspending a word the
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
            val fresh = CardScheduling(cardId = cardId, suspended = true)
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
     * schedule, counts it introduced, and unqueues it. Any Again past introduction
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
    ): BoxState = Answering.answer(state, cardId, rating, nowEpochMillis)

    /** How many joined, active cards stand due at `now`, without composing their order. */
    fun dueCount(state: BoxState, nowEpochMillis: Long): Int =
        Inventory.dueCount(state, nowEpochMillis)

    /**
     * [otherLanguagesAnswerDays]: [answerDays] from every OTHER target-language box the
     * learner has, merged; THIS state's own days are counted in here — the streak counts
     * the day, not which language it was spent on. Everything else stays scoped to this join.
     */
    fun statistics(
        state: BoxState,
        nowEpochMillis: Long,
        tzId: String,
        otherLanguagesAnswerDays: Map<String, Int> = emptyMap(),
    ): BoxStatistics = Statistics.statistics(state, nowEpochMillis, tzId, otherLanguagesAnswerDays)

    /**
     * The activity strip's trailing [days], walked with the streak it stands beside, so a
     * day worked in another language shows on the picture as well as in the count.
     */
    fun activityWindow(
        state: BoxState,
        days: Int,
        nowEpochMillis: Long,
        tzId: String,
        otherLanguagesAnswerDays: Map<String, Int> = emptyMap(),
    ): List<ActivityDay> = streakWindow(
        mergeAnswerDays(listOf(otherLanguagesAnswerDays, answerDays(state.scheduling, tzId, state.drillDays))),
        days,
        nowEpochMillis,
        tzId,
    )

    /** A closed drill run's [answers], booked to today for the streak ([answerDays]). */
    fun bookDrillAnswers(state: BoxState, answers: Int, nowEpochMillis: Long, tzId: String): BoxState {
        if (answers <= 0) return state
        val day = dayKey(nowEpochMillis, tzId)
        return state.copy(drillDays = state.drillDays + (day to (state.drillDays[day] ?: 0) + answers))
    }

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

    /** [Statistics.hasSettled] by id; a card with no schedule is not settled. */
    fun hasSettled(state: BoxState, cardId: String): Boolean =
        state.scheduling[cardId]?.let { Statistics.hasSettled(it) } ?: false

    /**
     * Has this card cleared the growing bar? See [Statistics.hasArrived] — gate (a): phrase
     * unlock, the drill pools, and the presentation support a word gets while it is still
     * on its way in. Unknown ids read as false: a card with no schedule has cleared nothing.
     */
    fun hasArrived(state: BoxState, cardId: String): Boolean =
        state.scheduling[cardId]?.let { Statistics.hasArrived(it) } ?: false

    /**
     * Every arrived card id, in seed order — the words the box may hand to a
     * drill that practices only material the learner already holds (letter-drill
     * dictation is the first caller).
     *
     * Which words those are is an ENGINE rule, not a caller's filter: this reads
     * through [Inventory.active] like every other inventory query, so a suspended,
     * non-joining, or never-scheduled card is never offered, and a lapse drops a
     * card out on its own wherever it costs the card the growing bar ([Statistics.hasArrived]).
     * Restating that predicate app-side would let two platforms drift on what "known" means.
     *
     * Seed order, not the due shuffle: a drill samples with its own `Random`, so it
     * wants a list that is stable under it rather than a second ordering rule.
     * The query is read-only — drills stay stateless and never book a review.
     */
    fun arrivedCardIds(state: BoxState): List<String> =
        Inventory.active(state)
            .filter { Statistics.hasArrived(it) }
            .map { state.cards.getValue(it.cardId) }
            .sortedWith(Inventory.seedOrder)
            .map { it.id }
}
