package net.spross.kern.trainer

import net.spross.kern.session.Saying
import net.spross.kern.model.Card
import net.spross.kern.model.Language
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.TurnFeedback

/** What the learner does to a letter run. */
sealed class LetterDrillIntent {
    /** A choice tile. One attempt per question: a second tap would be a retry, which has no verdict. */
    data class Choose(val glyph: String) : LetterDrillIntent()

    /** A keystroke on a typed format: an exact answer approves itself, with no Check tap. */
    data class InputChanged(val text: String) : LetterDrillIntent()

    data class Submit(val text: String) : LetterDrillIntent()

    /** "Aufdecken" on an empty field — the card carries the answer and the question books a miss. */
    data object Reveal : LetterDrillIntent()

    data object ConfirmPending : LetterDrillIntent()

    data object AdvanceElapsed : LetterDrillIntent()

    /** Keep practicing from a pause ([DrillPacing]): the same run goes on, and a fresh stretch starts. */
    data object KeepPracticing : LetterDrillIntent()
}

/** The closed result of one intent. */
data class LetterDrillReduction(val state: LetterDrillRunState, val effects: List<DrillEffect>)

/** What a closed letter run leaves behind — its figures and the Sprossen it answered out. */
data class LetterDrillClose(
    val state: LetterDrillRunState,
    /** null ⇒ nothing was answered: dismiss, report nothing. */
    val summary: DrillRunSummary?,
    /**
     * The tile and typed Sprossen this run climbed off or answered out before its first slip
     * ([DrillSprossen], the scrambles' ledger), for the store to OR into the mask under
     * [LetterDrillRunState.storageKey]. Dictation draws from the box, which grows, so it is
     * never cleared.
     */
    val clearedSprossen: Set<Int>,
    val effects: List<DrillEffect>,
)

/**
 * Everything one letter run is fixed to: what this device can ask, the learner's own cards, and
 * the grader dictation is judged by. Resolved when the run opens, never per question.
 */
class LetterDrillRunConfig(
    val report: LetterDrillAvailability.Report,
    /** The learner's real cards by id — dictation grades against the card's own identity. */
    val cards: Map<String, Card>,
    /**
     * The STRICT drill grader with the whole join in view: a per-word slip budget alone would
     * accept `kufungua` for `kufunga`, and only the catalog-wide grader withdraws that credit.
     * Null falls the dictation Sprosse back to glyph grading — defensive, never asserted.
     */
    val dictationGrader: CatalogAnswerGrader?,
    /**
     * The Sprossen earlier runs answered out, as the PLATFORM's store holds them
     * ([NumbersMode.clearedSprossen] over the mask under [LetterDrillRunState.storageKey]).
     */
    val cleared: Set<Int>,
)

/**
 * One letter run, whole and immutable.
 *
 * No FSRS anywhere (D12 — transcription is not recall): the box is READ, for the pacing figures
 * and the dictation pool, and never written. The run keeps no streak record; what its close
 * stores is the Sprossen it answered out.
 */
data class LetterDrillRunState(
    val config: LetterDrillRunConfig,
    /** The question on screen; null only once nothing can be asked any more. */
    val task: LetterDrillTask?,
    override val index: Int,
    val sprosse: Int,
    val winsAtSprosse: Int,
    /** The Sprossen climbed off before the run's first slip — what the close hands the store. */
    val clearedSprossen: Set<Int>,
    /** The counters every drill run keeps, booked as one ([DrillRunCore.book]). */
    override val core: DrillRunCore,
    /** The tile the learner picked, so the grid can mark both it and the answer. */
    val chosen: String?,
    override val feedback: TurnFeedback,
    override val finished: Boolean,
) : DrillRunProgress {
    val format: LetterFormat? get() = task?.format

    /** The formats that carry an input field. */
    val typing: Boolean get() = format == LetterFormat.Typed || format == LetterFormat.Dictation

    /**
     * The card opens, whatever the spelling was graded.
     * A slip leaves a spelling worth seeing whole;
     * a clean one opens it too, because the LETTERS were the question and the meaning never was.
     */
    val showsAnswer: Boolean get() = !owesAnswer

    /** Nothing through the reader: the question's sound IS the question, and plays past the mute on its own. */
    override val promptSaying: Saying? get() = null

    /** Nothing: the question already was the sound, and the answer is the glyph or the word it said. */
    override val answerSaying: Saying? get() = null

    /**
     * The Sprossen the store may keep of those climbed off so far.
     * Dictation draws from the box, which grows —
     * a Sprosse of it climbed today says nothing about the words it will hold tomorrow.
     */
    internal val keptSprossen: Set<Int>
        get() = clearedSprossen.filter { LetterDrill.formatFor(it) != LetterFormat.Dictation }.toSet()

    /** The kept Sprossen the store did not hold — what a pause for improving names. */
    internal val newSprossen: Int get() = (keptSprossen - config.cleared).size

    companion object {
        /** Where the answered-out mask is filed: one per learned language, with no direction. */
        fun storageKey(language: Language): String = "letters.$language"
    }
}
