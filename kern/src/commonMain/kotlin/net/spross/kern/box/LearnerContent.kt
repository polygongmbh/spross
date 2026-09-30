package net.spross.kern.box

import net.spross.kern.model.Card

/**
 * The edits that reach what the learner WROTE rather than what the box KNOWS —
 * their own words and the reports they filed — behind [BoxEngine]'s verbs of the same names.
 */
internal object LearnerContent {

    /**
     * Take in a word the learner wrote and queue it. Queuing is not a separate step:
     * they named this word themselves, so waiting for growth to walk to it would be
     * absurd. A word already known by id leaves the state untouched.
     *
     * One written in only ONE of the profile's languages is still taken in — it is a
     * SUGGESTION ([OwnWord]) — but joins no card and so is never queued or scheduled.
     */
    fun addWord(state: BoxState, word: OwnWord, nowEpochMillis: Long): BoxState {
        require(OwnWords.owns(word.id)) { "own word id must start with \"${OwnWords.ID_PREFIX}\"" }
        if (state.ownWords.any { it.id == word.id }) return state
        // why: the engine stamps it, not the caller — an app that had to remember
        // would be the one place a suggestion's age could go wrong, and it is the
        // only date a suggestion ever gets (it earns no schedule to carry one).
        val words = state.ownWords + word.copy(
            addedAt = stampOf(nowEpochMillis),
        )
        val next = state.copy(ownWords = words, cards = rebuilt(state, words))
        return if (next.cards[word.id] == null) next else BoxEngine.queue(next, listOf(word.id))
    }

    /**
     * Rewrite a word the learner wrote — its texts, its picture, its kind — KEEPING its
     * id, and with the id its schedule, its queue slot and anything filed against it.
     * That is the whole difference from [removeWord] + [addWord]: a typo fixed
     * should not cost the learner the progress they made on the word.
     *
     * [OwnWord.addedAt] is the stored one, never the incoming: it records when the word
     * was WRITTEN, and editing it is not writing it again. An edit that fills in the
     * missing half turns a suggestion into a card and queues it, exactly as [addWord]
     * would have; one that empties a half turns the card back into a suggestion, and its
     * schedule stays behind untouched, inert, the same way a source switch leaves one.
     * An id the learner never wrote leaves the state alone.
     */
    fun updateWord(state: BoxState, word: OwnWord): BoxState {
        val stored = state.ownWords.firstOrNull { it.id == word.id } ?: return state
        val words = state.ownWords.map {
            if (it.id == word.id) word.copy(addedAt = stored.addedAt) else it
        }
        val next = state.copy(ownWords = words, cards = rebuilt(state, words))
        val joinsNow = next.cards[word.id] != null
        return if (joinsNow && next.scheduling[word.id] == null) {
            BoxEngine.queue(next, listOf(word.id))
        } else {
            next
        }
    }

    /**
     * Take a word the learner wrote back out, with its schedule and its place in the
     * queue. Catalog words are never removable this way — a word the box did not get
     * from the learner is not theirs to delete, only to suspend ([BoxEngine.setSuspended]).
     */
    fun removeWord(state: BoxState, wordId: String): BoxState {
        if (state.ownWords.none { it.id == wordId }) return state
        val words = state.ownWords.filterNot { it.id == wordId }
        return state.copy(
            ownWords = words,
            cards = rebuilt(state, words),
            scheduling = state.scheduling - wordId,
            queued = state.queued.filterNot { it == wordId },
            reportedIssues = state.reportedIssues - wordId,
        )
    }

