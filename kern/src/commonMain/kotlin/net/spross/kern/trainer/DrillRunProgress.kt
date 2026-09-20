package net.spross.kern.trainer

import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.TurnFeedback

/**
 * The read surface every drill run shares, whatever it happens to ask: [NumbersRunState],
 * [LetterDrillRunState], [WordScrambleRunState] and [SentenceScrambleRunState] each embed a
 * [DrillRunCore] and answer with the same [TurnFeedback] vocabulary, so what a question is
 * worth and whether the run offers a way out is one formula, not four copies of it.
 *
 * A run's own business — the draw, the ladder, what an intent does to either — stays on the
 * concrete type; this is only the handful of figures the run shell on each phone reads to
 * render the score line and drive the close.
 */
interface DrillRunProgress {
    /** Bumped per question — what an autoplay effect keys on, since two draws can be equal values. */
    val index: Int

    /** Nothing left to ask. */
    val finished: Boolean

    /** The counters every drill run keeps, booked as one ([DrillRunCore.book]). */
    val core: DrillRunCore

    /** Where kern says the answer stands. */
    val feedback: TurnFeedback

    val done: Int get() = core.done

    val streak: Int get() = core.streak

    val bestStreak: Int get() = core.bestStreak

    val missRun: Int get() = core.missRun

    val outcomes: List<AnswerOutcome> get() = core.outcomes

    val solved: Set<String> get() = core.solved

    /** The answer is still owed. */
    val owesAnswer: Boolean get() = feedback == TurnFeedback.Neutral

    /** Correct or almost: something is pending that closing must book rather than lose. */
    val answerAccepted: Boolean
        get() = feedback == TurnFeedback.Correct || feedback is TurnFeedback.Almost

    /** The way out, under the button that goes on, on the second miss in a row. */
    val offersFinish: Boolean get() = missRun >= 1 && feedback == TurnFeedback.Revealed

    val tally: DrillTally get() = DrillTally.of(outcomes)
}
