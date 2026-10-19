package net.spross.kern.box

import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling

/**
 * The learner's own hand on which cards the box takes in and keeps asking —
 * the queue and the per-card schedule edits — behind [BoxEngine]'s verbs of the same names.
 */
internal object Curation {

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
     * Unlike [BoxEngine.reset] this is about ONE card, and unlike [LearnerContent.removeWord]
     * it keeps the card — a catalog word is not theirs to delete, and their own word survives
     * its progress being cleared. Anything filed against it stays: a report is about the
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
    fun setSuspended(state: BoxState, cardId: String, suspended: Boolean): BoxState {
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
}
