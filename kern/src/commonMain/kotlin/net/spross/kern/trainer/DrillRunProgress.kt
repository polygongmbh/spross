package net.spross.kern.trainer

import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Reading
import net.spross.kern.session.Saying
import net.spross.kern.session.TurnFeedback

/**
 * The read surface every drill run shares, whatever it happens to ask: [NumbersRunState],
 * [LetterDrillRunState], [WordScrambleRunState], [SentenceScrambleRunState],
 * [OppositesRunState], [CountryDrillRunState] and [DateDrillRunState] each embed a
 * [DrillRunCore] and answer with the same [TurnFeedback] vocabulary, so what a question is
 * worth and whether the run offers a way out — under the button, or as a pause
 * ([DrillPacing]) — is one formula, not seven copies of it.
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

    val answerStreak: Int get() = core.answerStreak

    val bestAnswerStreak: Int get() = core.bestAnswerStreak

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

    val pacing: DrillPacing get() = core.pacing

    /**
     * Why the run waits on the learner ([DrillPacing]): the pause stands in place of the
     * question until the run is closed or goes on. Null while it runs on.
     */
    val pause: DrillPauseReason? get() = core.pacing.pause

    /**
     * What the question says as it goes up: its prompt, where that is a form in the language
     * being learned and hearing it gives nothing away. No default — every drill rules on it.
     */
    val promptSaying: Saying?

    /**
     * What a verdict says, right or wrong: the answer in the language being learned.
     * No default either; null where the drill's answer is in the learner's own language
     * or its question already was the sound.
     */
    val answerSaying: Saying?

    /**
     * The question's [Reading], as a review card has one: the prompt held back while a pause
     * stands in for the question, the answer once a verdict is out, a hold saying its correction.
     */
    val reading: Reading
        get() = Reading(
            key = index.toString(),
            prompt = promptSaying.takeIf { pause == null },
            answer = answerSaying.takeUnless { owesAnswer }?.let { said ->
                (feedback as? TurnFeedback.Almost)?.let { said.copy(form = it.correctForm) } ?: said
            },
        )
}
