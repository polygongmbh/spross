package net.spross.kern.session

import kotlin.math.max
import kotlin.math.min
import net.spross.kern.box.AnswerStatus
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.BoxStatistics
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Rating
import net.spross.kern.model.SessionPlan

/** Where the run stands: a card to answer, or the summary. */
sealed class SessionStep {
    data class Card(val cardId: String) : SessionStep()
    data object Completed : SessionStep()
}

/** How an answer reads back: the grouping is the rule, the colour each tone wears is the platform's. */
enum class AnswerTone { Right, Tough, Wrong }

/** What the learner (or the app's lifecycle) does to a run. */
sealed class SessionIntent {
    /** Today's round, composed against the live box. */
    data object Start : SessionIntent()

    /** The on-demand extra round; a no-op when it would come back empty. */
    data object StartExtra : SessionIntent()
    data class Answer(val rating: Rating) : SessionIntent()

    /** "Keep practising": switch a finished run into endless and pull one refill. */
    data object ContinueEndless : SessionIntent()

    /** The join moved under a running session — recompose against the live one. */
    data object RecomposeIfStale : SessionIntent()

    /** Backgrounding: book what has been answered so far. */
    data object FoldPartial : SessionIntent()
    data object Finish : SessionIntent()
    data object Close : SessionIntent()
}

/** What the reduction asks the platform to do about the world outside the box. */
sealed class SessionEffect {
    /** Write the box out; [immediate] skips the store's debounce. */
    data class Persist(val immediate: Boolean) : SessionEffect()

    /** The day's counters moved into `dailyStats` — statistics and every box-derived surface read stale. */
    data object DayBooked : SessionEffect()
}

/** The closed result of one intent: the next state plus what it asks for. */
data class SessionReduction(val state: SessionRunState, val effects: List<SessionEffect>)

/**
 * A session run, whole and immutable — the composed queue, its tallies, and the box it works on.
 *
 * The composed plan IS the run: the count on screen is a promise, so nothing joins a session
 * already under way. Only endless refills, and only once it has been asked for.
 */
data class SessionRunState(
    val box: BoxState,
    val step: SessionStep,
    /** Card ids still to answer, front first. */
    val queue: List<String>,
    /** The promise on screen; it moves only on an endless refill or a dropped answer. */
    val total: Int,
    val answered: Int,
    /** Answers already booked into `dailyStats`; later folds add the delta only. */
    val folded: Int,
    /** Ratings in answer order. */
    val ratings: List<Rating>,
    /** Summary tallies: first meetings, words that crossed into consolidated, review reps. */
    val newCards: Int,
    val graduated: Int,
    val reviews: Int,
    val endless: Boolean,
    val finished: Boolean,
    /** A run exists from [SessionIntent.Start] until [SessionIntent.Close]; a summary still counts. */
    val active: Boolean,
    /** The join this run was composed against; a mismatch forces a recompose. */
    val joinStamp: JoinStamp?,
) {
    val currentCardId: String? get() = (step as? SessionStep.Card)?.cardId

    /** 1-based position in the composed plan. */
    val position: Int get() = min(answered + 1, max(total, 1))

    val remaining: Int get() = queue.size

    val segments: List<AnswerTone> get() = ratings.map(::tone)
}

/**
 * The session run as pure state plus one reducer — the machine both apps used to re-derive.
 *
 * Time discipline as everywhere in kern: `nowEpochMillis`/`tzId` come from the caller.
 * No default arguments: they do not cross the ObjC boundary, so every entry point is explicit.
 */
object SessionRun {

    /** No run yet: a closed, finished shell around the box. */
    fun idle(box: BoxState): SessionRunState = SessionRunState(
        box = box, step = SessionStep.Completed, queue = emptyList(), total = 0,
        answered = 0, folded = 0, ratings = emptyList(),
        newCards = 0, graduated = 0, reviews = 0,
        endless = false, finished = true, active = false, joinStamp = null,
    )

    /** The box changed outside the run (a word packed, settings edited) — carry it in. */
    fun withBox(state: SessionRunState, box: BoxState): SessionRunState = state.copy(box = box)

