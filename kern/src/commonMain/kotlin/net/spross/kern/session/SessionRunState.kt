package net.spross.kern.session

import kotlin.math.max
import kotlin.math.min
import net.spross.kern.box.BoxState
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Rating
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
