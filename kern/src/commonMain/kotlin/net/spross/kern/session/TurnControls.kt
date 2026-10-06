package net.spross.kern.session

import net.spross.kern.model.ProducePrompt
import net.spross.kern.session.AnswerControls.Confirm
import net.spross.kern.session.AnswerControls.GiveUp
import net.spross.kern.session.AnswerControls.Primary
import net.spross.kern.session.AnswerControls.Slot

/**
 * What stands under the review card:
 * - the write-out owns the turn while it is open;
 * - recognition and a recalled production are never typed: one reveal, then the three verdicts;
 * - other production is typed, and a blank reveal hands the turn to the verdicts too;
 * - a miss keeps the field open for the retype, with a quiet skip beside it —
 *   except a miss asked by ear, which retypes nothing and goes on with one Next;
 * - a near miss holds on its correction until tapped, a clean answer and a finished retype
 *   only where no beat may run;
 * - while a card asked by ear is still asking, the learner who cannot listen may have it in writing.
 */
val TurnState.controls: AnswerControls
    get() {
        val step = copyStep
        val target = card.target.lang
        return when {
            step != null -> AnswerControls(
                slot = Slot.WriteOut(target, step.missed),
                fieldFeedback = if (step.written) TurnFeedback.Correct else TurnFeedback.Neutral,
                primary = null, confirm = null, giveUp = GiveUp.Skip,
            )
            revealed && feedback == TurnFeedback.Neutral -> AnswerControls(
                slot = Slot.SelfGrade, fieldFeedback = feedback, primary = null, confirm = null, giveUp = null,
            )
            !typesAnswer -> AnswerControls(
                slot = null, fieldFeedback = feedback, primary = Primary.Reveal, confirm = null, giveUp = null,
                cantListen = prompt == ProducePrompt.Sound && !promptInText,
            )
            feedback == TurnFeedback.Revealed && !retypes -> AnswerControls(
                slot = null, fieldFeedback = feedback, primary = null, confirm = null, giveUp = GiveUp.Next,
            )
            else -> AnswerControls(
                slot = Slot.Typed(answerLang),
                fieldFeedback = if (retryApproved) TurnFeedback.Correct else feedback,
                primary = Primary.Submit.takeIf { feedback == TurnFeedback.Neutral },
                confirm = when (feedback) {
                    TurnFeedback.Neutral -> null
                    TurnFeedback.Correct -> Confirm.WhenNoBeat
                    is TurnFeedback.Almost -> Confirm.Always
                    TurnFeedback.Revealed -> Confirm.WhenNoBeat.takeIf { retryApproved }
                },
                giveUp = GiveUp.Skip.takeIf { feedback == TurnFeedback.Revealed },
                cantListen = prompt == ProducePrompt.Sound && !promptInText && feedback == TurnFeedback.Neutral,
            )
        }
    }
