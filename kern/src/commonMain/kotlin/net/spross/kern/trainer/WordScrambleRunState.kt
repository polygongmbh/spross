package net.spross.kern.trainer

import net.spross.kern.session.Saying
import net.spross.kern.model.Language
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.TurnFeedback

/**
 * What the learner does to a word-scramble run. Writing the word out IS the answer, so a
 * keystroke is an intent of its own — the typed drills' rule, which the arrangement drill
 * has no use for.
 */
sealed class WordScrambleIntent {

    /** A live keystroke: a word finished exactly right needs no check tap. */
    data class InputChanged(val text: String) : WordScrambleIntent()

    /** Check/Enter with text standing. */
    data class Submit(val text: String) : WordScrambleIntent()

    /** "Aufdecken" on an empty field — the card carries the spelling and the question books a miss. */
    data object Reveal : WordScrambleIntent()

    /** The explicit tap that books whatever the feedback already said. */
    data object ConfirmPending : WordScrambleIntent()

    /** The platform's armed beat elapsed. */
    data object AdvanceElapsed : WordScrambleIntent()

    /** Keep practicing from a pause ([DrillPacing]): the same run goes on, and a fresh stretch starts. */
    data object KeepPracticing : WordScrambleIntent()
}

/** The closed result of one intent: the next state plus what it asks for. */
data class WordScrambleReduction(
    val state: WordScrambleRunState,
    val effects: List<DrillEffect>,
)

/**
 * What a closed word run leaves behind: the figures, the furthest Sprosse it stood on, and the
 * Sprossen it EARNED for the store to keep.
 */
data class WordScrambleClose(
    val state: WordScrambleRunState,
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
 * Everything one word run is fixed to, resolved when it opens and never per question: the words
 * it may ask, the grader their spelling is judged by, and how far the ladder already stands.
 */
class WordScrambleRunConfig(
    val report: WordScrambleAvailability.Report,
    /**
     * The STRICT drill grader for the language the answer is owed in — the one being learned.
     * Null (a preview with no language info) grades plainly.
     */
    val normalizer: AnswerNormalizer?,
    /**
     * The Sprossen earlier runs earned, as the PLATFORM's store holds them
     * ([NumbersMode.clearedSprossen] over the mask under [NumbersMode.CLEARED_PREFIX]) — kern
     * reads no device state, so where the ladder stands arrives as a parameter.
     * A run fast-climbs them ([DrillSprossen.winsRequired]).
     */
    val cleared: Set<Int> = emptySet(),
) {
}

/**
 * One word-scramble run, whole and immutable.
 *
 * The learner's TEXT is not in here — the platform owns the field, the keyboard and the focus,
 * and hands text in through [WordScrambleIntent].
 *
 * No FSRS anywhere: the box is READ for the words it has settled and never written, so the run
 * books no review. What outlives it is the ladder — [bestSprosse] and [clearedSprossen], which
 * the screen that started the run files.
 */
data class WordScrambleRunState(
    val config: WordScrambleRunConfig,
    /** The question on screen; null only once nothing can be asked any more. */
    val task: WordScrambleTask?,
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
         * Where the ladder is filed: one mask per learned language, and no direction to split it
         * by — the mixed letters are only ever written back in the language they came from.
         */
        fun storageKey(language: Language): String = "wordscramble.$language"
    }

    /**
     * The card opens, whatever the spelling was graded.
     * A clean one raises it too: the LETTERS were the question and the meaning never was,
     * so a word that vanished the moment it landed was the one answer the drill never glossed.
     */
    val showsAnswer: Boolean get() = !owesAnswer

    /** The Sprossen this run cleared that the store did not hold — what a pause for improving names. */
    internal val newSprossen: Int get() = (clearedSprossen - config.cleared).size

    /** Nothing: the letters handed over spell the very word that would be heard. */
    override val promptSaying: Saying? get() = null

    /** The word whose letters were handed over. */
    override val answerSaying: Saying? get() = task?.let { Saying(it.display, it.language) }
}
