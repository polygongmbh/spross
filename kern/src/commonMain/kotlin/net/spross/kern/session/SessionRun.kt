package net.spross.kern.session

import kotlin.math.max
import kotlin.math.min
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.TallyPartKind
import net.spross.kern.box.tallyKind
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Rating
import net.spross.kern.model.SessionPlan
import net.spross.kern.store.SaveScope

/** Where the run stands: a card to answer, or the summary. */
sealed class SessionStep {
    data class Card(val cardId: String) : SessionStep()
    data object Completed : SessionStep()
}

/** What one answer turned out to be: [Right] clean and correct, [Almost] accepted with a caveat, [Wrong] a miss. */
enum class AnswerOutcome { Right, Almost, Wrong }

/** What the learner (or the app's lifecycle) does to a run. */
sealed class SessionIntent {
    /** Today's round, composed against the live box. */
    data object Start : SessionIntent()

    /** The on-demand extra round; a no-op when it would come back empty. */
    data object StartExtra : SessionIntent()

    /** Today's round taken short — due work alone; a no-op when nothing is due. */
    data object StartShort : SessionIntent()
    data class Answer(val rating: Rating) : SessionIntent()

    /**
     * The learner wants no more of the card in front of them: suspend it and move on
     * WITHOUT an answer. Never a rating — they are not saying they failed it, they are
     * saying it should not be asked, and a review the box invented would move a schedule
     * the learner never touched.
     */
    data object SuspendCurrent : SessionIntent()

    /** "Keep practicing": switch a finished run into endless and pull one refill. */
    data object ContinueEndless : SessionIntent()

    /** The join moved under a running session — recompose against the live one. */
    data object RecomposeIfStale : SessionIntent()

    data object Finish : SessionIntent()
    data object Close : SessionIntent()
}

/** What the reduction asks the platform to do about the world outside the box. */
sealed class SessionEffect {
    /** Write the box out, and what rides along with it ([SaveScope]). */
    data class Save(val scope: SaveScope) : SessionEffect()

    /** The run closed on the day — statistics and every box-derived surface read stale. */
    data object DayBooked : SessionEffect()
}

/** Which round opened a run: the day's own, the on-demand extra, or the day's taken short. */
enum class SessionOpening { Day, Extra, Short }

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
    /**
     * The promise on screen; it moves only on an endless refill or a dropped answer,
     * and never below [answered] — [segments] holds one part per rating, so a total
     * under it would be a run drawing more parts than it claims to have.
     */
    val total: Int,
    /** Every answer this run recorded, in answer order. */
    val tally: RoundTally,
    val endless: Boolean,
    val finished: Boolean,
    /** A run exists from [SessionIntent.Start] until [SessionIntent.Close]; a summary still counts. */
    val active: Boolean,
    /** The join this run was composed against; a mismatch forces a recompose. */
    val joinStamp: JoinStamp?,
    /** Which round opened this run — a stale run recomposes as the same one. */
    val opening: SessionOpening = SessionOpening.Day,
    /** The box as this run opened on it — the before of what the round did ([RoundSummary]). */
    val startBox: BoxState? = null,
) {
    val currentCardId: String? get() = (step as? SessionStep.Card)?.cardId

    val answered: Int get() = tally.answered

    /** 1-based position in the composed plan. */
    val position: Int get() = min(answered + 1, max(total, 1))

    val remaining: Int get() = queue.size

    val segments: List<AnswerOutcome> get() = tally.segments
}

/**
 * The session run as pure state plus one reducer, the one machine both apps drive.
 *
 * Time discipline as everywhere in kern: `nowEpochMillis`/`tzId` come from the caller.
 * No default arguments: they do not cross the ObjC boundary, so every entry point is explicit.
 */
object SessionRun {

    /** No run yet: a closed, finished shell around the box. */
    fun idle(box: BoxState): SessionRunState = SessionRunState(
        box = box, step = SessionStep.Completed, queue = emptyList(), total = 0,
        tally = RoundTally(),
        endless = false, finished = true, active = false, joinStamp = null,
    )

    /** The box changed outside the run (a word queued, settings edited) — carry it in. */
    fun withBox(state: SessionRunState, box: BoxState): SessionRunState = state.copy(box = box)

    fun reduce(
        state: SessionRunState,
        intent: SessionIntent,
        nowEpochMillis: Long,
        tzId: String,
    ): SessionReduction = when (intent) {
        SessionIntent.Start -> begin(
            state, SessionComposer.composeSession(state.box, nowEpochMillis, tzId),
            SessionOpening.Day, nowEpochMillis, tzId,
        )
        SessionIntent.StartExtra -> startExtra(state, nowEpochMillis, tzId)
        SessionIntent.StartShort -> startShort(state, nowEpochMillis, tzId)
        is SessionIntent.Answer -> answer(state, intent.rating, nowEpochMillis, tzId)
        SessionIntent.SuspendCurrent -> suspendCurrent(state, nowEpochMillis, tzId)
        SessionIntent.ContinueEndless -> continueEndless(state, nowEpochMillis, tzId)
        SessionIntent.RecomposeIfStale -> recompose(state, nowEpochMillis, tzId)
        SessionIntent.Finish -> finish(state)
        SessionIntent.Close -> close(state, nowEpochMillis, tzId)
    }

