package net.spross.kern.trainer

import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback

/** What the learner does to a slot run. */
sealed class NumbersIntent {
    /** A live keystroke: finishing the word IS the answer, within the growing-answer guard. */
    data class InputChanged(val text: String) : NumbersIntent()

    /** Check/Enter with text standing. */
    data class Submit(val text: String) : NumbersIntent()

    /** "Aufdecken" on an empty field — the answer stands and the task books a miss. */
    data object Reveal : NumbersIntent()

    /** The reference table raised mid-run; while the answer is still owed it costs the Sprosse. */
    data object LookUp : NumbersIntent()

    /** The explicit tap that books whatever the feedback already said. */
    data object ConfirmPending : NumbersIntent()

    /** The platform's armed beat elapsed. */
    data object AdvanceElapsed : NumbersIntent()

    /**
     * A timed run's clock ran out ([TimedRun.SECONDS]): the run is over, and its close books
     * a pending answer exactly as the ✕ would. Ignored by a run that is not timed.
     */
    data object TimeUp : NumbersIntent()
}

/** The closed result of one intent: the next state plus what it asks for. */
data class NumbersReduction(val state: NumbersRunState, val effects: List<DrillEffect>)

/**
 * What a closed run leaves behind: the figures for the page that started it, and the two store
 * writes the platform owes.
 */
data class NumbersClose(
    val state: NumbersRunState,
    /** null ⇒ the run was never answered: dismiss, store nothing. */
    val summary: DrillRunSummary?,
    /** Where the streak record is filed ([NumbersMode.RECORD_PREFIX] + this). */
    val recordKey: String,
    /**
     * Progress key ([NumbersMode.PROGRESS_PREFIX] + it) → the Sprosse to store, already filtered to
     * the ones that strictly beat what was standing. Every exercise the run ASKED, not only the
     * one it ended on; an exercise it never drew is absent, because an unasked Sprosse was never
     * stood on.
     */
    val progressBookings: Map<String, Int>,
    val effects: List<DrillEffect>,
)

/**
 * One slot run, whole and immutable: what is on screen, what the answers have done to it, and
 * the per-exercise Sprossen it is standing on.
 *
 * The learner's TEXT is not in here — the platform owns the field, the keyboard and the focus,
 * and hands text in through [NumbersIntent]. What is in here is every rule that decides what
 * the text means.
 *
 * No FSRS, no box: right or wrong only moves the in-run streak, and the run ends when the
 * learner closes it.
 */
data class NumbersRunState(
    val mode: NumbersMode,
    val current: DrawnTask,
    /** Bumped per question — what an autoplay effect keys on, since two draws can be equal values. */
    override val index: Int,
    /**
     * The Sprosse each exercise stands on, all starting at 1 however far the learner has climbed
     * before: persisted progress buys ACCESS, never a head start, because the climb is the drill.
     */
    val levels: Map<NumbersExercise, Int>,
    val winsAtLevel: Map<NumbersExercise, Int>,
    /**
     * The highest Sprosse each exercise STOOD ON in this run — what the close books. Tracked apart
     * from [levels] because a Sprosse steps back down on a miss, and the ladder rewards reaching
     * one, not finishing on it.
     */
    val bestLevels: Map<NumbersExercise, Int>,
    /** The counters every drill run keeps, booked as one ([DrillRunCore.book]). */
    override val core: DrillRunCore,
    /** Digit counts already introduced with a place-value hint; each length is hinted once. */
    val seenDigitCounts: Set<Int>,
    /** The learner looked the numbers up while owing this answer: it books almost. */
    val hintUsed: Boolean,
    override val feedback: TurnFeedback,
    /** What a refused answer actually NAMED ("setenta" is 70) — only beside a Revealed miss. */
    val otherWord: Match.OtherWord? = null,
    override val finished: Boolean,
    /** Every clean answer's Sprosse, summed ([TimedRun.points]) — read only where [timed]. */
    val score: Int = 0,
    /** The script a challenge run asks from instead of the ramp's draw; null for every other run. */
    val challenge: NumbersChallenge? = null,
) : DrillRunProgress {
    /** The run ends on a clock and is scored ([TimedRun]). */
    val timed: Boolean get() = mode.isTimed

    /** A timed run ends on its clock, so it offers no way out of its own beyond the ✕. */
    override val offersFinish: Boolean get() = !timed && super.offersFinish

    val currentTask: NumbersTask get() = current.task

    /** Which of the run's exercises asked what is on screen — what a win and a miss apply to. */
    val currentExercise: NumbersExercise get() = current.exercise

    /** The reading is the prompt and the value is owed. The one thing it decides is the keyboard. */
    val currentReversed: Boolean get() = current.reversed

    val currentLevel: Int get() = levels[currentExercise] ?: 1

    val currentMaxLevel: Int get() = mode.maxLevel(currentExercise)

    /** An exercise with one Sprosse has no Sprosse to report. */
    val showsSprosse: Boolean get() = currentMaxLevel > 1

    /** A run that asks one thing has already said what it asks. */
    val severalExercises: Boolean get() = mode.exercises.size > 1

    /**
     * The card carries the answer. A typo leaves it closed — the correction box already spells
     * the word out, and the answer is never on screen twice.
     */
    val showsAnswer: Boolean get() = feedback == TurnFeedback.Revealed

    /**
     * The numbers page is one tap away from a numbers task, and from no other — and not
     * against a clock, where reading the answer up would be the fastest way to score.
     */
    val offersLookUp: Boolean get() = currentExercise == NumbersExercise.Counting && !timed

    /**
     * Digit count of the numeric prompt on screen, null outside a forward numbers task: a
     * reversed prompt IS the reading, which already names the place a hint would introduce.
     */
    val currentDigits: Int?
        get() = if (currentExercise == NumbersExercise.Counting && !currentReversed) {
            currentTask.prompt.length
        } else {
            null
        }

    /** The place word, the first time a length appears and never again. */
    val placeValueHint: String?
        get() {
            val digits = currentDigits ?: return null
            if (digits in seenDigitCounts) return null
            return Numbers.placeValueHint(digits, mode.language)
        }

    /** What a correct answer earns: almost where the reference was read while the answer was owed. */
    internal val cleanOutcome: AnswerOutcome
        get() = if (hintUsed) AnswerOutcome.Almost else AnswerOutcome.Right
}
