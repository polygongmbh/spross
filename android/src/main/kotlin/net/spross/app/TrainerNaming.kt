package net.spross.app

import net.spross.kern.trainer.DrillModifier
import net.spross.kern.trainer.DrillVariant
import net.spross.kern.trainer.LetterStage

/**
 * What kern's drill enums are CALLED to this learner.
 *
 * Kern names the rule and never the rendering, so the face and the wording of a variant,
 * a modifier and a stage live out here — one table, read by the overviews, the score line
 * and the result tile alike, so a run can never be named two things on one page.
 */

/**
 * A variant's face. Numbers, Clock and Forms deliberately borrow the slot kind's glyph:
 * they are the same exercise, met through the ladder rather than beside it.
 */
val DrillVariant.trainerEmoji: String
    get() = when (this) {
        DrillVariant.Numbers -> "🔢"
        DrillVariant.Clock -> "🕐"
        DrillVariant.Phrases -> "💬"
        DrillVariant.Forms -> "➗"
    }

fun Chrome.name(variant: DrillVariant): String = when (variant) {
    DrillVariant.Numbers -> numbersTitle
    DrillVariant.Clock -> variantClock
    DrillVariant.Phrases -> variantPhrases
    DrillVariant.Forms -> variantForms
}

/** Face and name together — how a row, a price and a mixed run's score line all read. */
fun Chrome.badge(variant: DrillVariant): String = "${variant.trainerEmoji} ${name(variant)}"

/**
 * A modifier has no face of its own: it changes every variant alike, so it is named and
 * explained in words.
 */
fun Chrome.name(modifier: DrillModifier): String = when (modifier) {
    DrillModifier.Reverse -> modifierReverse
    DrillModifier.Fast -> modifierFast
    DrillModifier.Mix -> modifierMix
}

fun Chrome.hint(modifier: DrillModifier): String = when (modifier) {
    DrillModifier.Reverse -> modifierReverseHint
    DrillModifier.Fast -> modifierFastHint
    DrillModifier.Mix -> modifierMixHint
}

fun Chrome.name(stage: LetterStage): String = when (stage) {
    LetterStage.ChoiceEasy -> stageChoiceEasy
    LetterStage.ChoiceConfusable -> stageChoiceConfusable
    LetterStage.Typed -> stageTyped
    LetterStage.Dictation -> stageDictation
}

fun Chrome.hint(stage: LetterStage): String = when (stage) {
    LetterStage.ChoiceEasy -> stageChoiceEasyHint
    LetterStage.ChoiceConfusable -> stageChoiceConfusableHint
    LetterStage.Typed -> stageTypedHint
    LetterStage.Dictation -> stageDictationHint
}

/**
 * What a locked row costs, straight out of kern's unlock table — never a price authored
 * beside it, which would go stale the day the table moves.
 *
 * Numbers counts DIGITS and its wording already wears the drill's face, so it prints as
 * the length it is; every other variant names itself and its rung.
 */
fun Chrome.unlockPrice(required: Map<DrillVariant, Int>): String {
    val parts = DrillVariant.entries.mapNotNull { variant ->
        val rung = required[variant] ?: return@mapNotNull null
        if (variant == DrillVariant.Numbers) {
            countLine(digitsOne, digitsMany, rung)
        } else {
            "${badge(variant)} ${level.format(rung)}"
        }
    }
    if (parts.isEmpty()) return unlockPrefix
    return "$unlockPrefix ${parts.joinToString(" · ")}"
}
