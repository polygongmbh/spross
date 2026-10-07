package net.spross.kern.trainer

import net.spross.kern.model.ClosingNote
import net.spross.kern.model.EmojiCue
import net.spross.kern.session.Question
import net.spross.kern.session.Saying
import net.spross.kern.model.Language
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

    /** Keep practicing from a pause ([DrillPacing]): the same run goes on, and a fresh stretch starts. */
    data object KeepPracticing : SentenceScrambleIntent()
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
    val bestSprosse: Int,
    /**
     * The Sprossen this run climbed off before its first slip ([DrillSprossen]), for the store to add
     * to the mask it holds — the Sprossen later runs pass on one clean answer
     * ([DrillSprossen.winsRequired]).
     * Unfiltered: unlike [bestSprosse] there is no standing value to beat.
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
     * ([NumbersMode.clearedSprossen] over the mask under [NumbersMode.CLEARED_PREFIX]) — kern
     * reads no device state, so where the ladder stands arrives as a parameter.
     * A run fast-climbs them ([DrillSprossen.winsRequired]).
     */
    val cleared: Set<Int> = emptySet(),
)

/**
 * One sentence-scramble run, whole and immutable.
 *
 * No FSRS anywhere — arrangement is not recall: the box is READ for its phrases
 * and never written, so the run books no review. What outlives it is the ladder —
 * [bestSprosse] and [clearedSprossen], which the screen that started the run files.
 */
data class SentenceScrambleRunState(
    val config: SentenceScrambleRunConfig,
    /** The question on screen; null only once nothing can be asked any more. */
    val task: SentenceScrambleTask?,
    /** Indices into [SentenceScrambleTask.shuffled], in the order the learner committed them. */
    val placed: List<Int>,
    override val index: Int,
    val sprosse: Int,
    val bestSprosse: Int,
    val winsAtSprosse: Int,
    /** The Sprossen climbed off before the run's first slip — what the close hands the store. */
    val clearedSprossen: Set<Int>,
    /** The counters every drill run keeps, booked as one ([DrillRunCore.book]). */
    override val core: DrillRunCore,
    override val feedback: TurnFeedback,
    override val finished: Boolean,
) : DrillRunProgress {
    companion object {
        /**
         * [WordScrambleRunState.storageKey]'s twin: one mask per learned language, and no
         * direction to split it by — a phrase is only ever put back in order.
         */
        fun storageKey(language: Language): String = "sentencescramble.$language"
    }

    /**
     * The card is up, whatever the arrangement was graded.
     * A clean one raises it too: the ORDER was the question and the MEANING never was,
     * so an arrangement that vanished the moment it landed
     * was the one answer the drill never glossed.
     */
    val showsAnswer: Boolean get() = !owesAnswer

    /** Nothing: the phrase heard in order is the order being asked for. */
    override val promptSaying: Saying? get() = null

    /** The phrase as authored, whichever order was accepted. */
    override val answerSaying: Saying? get() = task?.let { Saying(it.display, it.language) }

    /**
     * The bank is the prompt, so no words stand on it.
     * The card opens onto the meaning always, and onto the authored order only where the arrangement missed
     * or took another order — a clean one already stands in that order, and setting it twice reads as a correction.
     * An alternative order drops the meaning, which belongs to the authored one.
     */
    override val question: Question?
        get() = task?.let { t ->
            val showsOrder = !answerAccepted || alternativeMatch
            Question(
                key = index.toString(),
                ask = null,
                prompt = Question.Side(null, t.language, Question.Form.Sentence),
                answer = Question.Side(
                    t.display.takeIf { showsOrder }, t.language, Question.Form.Sentence,
                    saying = answerSaying.takeIf { showsOrder },
                ),
                emoji = null,
                emojiCue = EmojiCue.Upfront,
                opens = showsAnswer,
                closing = Question.Closing(note = t.gloss.takeUnless { alternativeMatch }?.let { ClosingNote.Own(it) }),
            )
        }

    /** Accepted via an alternative word order rather than the canonical one — gloss not shown. */
    val alternativeMatch: Boolean
        get() {
            if (!answerAccepted) return false
            val t = task ?: return false
            return !ScrambleGrading.matchesCanonical(placedAtoms, t.canonical)
        }

    /** The arrangement so far, in order. */
    val placedAtoms: List<ScrambleAtom>
        get() = task?.let { t -> placed.mapNotNull { t.shuffled.getOrNull(it) } }.orEmpty()

    /** What the arrangement reads as — the answer a screen reader is given. */
    val arranged: String get() = ScrambleTokenizer.joined(placedAtoms)

    /** The Sprossen this run cleared that the store did not hold — what a pause for improving names. */
    internal val newSprossen: Int get() = (clearedSprossen - config.cleared).size

    /** Whether the dealt atom at [index] has already been committed. */
    fun isPlaced(index: Int): Boolean = index in placed

    /** How many atoms are still to be placed. */
    val remaining: Int get() = (task?.size ?: 0) - placed.size

    /** Every atom stands somewhere: the arrangement is the answer, and it has been given. */
    val complete: Boolean get() = task != null && remaining == 0
}