    /**
     * Move a word the learner wrote onto the catalog word that has caught up with it
     * ([CatalogMatches] finds the pair), keeping the progress made on either.
     *
     * **The longer history wins, whole.** The two logs are never folded together: they are
     * two records of learning ONE word, and a schedule replayed from both would read as far
     * more exposure than the word has had and overshoot its stability by a wide margin. The
     * longer one is the one that describes the word best, and a tie goes to the catalog card,
     * since that is the id that survives. Suspended if either was — one tap undoes a
     * suspension where burying one takes the word out of the rotation unasked.
     *
     * A suggestion carries no schedule at all, so merging one is the catalog answering what
     * the learner wrote it for, and the catalog word is queued in its place — by [BoxEngine.queue],
     * which is what already declines to queue a card the box has answered.
     *
     * The own word goes with its card. Nothing merges a catalog word into another
     * ([OwnWords.owns] guards the source), and nothing merges into a card this profile does
     * not hold.
     */
    fun mergeWord(state: BoxState, wordId: String, cardId: String): BoxState {
        if (!OwnWords.owns(wordId) || state.ownWords.none { it.id == wordId }) return state
        if (OwnWords.owns(cardId) || state.cards[cardId] == null) return state
        val own = state.scheduling[wordId]
        val held = state.scheduling[cardId]
        val winner = when {
            own == null -> held
            held == null -> own
            own.log.size > held.log.size -> own
            else -> held
        }?.copy(cardId = cardId, suspended = own?.suspended == true || held?.suspended == true)
        val words = state.ownWords.filterNot { it.id == wordId }
        val merged = state.copy(
            ownWords = words,
            cards = rebuilt(state, words),
            scheduling = state.scheduling - wordId + listOfNotNull(winner?.let { cardId to it }),
            // why: the learner asked for this word once and the catalog now has it, so the
            // ask moves across rather than being spent on a word that is gone.
            queued = state.queued.map { if (it == wordId) cardId else it }.distinct(),
            reportedIssues = state.reportedIssues.let { filed ->
                val moved = filed[wordId] ?: return@let filed
                if (cardId in filed) filed - wordId
                else filed - wordId + (cardId to moved.copy(cardId = cardId))
            },
        )
        return BoxEngine.queue(merged, listOf(cardId))
    }

    /**
     * Empty what waits to be sent on: every suggestion, every note, and every report filed
     * ([Feedback.clearableCount] is what that comes to). The learner has handed the lot
     * to whoever maintains the catalog, and no such entry has anything left to do here.
     *
     * A word written in two languages or more is untouched — it is study material with
     * progress on it, not a note to the maintainer, and a profile that cannot pair it does
     * not make it one ([OwnWord.isPair]). That is the whole line this verb
     * draws, and the reason it is not [BoxEngine.reset]: reset clears what the box KNOWS and keeps
     * what the learner WROTE, while this one keeps the studiable words and clears the
     * notes. Neither reaches a catalog word.
     *
     * [BoxState.lastExportAt] stays: it records that a copy was taken, which emptying
     * the outbox does not undo.
     */
    fun clearOutbox(state: BoxState): BoxState {
        val kept = state.ownWords.filter { it.isPair }
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
     * Deliberately independent of [BoxEngine.setSuspended]: neither verb implies the other, and
     * reporting never changes what the box schedules. Filing again replaces the earlier
     * report; a card the current profile does not join is refused, since a report
     * nobody can resolve to a word is unreadable to whoever would fix it, and so is a
     * word the learner wrote themselves ([Feedback.isReportable]).
     */
    fun report(
        state: BoxState,
        cardId: String,
        comment: String?,
        learnerInput: String?,
        nowEpochMillis: Long,
    ): BoxState {
        if (state.cards[cardId] == null || !Feedback.isReportable(cardId)) return state
        val issue = ReportedIssue(
            cardId = cardId,
            comment = comment?.takeIf { it.isNotBlank() },
            learnerInput = learnerInput?.takeIf { it.isNotBlank() },
            reportedAt = stampOf(nowEpochMillis),
        )
        return state.copy(reportedIssues = state.reportedIssues + (cardId to issue))
    }

    /** Withdraw a report; no-op when the card carries none. */
    fun dismissReport(state: BoxState, cardId: String): BoxState {
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
        else state.copy(lastExportAt = stampOf(nowEpochMillis))

    /** The card map with every own-word card re-derived; the catalog half is untouched. */
    private fun rebuilt(state: BoxState, words: List<OwnWord>): Map<String, Card> =
        state.cards.filterKeys { !OwnWords.owns(it) } +
            OwnWords.cards(words, state.joinStamp.source, state.joinStamp.target)
                .associateBy { it.id }
}