    fun reduce(
        state: SessionRunState,
        intent: SessionIntent,
        nowEpochMillis: Long,
        tzId: String,
    ): SessionReduction = when (intent) {
        SessionIntent.Start ->
            begin(state, SessionComposer.composeSession(state.box, nowEpochMillis, tzId), nowEpochMillis, tzId)
        SessionIntent.StartExtra -> startExtra(state, nowEpochMillis, tzId)
        is SessionIntent.Answer -> answer(state, intent.rating, nowEpochMillis, tzId)
        SessionIntent.ContinueEndless -> continueEndless(state, nowEpochMillis, tzId)
        SessionIntent.RecomposeIfStale -> recompose(state, nowEpochMillis, tzId)
        SessionIntent.FoldPartial -> foldPartial(state, nowEpochMillis, tzId)
        SessionIntent.Finish -> finish(state, nowEpochMillis, tzId)
        SessionIntent.Close -> close(state, nowEpochMillis, tzId)
    }

    /**
     * The extra round is [SessionComposer.composeRound] itself — the day-done question is
     * [SessionComposer.composeSession]'s alone, and a round the learner opens is an ordinary one.
     *
     * why: it used to be composed by rules of its own, so it kept arriving as either a wall of
     * first sights or a wall of cards dragged forward from days out, depending on which of the
     * two bespoke composers happened to win.
     */
    private fun startExtra(state: SessionRunState, nowEpochMillis: Long, tzId: String): SessionReduction {
        val plan = SessionComposer.composeRound(state.box, nowEpochMillis, tzId)
        return if (plan.isEmpty) unchanged(state) else begin(state, plan, nowEpochMillis, tzId)
    }

    private fun begin(
        state: SessionRunState,
        plan: SessionPlan,
        nowEpochMillis: Long,
        tzId: String,
    ): SessionReduction = advance(
        state.copy(
            queue = plan.queue, total = plan.queue.size,
            answered = 0, folded = 0, ratings = emptyList(),
            newCards = 0, graduated = 0, reviews = 0,
            endless = false, finished = false, active = true, joinStamp = plan.joinStamp,
        ),
        emptyList(),
        nowEpochMillis,
        tzId,
    )

    /** Apply one answer — every answer event is an FSRS review — then advance. */
    private fun answer(state: SessionRunState, rating: Rating, nowEpochMillis: Long, tzId: String): SessionReduction {
        val cardId = state.currentCardId ?: return unchanged(state)
        val wasConsolidated = BoxEngine.isConsolidated(state.box, cardId)
        val outcome = BoxEngine.answer(state.box, cardId, rating, nowEpochMillis, tzId)
        val applied = outcome.status == AnswerStatus.Applied
        val next = if (applied) {
            tallied(
                state.copy(box = outcome.state, ratings = state.ratings + rating, answered = state.answered + 1),
                firstAnswer = outcome.state.scheduling[cardId]?.reviewCount == 1,
                wasConsolidated = wasConsolidated,
                isConsolidated = BoxEngine.isConsolidated(outcome.state, cardId),
            )
        } else {
            // Stale or dropped answers leave the run silently — shrink the total so the
            // progress counter stays honest.
            state.copy(box = outcome.state, total = max(1, state.total - 1))
        }
        return advance(next.copy(queue = next.queue.drop(1)), listOf(SessionEffect.Persist(false)), nowEpochMillis, tzId)
    }

    /**
     * Bucket each answer for the summary: first-ever answer = new, a word crossing into
     * consolidated = graduated, else a review rep.
     *
     * why: the crossing, not a phase transition — with one learning step a word reaches Review
     * on its first pass while its stability is still tiny, so the phase edge would have called
     * that consolidated and the summary would have claimed a word had landed that had barely arrived.
     */
    private fun tallied(
        state: SessionRunState,
        firstAnswer: Boolean,
        wasConsolidated: Boolean,
        isConsolidated: Boolean,
    ): SessionRunState = when {
        firstAnswer -> state.copy(newCards = state.newCards + 1)
        !wasConsolidated && isConsolidated -> state.copy(graduated = state.graduated + 1)
        else -> state.copy(reviews = state.reviews + 1)
    }

    /**
     * Next step: composed queue → endless refill (only once asked for) → done.
     *
     * why: no mid-run drain. Cards coming due while the learner sits there used to be pulled
     * straight in, so "12/30" quietly became "12/37" and the finish line moved away from someone
     * already counting down to it. They are still due — the summary offers them as extra practice.
     */
    private fun advance(
        state: SessionRunState,
        effects: List<SessionEffect>,
        nowEpochMillis: Long,
        tzId: String,
    ): SessionReduction {
        val next = state.queue.firstOrNull()
        if (next != null) return SessionReduction(state.copy(step = SessionStep.Card(next)), effects)
        if (state.endless) {
            refilled(state, nowEpochMillis, tzId)?.let { return SessionReduction(it, effects) }
        }
        val done = finish(state, nowEpochMillis, tzId)
        return SessionReduction(done.state.copy(step = SessionStep.Completed), effects + done.effects)
    }

