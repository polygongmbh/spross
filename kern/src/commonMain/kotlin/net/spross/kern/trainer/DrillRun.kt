package net.spross.kern.trainer

import net.spross.kern.model.Language
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.ToneKind

// What the two ENDLESS drills — the slot run and the letter run — put around whatever they
// happen to be asking. Their state machines stay apart (a heard glyph and a typed numeral share
// no grammar); this is the whole of what they have in common, and a second copy of it is how
// two beats drift apart.

/**
 * What a reduction asks the platform to do about the world outside a drill.
 *
 * Timers, focus, sound files and playback are the platform's; WHICH branch waits and which
 * moves on is the run's rule, which is what these say.
 */
sealed class DrillEffect {
    /** Arm (or re-arm) the beat before the run moves on; a screen reader renders a tap instead. */
    data class ArmAdvance(val tier: AdvanceTier) : DrillEffect()

    data object CancelAdvance : DrillEffect()

    /** Sound the verdict. */
    data class Tone(val kind: ToneKind) : DrillEffect()

    /** A pause that waits for a tap must give the keyboard back, or it covers the button. */
    data object ReleaseFocus : DrillEffect()

    /**
     * Say the answer the question was graded against, in the language being learned, once the
     * verdict's chime is out of the way. Every verdict carries it — right, a slip, a miss, the
     * look-up — so a drill always ends a question on the sound of its answer; a beat armed
     * beside it waits for the reading to finish, or a clean answer would cut its own word off.
     * A side answered in the learner's own language carries none.
     */
    data class SayAnswer(val text: String, val language: Language) : DrillEffect()

    /**
     * Cut whatever is sounding — the reading belongs to the question being left (D5).
     * Every path that ENDS a question carries it, so a clip can never follow the learner
     * onto the next one.
     */
    data object Silence : DrillEffect()
}

/**
 * The counter an endless run carries: clean wins over the answers that were judged either way.
 *
 * Almost is in NEITHER half, for the reason the ramp already gives ([DrillRamp.step]) — the
 * counter and the Sprosse read the same answer, so what moves no Sprosse may move no count.
 */
data class DrillTally(val clean: Int, val judged: Int) {

    companion object {
        fun of(outcomes: List<AnswerOutcome>): DrillTally = DrillTally(
            clean = outcomes.count { it == AnswerOutcome.Right },
            judged = outcomes.count { it != AnswerOutcome.Almost },
        )
    }
}

/**
 * The ladder a run's best answer streak earns, as the RULE (the thresholds) rather than the badge:
 * canonically 🌱 [Sprout] · 💪 [Effort] · 🎉 [Cheer] · 🏆 [Trophy], but which glyph wears a
 * milestone is the platform's chrome.
 */
enum class AnswerStreakMilestone { Sprout, Effort, Cheer, Trophy }

/**
 * The whole of what a finished run has to say. It travels back to the page that started it
 * rather than filling a screen of its own: three figures do not earn a page, and a page they
 * do not earn is one more ✕ between a learner and their next run.
 */
data class DrillRunSummary(
    val done: Int,
    val bestAnswerStreak: Int,
    /**
     * The run beat the drill's standing record. A drill that keeps no record store leaves it
     * false, which drops the record line and the celebration with it.
     */
    val newRecord: Boolean,
    /** A timed run's score, and the challenge it answered; null for a run that was not timed. */
    val timed: TimedOutcome? = null,
) {
    /** The record figure: a timed run's score, otherwise the best answer streak. */
    val recordFigure: Int get() = timed?.score ?: bestAnswerStreak

    val milestone: AnswerStreakMilestone
        get() = when {
            bestAnswerStreak >= TROPHY_ANSWER_STREAK -> AnswerStreakMilestone.Trophy
            bestAnswerStreak >= CHEER_ANSWER_STREAK -> AnswerStreakMilestone.Cheer
            bestAnswerStreak >= EFFORT_ANSWER_STREAK -> AnswerStreakMilestone.Effort
            else -> AnswerStreakMilestone.Sprout
        }

    private companion object {
        const val TROPHY_ANSWER_STREAK = 10
        const val CHEER_ANSWER_STREAK = 5
        const val EFFORT_ANSWER_STREAK = 2
    }
}