    /**
     * The extra round is [SessionComposer.composeRound] itself — the day-done question is
     * [SessionComposer.composeSession]'s alone, and a round the learner opens is an ordinary one.
     *
     * why: one composer for every round,
     * so an extra round never arrives as a wall of first sights or a wall of cards dragged forward from days out.
     */
    private fun startExtra(state: SessionRunState, nowEpochMillis: Long, tzId: String): SessionReduction {
        val plan = SessionComposer.composeRound(state.box, nowEpochMillis, tzId)
        return if (plan.isEmpty) unchanged(state) else begin(state, plan, SessionOpening.Extra, nowEpochMillis, tzId)
    }

    /**
     * The short round is [SessionComposer.composeShortRound] — the day's own round taken
     * short, so it opens on exactly the days the full one does and closes the same way.
     */
    private fun startShort(state: SessionRunState, nowEpochMillis: Long, tzId: String): SessionReduction {
        val plan = SessionComposer.composeShortRound(state.box, nowEpochMillis, tzId)
        return if (plan.isEmpty) unchanged(state) else begin(state, plan, SessionOpening.Short, nowEpochMillis, tzId)
    }

    private fun begin(
        state: SessionRunState,
        plan: SessionPlan,
        opening: SessionOpening,
        nowEpochMillis: Long,
        tzId: String,
    ): SessionReduction = advance(
        state.copy(
            queue = plan.queue, total = plan.queue.size,
            tally = RoundTally(),
            endless = false, finished = false, active = true, joinStamp = plan.joinStamp,
            opening = opening, startBox = state.box,
        ),
        emptyList(),
        nowEpochMillis,
        tzId,
    )

    /** Apply one answer — every answer event is an FSRS review — then advance. */
    private fun answer(state: SessionRunState, rating: Rating, nowEpochMillis: Long, tzId: String): SessionReduction {
        val cardId = state.currentCardId ?: return unchanged(state)
        val before = state.box.scheduling[cardId] ?: CardScheduling(cardId = cardId)
        val box = BoxEngine.answer(state.box, cardId, rating, nowEpochMillis)
        val kind = box.scheduling[cardId]?.let { tallyKind(before, it) } ?: TallyPartKind.Reviewed
        val next = state.copy(
            box = box,
            tally = state.tally + RoundAnswer(cardId, rating, kind),
            queue = state.queue.drop(1),
        )
        return advance(next, listOf(SessionEffect.Save(SaveScope.BOX)), nowEpochMillis, tzId)
    }

    /**
     * Suspend the card on screen and step past it.
     * It counts as nothing — no entry in [SessionRunState.tally] —
     * so the round it leaves is the round the learner actually did.
     * [SessionRunState.total] shrinks with it: the count on screen is a promise,
     * and a word taken out of the round was never owed.
     */
    private fun suspendCurrent(
        state: SessionRunState,
        nowEpochMillis: Long,
        tzId: String,
    ): SessionReduction {
        val cardId = state.currentCardId ?: return unchanged(state)
        val box = BoxEngine.setSuspended(state.box, cardId, true, nowEpochMillis)
        val next = state.copy(
            box = box,
            queue = state.queue.drop(1),
            // why: never below what has been answered — `segments` draws one part per
            // rating, and a total under that would claim fewer parts than it holds.
            total = maxOf(state.total - 1, state.answered),
        )
        return advance(next, listOf(SessionEffect.Save(SaveScope.BOX)), nowEpochMillis, tzId)
    }

    /**
     * Next step: composed queue → endless refill (only once asked for) → done.
     *
     * why: no mid-run drain.
     * Cards coming due while the learner sits there stay out of the queue,
     * so "12/30" never quietly becomes "12/37" and the finish line holds still for someone counting down to it.
     * They are still due — the summary offers them as extra practice.
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
        val done = finish(state)
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
     * → recompose against the live join, as the round that opened the run.
     */
    private fun recompose(state: SessionRunState, nowEpochMillis: Long, tzId: String): SessionReduction {
        val stamp = state.joinStamp
        if (!state.active || state.finished || stamp == null || stamp == state.box.joinStamp) {
            return unchanged(state)
        }
        val plan = when (state.opening) {
            SessionOpening.Day -> SessionComposer.composeSession(state.box, nowEpochMillis, tzId)
            SessionOpening.Extra -> SessionComposer.composeRound(state.box, nowEpochMillis, tzId)
            SessionOpening.Short -> SessionComposer.composeShortRound(state.box, nowEpochMillis, tzId)
        }
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
     * Close the run out. Every answer is already in the box and the day is counted off the
     * logs, so nothing is booked here — only the surfaces drawn from the box are behind,
     * which is why this save carries the snapshots.
     */
    private fun finish(state: SessionRunState): SessionReduction {
        if (state.finished) return unchanged(state)
        return SessionReduction(
            state.copy(finished = true),
            listOf(SessionEffect.Save(SaveScope.BOX_AND_SNAPSHOTS), SessionEffect.DayBooked),
        )
    }

    /**
     * Close the run. The step and queue stay as they were — the platform may still be animating
     * the summary away and needs its content; a start resets everything anyway.
     */
    private fun close(state: SessionRunState, nowEpochMillis: Long, tzId: String): SessionReduction {
        val ended = if (!state.finished && state.answered > 0) {
            finish(state)
        } else {
            unchanged(state)
        }
        return SessionReduction(
            ended.state.copy(finished = true, endless = false, active = false),
            ended.effects + SessionEffect.DayBooked,
        )
    }

    private fun unchanged(state: SessionRunState) = SessionReduction(state, emptyList())
}
