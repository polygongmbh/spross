package net.spross.kern.trainer

import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback

/** What the learner does to a slot run. */
sealed class TrainerIntent {
    /** A live keystroke: finishing the word IS the answer, within the growing-answer guard. */
    data class InputChanged(val text: String) : TrainerIntent()

    /** Check/Enter with text standing. */
    data class Submit(val text: String) : TrainerIntent()

    /** "Aufdecken" on an empty field — the answer stands and the task books a miss. */
    data object Reveal : TrainerIntent()

    /** The reference table raised mid-run; while the answer is still owed it costs the rung. */
    data object LookUp : TrainerIntent()

    /** The explicit tap that books whatever the feedback already said. */
    data object ConfirmPending : TrainerIntent()

    /** The platform's armed beat elapsed. */
    data object AdvanceElapsed : TrainerIntent()
}

/** The closed result of one intent: the next state plus what it asks for. */
data class TrainerReduction(val state: TrainerRunState, val effects: List<DrillEffect>)

/**
 * What a closed run leaves behind: the figures for the page that started it, and the two store
 * writes the platform owes.
 */
data class TrainerClose(
    val state: TrainerRunState,
    /** null ⇒ the run was never answered: dismiss, store nothing. */
    val summary: DrillRunSummary?,
    /** Where the streak record is filed ([TrainerMode.RECORD_PREFIX] + this). */
    val recordKey: String,
    /**
     * Progress key ([TrainerMode.PROGRESS_PREFIX] + it) → the rung to store, already filtered to
     * the ones that strictly beat what was standing. Every variant the run ASKED, not only the
     * one it ended on; a variant it never drew is absent, because an unasked rung was never
     * stood on.
     */
    val progressBookings: Map<String, Int>,
    val effects: List<DrillEffect>,
)

/**
 * One slot run, whole and immutable: what is on screen, what the answers have done to it, and
 * the per-variant rungs it is standing on.
 *
 * The learner's TEXT is not in here — the platform owns the field, the keyboard and the focus,
 * and hands text in through [TrainerIntent]. What is in here is every rule that decides what
 * the text means.
 *
 * No FSRS, no box: right or wrong only moves the in-run streak, and the run ends when the
 * learner closes it.
 */
data class TrainerRunState(
    val mode: TrainerMode,
    val current: DrawnTask,
    /** Bumped per question — what an autoplay effect keys on, since two draws can be equal values. */
    val index: Int,
    /**
     * The rung each variant stands on, all starting at 1 however far the learner has climbed
     * before: persisted progress buys ACCESS, never a head start, because the climb is the drill.
     */
    val levels: Map<DrillVariant, Int>,
    val winsAtLevel: Map<DrillVariant, Int>,
    /**
     * The highest rung each variant STOOD ON in this run — what the close books. Tracked apart
     * from [levels] because a rung steps back down on a miss, and the ladder rewards reaching
     * one, not finishing on it.
     */
    val bestLevels: Map<DrillVariant, Int>,
    val done: Int,
    val streak: Int,
    val bestStreak: Int,
    /**
     * Misses in a row already BOOKED — the one on screen is not among them, so 1 while a miss
     * shows means this is the second in a row.
     */
    val missRun: Int,
    val outcomes: List<AnswerOutcome>,
    /**
     * The prompts this run has already answered RIGHT ([DrillSolved]): never drawn again, and
     * a rung with nothing left outside them is climbed past rather than repeated.
     */
    val solved: Set<String>,
    /** Digit counts already introduced with a place-value hint; each length is hinted once. */
    val seenDigitCounts: Set<Int>,
    /** The learner looked the numbers up while owing this answer: it books almost. */
    val hintUsed: Boolean,
    val feedback: TurnFeedback,
    /** What a refused answer actually NAMED ("setenta" is 70) — only beside a Revealed miss. */
    val otherWord: Match.OtherWord? = null,
    val finished: Boolean,
) {
    val currentTask: TrainerTask get() = current.task

    /** Which of the run's variants asked what is on screen — what a win and a miss apply to. */
    val currentVariant: DrillVariant get() = current.variant

    /** The reading is the prompt and the value is owed. The one thing it decides is the keyboard. */
    val currentReversed: Boolean get() = current.reversed

    val currentLevel: Int get() = levels[currentVariant] ?: 1

    val currentMaxLevel: Int get() = mode.maxLevel(currentVariant)

    /** A variant with one rung has no rung to report. */
    val showsRung: Boolean get() = currentMaxLevel > 1

    /** A run that asks one thing has already said what it asks. */
    val severalVariants: Boolean get() = mode.variants.size > 1

    /** The answer is still owed — what makes a look-up cost the rung. */
    val owesAnswer: Boolean get() = feedback == TurnFeedback.Neutral

    /** Correct or almost: something is pending that closing must book rather than lose. */
    val answerAccepted: Boolean
        get() = feedback == TurnFeedback.Correct || feedback is TurnFeedback.Almost

    /**
     * The card carries the answer. A typo leaves it closed — the correction box already spells
     * the word out, and the answer is never on screen twice.
     */
    val showsAnswer: Boolean get() = feedback == TurnFeedback.Revealed

    /** The numbers page is one tap away from a numbers task, and from no other. */
    val offersLookUp: Boolean get() = currentVariant == DrillVariant.Numbers

    /**
     * The way out, where it is wanted: under the button that goes on, on the SECOND miss in a
     * row. One miss is what a drill is made of; two is where carrying on stops feeling like a
     * choice. Any correct answer takes the offer away again.
     */
    val offersFinish: Boolean get() = missRun >= 1 && feedback == TurnFeedback.Revealed

    val tally: DrillTally get() = DrillTally.of(outcomes)

    /**
     * Digit count of the numeric prompt on screen, null outside a forward numbers task: a
     * reversed prompt IS the reading, which already names the place a hint would introduce.
     */
    val currentDigits: Int?
        get() = if (currentVariant == DrillVariant.Numbers && !currentReversed) {
            currentTask.prompt.length
        } else {
            null
        }

    /** The place word, the first time a length appears and never again. */
    val placeValueHint: String?
        get() {
            val digits = currentDigits ?: return null
            if (digits in seenDigitCounts) return null
            return Trainer.placeValueHint(digits, mode.language)
        }

    /** What a correct answer earns: almost where the reference was read while the answer was owed. */
    internal val cleanOutcome: AnswerOutcome
        get() = if (hintUsed) AnswerOutcome.Almost else AnswerOutcome.Right
}
