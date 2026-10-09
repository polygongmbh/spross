package net.spross.kern.trainer

import net.spross.kern.model.Language
import net.spross.kern.session.AnswerControls
import net.spross.kern.session.AnswerControls.Confirm
import net.spross.kern.session.AnswerControls.Primary
import net.spross.kern.session.AnswerControls.Slot
import net.spross.kern.session.TurnFeedback

/**
 * A drill's controls around [slot], the same for every drill:
 * the one primary action stands while a written answer is owed (tiles and an arrangement answer by themselves);
 * a near miss and a miss hold until tapped, a clean answer only where no beat may run;
 * nothing gives up — a reveal already counts as the miss — and the way out stands where the run offers it.
 */
internal fun DrillRunProgress.answerControls(slot: Slot): AnswerControls = AnswerControls(
    slot = slot,
    fieldFeedback = feedback,
    primary = Primary.Submit.takeIf { owesAnswer && slot is Slot.Typed },
    confirm = when (feedback) {
        TurnFeedback.Neutral -> null
        TurnFeedback.Correct -> Confirm.WhenNoBeat
        else -> Confirm.Always
    },
    giveUp = null,
    stop = offersFinish,
)

/** A drill's field: a miss leaves it holding what was written, since a drill has nothing to retype. */
internal fun DrillRunProgress.typedSlot(lang: Language, digits: Boolean = false, numberPad: Boolean = false): Slot =
    Slot.Typed(lang, digits, numberPad, editable = feedback != TurnFeedback.Revealed)
