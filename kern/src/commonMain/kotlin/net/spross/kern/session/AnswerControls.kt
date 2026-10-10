package net.spross.kern.session

import net.spross.kern.model.Language

/**
 * What stands under the card for the question on screen — a review turn and a drill task alike —
 * so each app draws one answer area instead of choosing its controls per screen.
 *
 * Nothing here is worded or placed: each app labels the controls from its string table,
 * and the primary action's label stays its reading of [AnswerNormalizer.isBlankAnswer].
 */
data class AnswerControls(
    /** What the answer is given with; null where nothing is left to give it with — a miss nothing retypes. */
    val slot: Slot?,
    /** The field's own verdict, which parts ways with the card's on a finished retype. */
    val fieldFeedback: TurnFeedback,
    /** The one primary action while the answer is owed; null once it is not. */
    val primary: Primary?,
    /** The tap that books what the verdict already said; null where nothing waits on it. */
    val confirm: Confirm?,
    /** The way on without answering; null where none is offered. */
    val giveUp: GiveUp?,
    /** An endless run's way out, offered under the button that goes on. */
    val stop: Boolean = false,
    /** The way out of a question asked by ear, while it is still asking. */
    val cantListen: Boolean = false,
) {
    sealed interface Slot {
        /**
         * A field the answer is written in, in [lang].
         * [digits]: a value is owed, written alike in every language;
         * [numberPad]: that value needs nothing a number pad lacks ([typableOnNumberPad]).
         * An uneditable field keeps what the learner wrote and takes no more; empty, it stands for nothing.
         */
        data class Typed(
            val lang: Language,
            val digits: Boolean = false,
            val numberPad: Boolean = false,
            val editable: Boolean = true,
        ) : Slot

        /** Tiles the answer is picked off, in kern's own order; [answer] is the one the verdict marks right. */
        data class Choices(val options: List<String>, val answer: String) : Slot

        /** The question's own pieces, put in order; placing the last one is the answer. */
        data object Arrangement : Slot

        /** The three verdicts a learner gives on a word they asked to see. */
        data object SelfGrade : Slot

        /** The write-out: the word typed once in [lang] with the answer in view; [missed] says the copy was another word. */
        data class WriteOut(val lang: Language, val missed: Boolean) : Slot
    }

    enum class Primary {
        /** Check what stands, or reveal on a blank field ([TurnIntent.Submit]). */
        Submit,

        /** Show the answer; nothing is typed ([TurnIntent.Reveal]). */
        Reveal,

        /** Check what stands, or skip on a blank field: a race books the miss and shows no answer. */
        SubmitOrSkip,
    }

    enum class Confirm {
        /** The verdict holds until it is tapped on. */
        Always,

        /** It stands in for the beat, where no beat may run ([TurnEffect.ArmAdvance]). */
        WhenNoBeat,
    }

    enum class GiveUp {
        /** A quiet way past what is still open — a retype, a write-out. */
        Skip,

        /** The one way on, where nothing is left to type. */
        Next,
    }
}

/**
 * Whether one of [accepted] needs nothing a number pad lacks. The pad carries digits,
 * `.`, `,`, `-` and a space — no `:` and no `/`, so a time or a fraction written that
 * way would be owed on a keyboard that cannot type it.
 */
fun typableOnNumberPad(accepted: List<String>): Boolean =
    accepted.any { form -> form.all { it.isDigit() || it in ".,-   " } }
