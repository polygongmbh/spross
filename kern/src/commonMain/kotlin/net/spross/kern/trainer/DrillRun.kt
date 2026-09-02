package net.spross.kern.trainer

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
 * The ladder a run's best streak earns, as the RULE (the thresholds) rather than the badge:
 * canonically 🌱 [Sprout] · 💪 [Effort] · 🎉 [Cheer] · 🏆 [Trophy], but which glyph wears a
 * tier is the platform's chrome.
 */
enum class StreakTier { Sprout, Effort, Cheer, Trophy }

/**
 * The whole of what a finished run has to say. It travels back to the page that started it
 * rather than filling a screen of its own: three figures do not earn a page, and a page they
 * do not earn is one more ✕ between a learner and their next run.
 */
data class DrillRunSummary(
    val done: Int,
    val bestStreak: Int,
    /**
     * The run beat the drill's standing record. A drill that keeps no record store leaves it
     * false, which drops the record line and the celebration with it.
     */
    val newRecord: Boolean,
) {
    val tier: StreakTier
        get() = when {
            bestStreak >= TROPHY_STREAK -> StreakTier.Trophy
            bestStreak >= CHEER_STREAK -> StreakTier.Cheer
            bestStreak >= EFFORT_STREAK -> StreakTier.Effort
            else -> StreakTier.Sprout
        }

    private companion object {
        const val TROPHY_STREAK = 10
        const val CHEER_STREAK = 5
        const val EFFORT_STREAK = 2
    }
}
