package net.spross.kern.trainer

import net.spross.kern.model.Card
import net.spross.kern.session.AnswerTone
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.TurnFeedback

/**
 * What a typed letter-drill answer earns. The two amber verdicts move the ramp neither way —
 * neither a win to bank nor a miss to punish.
 */
sealed class LetterVerdict {
    data object Clean : LetterVerdict()

    /** A slip inside the budget; [corrected] is the spelling that was owed. */
    data class Typo(val corrected: String) : LetterVerdict()

    /** A form this very card accepts, but not the one that PLAYED. */
    data class Heard(val played: String) : LetterVerdict()

    data object Wrong : LetterVerdict()
}

/** What the learner does to a letter run. There is no live approve — a typed glyph is submitted. */
sealed class LetterDrillIntent {
    /** A choice tile. One attempt per question: a second tap would be a retry, which has no verdict. */
    data class Choose(val glyph: String) : LetterDrillIntent()

    data class Submit(val text: String) : LetterDrillIntent()

    /** "Aufdecken" on an empty field — the card carries the answer and the question books a miss. */
    data object Reveal : LetterDrillIntent()

    data object ConfirmPending : LetterDrillIntent()

    data object AdvanceElapsed : LetterDrillIntent()
}

/** The closed result of one intent. */
data class LetterDrillReduction(val state: LetterDrillRunState, val effects: List<DrillEffect>)

/** What a closed letter run leaves behind — figures only; no record, no rung (D12). */
data class LetterDrillClose(
    val state: LetterDrillRunState,
    /** null ⇒ nothing was answered: dismiss, report nothing. */
    val summary: DrillRunSummary?,
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
     * Null falls the dictation rung back to glyph grading — defensive, never asserted.
     */
    val dictationGrader: CatalogAnswerGrader?,
)

/**
 * One letter run, whole and immutable.
 *
 * No FSRS anywhere (D12 — transcription is not recall): the box is READ, for the pacing figures
 * and the dictation pool, and never written. The run keeps no record and books no rung, so its
 * close has nothing to store.
 */
data class LetterDrillRunState(
    val config: LetterDrillRunConfig,
    /** The question on screen; null only once nothing can be asked any more. */
    val task: LetterDrillTask?,
    val index: Int,
    val level: Int,
    val winsAtLevel: Int,
    val done: Int,
    val streak: Int,
    val bestStreak: Int,
    /** Misses in a row already booked — 1 while a miss shows means this is the second. */
    val missRun: Int,
    val outcomes: List<AnswerTone>,
    /** The tile the learner picked, so the grid can mark both it and the answer. */
    val chosen: String?,
    val feedback: TurnFeedback,
    val finished: Boolean,
) {
    val stage: LetterStage? get() = task?.stage

    /** The stages that carry an input field. */
    val typing: Boolean get() = stage == LetterStage.Typed || stage == LetterStage.Dictation

    val owesAnswer: Boolean get() = feedback == TurnFeedback.Neutral

    val answerAccepted: Boolean
        get() = feedback == TurnFeedback.Correct || feedback is TurnFeedback.Almost

    /**
     * The card opens. Unlike the slot drill BOTH amber holds reveal too: a slip and a
     * heard-instead each leave a spelling worth seeing whole, and the box under the field says
     * which of the two it was.
     */
    val showsAnswer: Boolean
        get() = feedback is TurnFeedback.Almost || feedback == TurnFeedback.Revealed

    /** The way out, under the button that goes on, on the second miss in a row. */
    val offersFinish: Boolean get() = missRun >= 1 && feedback == TurnFeedback.Revealed

    val cleanCount: Int get() = outcomes.count { it != AnswerTone.Wrong }
}
