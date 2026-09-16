package net.spross.kern.trainer

import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.TurnFeedback

/**
 * What the learner does to a sentence-scramble run.
 *
 * There is no text and no submit: moving the last atom into place IS the answer, the same way
 * finishing a name is on the typed drills. Until then an atom can be taken back, so a slip of
 * the finger costs a tap rather than the question.
 */
sealed class SentenceScrambleIntent {

    /** Commit the atom at [index] in [SentenceScrambleTask.shuffled] to the end of the arrangement. */
    data class PlaceAtom(val index: Int) : SentenceScrambleIntent()

    /** Take back the atom standing at [index] of the arrangement so far. */
    data class ReturnAtom(val index: Int) : SentenceScrambleIntent()

    /** The explicit tap that books whatever the feedback already said. */
    data object ConfirmPending : SentenceScrambleIntent()

    /** The platform's armed beat elapsed. */
    data object AdvanceElapsed : SentenceScrambleIntent()
}

/** The closed result of one intent: the next state plus what it asks for. */
data class SentenceScrambleReduction(
    val state: SentenceScrambleRunState,
    val effects: List<DrillEffect>,
)

/**
 * What a closed sentence run leaves behind: the figures, the furthest Sprosse it stood on, and
 * the Sprossen it EARNED for the store to keep.
 */
data class SentenceScrambleClose(
    val state: SentenceScrambleRunState,
    /** null ⇒ nothing was answered: dismiss, report nothing. */
    val summary: DrillRunSummary?,
    /**
     * The Sprosse the run REACHED, not the one it ends on — the ramp drops back on a miss, and
     * the ladder rewards standing on a Sprosse rather than finishing there.
     */
    val bestLevel: Int,
    /**
     * The Sprossen this run climbed off without a blemish ([DrillSprossen]), for the store to add
     * to the mask it holds — the next run opens on the lowest one that is still missing.
     * Unfiltered: unlike [bestLevel] there is no standing value to beat.
     */
    val clearedSprossen: Set<Int>,
    val effects: List<DrillEffect>,
)

/**
 * Everything one sentence run is fixed to, resolved when it opens: the phrases it may ask and
 * how far the ladder already stands.
 */
class SentenceScrambleRunConfig(
    val report: SentenceScrambleAvailability.Report,
    /**
     * The Sprossen earlier runs earned, as the PLATFORM's store holds them
     * ([TrainerMode.clearedSprossen] over the mask under [TrainerMode.CLEARED_PREFIX]) — kern
     * reads no device state, so where the ladder stands arrives as a parameter.
     */
    val cleared: Set<Int> = emptySet(),
) {
    /** Where a fresh run opens: the lowest Sprosse not yet earned ([TrainerMode.entrySprosse]). */
    val entryLevel: Int get() = TrainerMode.entrySprosse(cleared, report.maxLevel)
}

/**
 * One sentence-scramble run, whole and immutable.
 *
 * No FSRS anywhere — arrangement is not recall: the box is READ for the phrases it has
 * unlocked and never written, so the run books no review. What outlives it is the ladder —
 * [bestLevel] and [clearedSprossen], which the screen that started the run files.
 */
data class SentenceScrambleRunState(
    val config: SentenceScrambleRunConfig,
    /** The question on screen; null only once nothing can be asked any more. */
    val task: SentenceScrambleTask?,
    /** Indices into [SentenceScrambleTask.shuffled], in the order the learner committed them. */
    val placed: List<Int>,
    val index: Int,
    val level: Int,
    val bestLevel: Int,
    val winsAtLevel: Int,
    /** The Sprossen climbed off unblemished so far — what the close hands the store. */
    val clearedSprossen: Set<Int>,
    /**
     * Whether the Sprosse the run stands on has already lost the store: an almost or a miss on
     * it. It costs the run nothing else — the streak, the banked wins and the ramp are all
     * [DrillRamp]'s, and a blemish moves none of them.
     */
    val blemished: Boolean,
    /** The counters every drill run keeps, booked as one ([DrillRunCore.book]). */
    val core: DrillRunCore,
    val feedback: TurnFeedback,
    val finished: Boolean,
) {
    val done: Int get() = core.done

    val streak: Int get() = core.streak

    val bestStreak: Int get() = core.bestStreak

    val missRun: Int get() = core.missRun

    val outcomes: List<AnswerOutcome> get() = core.outcomes

    val solved: Set<String> get() = core.solved

    val owesAnswer: Boolean get() = feedback == TurnFeedback.Neutral

    /** Correct or almost: something is pending that closing must book rather than lose. */
    val answerAccepted: Boolean
        get() = feedback == TurnFeedback.Correct || feedback is TurnFeedback.Almost

    /**
     * The card is up, whatever the arrangement was graded.
     * A clean one raises it too: the ORDER was the question and the MEANING never was,
     * so an arrangement that vanished the moment it landed
     * was the one answer the drill never glossed.
     */
    val showsAnswer: Boolean get() = !owesAnswer

    /** The way out, under the button that goes on, on the second miss in a row. */
    val offersFinish: Boolean get() = missRun >= 1 && feedback == TurnFeedback.Revealed

    val tally: DrillTally get() = DrillTally.of(outcomes)

    /** The arrangement so far, in order. */
    val placedAtoms: List<ScrambleAtom>
        get() = task?.let { t -> placed.mapNotNull { t.shuffled.getOrNull(it) } }.orEmpty()

    /** What the arrangement reads as — the answer a screen reader is given. */
    val arranged: String get() = ScrambleTokenizer.joined(placedAtoms)

    /** Whether the dealt atom at [index] has already been committed. */
    fun isPlaced(index: Int): Boolean = index in placed

    /** How many atoms are still to be placed. */
    val remaining: Int get() = (task?.size ?: 0) - placed.size

    /** Every atom stands somewhere: the arrangement is the answer, and it has been given. */
    val complete: Boolean get() = task != null && remaining == 0
}
