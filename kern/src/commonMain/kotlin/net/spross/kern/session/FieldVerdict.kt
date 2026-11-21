package net.spross.kern.session

import net.spross.kern.design.Palette
import net.spross.kern.design.Swatch

/**
 * How the answer field wears where its answer stands ([AnswerControls.fieldFeedback]):
 * its edge, the checkmark beside the text and the correction box under it,
 * on the review card and every typed drill alike.
 *
 * The answer is never on screen twice, and never in the field:
 * an accepted answer stands in the learner's own text and the card stays closed,
 * a miss or a reveal opens the card onto it ([Question.opens]).
 * Green is the clean answer's alone; a near miss runs amber at the edge, the checkmark and the box alike,
 * a merged meaning with full credit included — it sounds correct ([ToneKind.chime]) and still owes this card's word back.
 * Correctness is never color alone: the checkmark and the spoken verdict say it too.
 */
enum class FieldVerdict {
    /** Nothing decided yet. */
    Open,

    /** The word came back clean. */
    Clean,

    /**
     * Accepted, not cleanly: the correction box under the field sets the owed form
     * at a readable size with its speaker beside it, and the card stays closed.
     */
    NearMiss,

    /** Nothing was produced: the card carries the answer, so the field carries no mark. */
    Shown;

    /**
     * The edge, the checkmark and the correction box.
     * [Open] wears the control boundary, which a platform may hand to its own focused-field style.
     */
    val ink: Swatch
        get() = when (this) {
            Open -> Palette.borderStrong
            Clean -> Palette.success
            NearMiss, Shown -> Palette.amber
        }

    /** The checkmark that says ACCEPTED, in [ink]; nothing about a shown answer was. */
    val check: Boolean get() = this == Clean || this == NearMiss

    companion object {
        fun of(feedback: TurnFeedback): FieldVerdict = when (feedback) {
            TurnFeedback.Neutral -> Open
            TurnFeedback.Correct -> Clean
            is TurnFeedback.Almost -> NearMiss
            TurnFeedback.Revealed -> Shown
        }
    }
}