    /**
     * Pull the next endless batch onto the queue; null when dry.
     *
     * A refill is a round like any other, pull-aheads included: spacing spent on the
     * soonest-due cards costs nearly nothing (`docs/growth-evidence.md`), and withholding them
     * only made every refill an all-new one. So the run ends when the learner closes it rather
     * than when the catalog does — which is what "endless" is asked for.
     */
    private fun refilled(state: SessionRunState, nowEpochMillis: Long, tzId: String): SessionRunState? {
        val more = SessionComposer.composeRound(state.box, nowEpochMillis, tzId).queue
        if (more.isEmpty()) return null
        return state.copy(queue = more, total = state.total + more.size, step = SessionStep.Card(more.first()))
    }

    /** Endless stays asked-for even when the refill comes back dry — the run just stays on its summary. */
    private fun continueEndless(state: SessionRunState, nowEpochMillis: Long, tzId: String): SessionReduction {
        val asked = state.copy(endless = true)
        val refilled = refilled(asked, nowEpochMillis, tzId) ?: return unchanged(asked)
        // Re-open so the next finish books the new delta.
        return unchanged(refilled.copy(finished = false))
    }

    /**
     * The box's join moved under a running session (source switch, catalog update)
     * → recompose against the live join; stale ids would no-op.
     */
    private fun recompose(state: SessionRunState, nowEpochMillis: Long, tzId: String): SessionReduction {
        val stamp = state.joinStamp
        if (!state.active || state.finished || stamp == null || stamp == state.box.joinStamp) {
            return unchanged(state)
        }
        val plan = SessionComposer.composeSession(state.box, nowEpochMillis, tzId)
        return advance(
            state.copy(
                queue = plan.queue,
                total = state.answered + plan.queue.size,
                joinStamp = plan.joinStamp,
            ),
            emptyList(), nowEpochMillis, tzId,
        )
    }

    /**
     * Backgrounding mid-run: book answered-so-far into `dailyStats` so an evicted app never loses
     * demonstrated reviews (the streak stays honest). A fold that never reaches disk is no fold,
     * so it persists immediately.
     */
    private fun foldPartial(state: SessionRunState, nowEpochMillis: Long, tzId: String): SessionReduction {
        if (state.finished || state.answered <= state.folded) return unchanged(state)
        return SessionReduction(
            booked(state, nowEpochMillis, tzId),
            listOf(SessionEffect.Persist(true), SessionEffect.DayBooked),
        )
    }

    /** Fold the run into `dailyStats` exactly once; only the not-yet-folded delta is booked. */
    private fun finish(state: SessionRunState, nowEpochMillis: Long, tzId: String): SessionReduction {
        if (state.finished) return unchanged(state)
        return SessionReduction(
            booked(state, nowEpochMillis, tzId).copy(finished = true),
            listOf(SessionEffect.Persist(true), SessionEffect.DayBooked),
        )
    }

    private fun booked(state: SessionRunState, nowEpochMillis: Long, tzId: String): SessionRunState = state.copy(
        box = BoxEngine.endSession(state.box, state.answered - state.folded, nowEpochMillis, tzId),
        folded = state.answered,
    )

    /**
     * Close the run. The step and queue stay as they were — the platform may still be animating
     * the summary away and needs its content; a start resets everything anyway.
     */
    private fun close(state: SessionRunState, nowEpochMillis: Long, tzId: String): SessionReduction {
        val ended = if (!state.finished && state.answered > 0) {
            finish(state, nowEpochMillis, tzId)
        } else {
            unchanged(state)
        }
        return SessionReduction(
            ended.state.copy(finished = true, endless = false, active = false),
            ended.effects + SessionEffect.DayBooked,
        )
    }

    private fun unchanged(state: SessionRunState) = SessionReduction(state, emptyList())

    /**
     * The day streak standing at its all-time best, which the finish screen names.
     * A first day is not a record worth announcing — every box has one, and nothing has been
     * held on to yet.
     */
    fun streakIsRecord(stats: BoxStatistics): Boolean =
        stats.streak >= 2 && stats.streak == stats.longestStreak
}

private fun tone(rating: Rating): AnswerTone = when (rating) {
    Rating.Again -> AnswerTone.Wrong
    Rating.Hard -> AnswerTone.Tough
    Rating.Good, Rating.Easy -> AnswerTone.Right
}
