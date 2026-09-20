package net.spross.kern.trainer

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
     */
    val cleared: Set<Int> = emptySet(),
) {
    /** Where a fresh run opens: the lowest Sprosse not yet earned ([NumbersMode.entrySprosse]). */
    val entryLevel: Int get() = NumbersMode.entrySprosse(cleared, report.maxLevel)

    /**
     * What a spelling is actually graded by: [normalizer]'s strictness with the typo budget
     * scaled to the word's length ([AnswerNormalizer.lengthScaledTypos]). The flat per-word cap
     * the other drills carry is there to keep one number from reading as another, and a scramble
     * asks a vocabulary word rather than a reading — while its words run from four letters to
     * fifteen, which is the span one flat slip serves worst.
     */
    internal val grader: AnswerNormalizer? = normalizer?.lengthScaledTypos()
}

/**
 * One word-scramble run, whole and immutable.
 *
 * The learner's TEXT is not in here — the platform owns the field, the keyboard and the focus,
 * and hands text in through [WordScrambleIntent].
 *
 * No FSRS anywhere: the box is READ for the words it has grown and never written, so the run
 * books no review. What outlives it is the ladder — [bestLevel] and [clearedSprossen], which
 * the screen that started the run files.
 */
data class WordScrambleRunState(
    val config: WordScrambleRunConfig,
    /** The question on screen; null only once nothing can be asked any more. */
    val task: WordScrambleTask?,
    override val index: Int,
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

    /** The card opens: a slip and a miss each leave a spelling worth seeing whole. */
    val showsAnswer: Boolean
        get() = feedback is TurnFeedback.Almost || feedback == TurnFeedback.Revealed
}
