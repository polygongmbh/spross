package net.spross.kern.trainer

import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Cue

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
    /** The verdict's cue around the run — the beat, the sound, the keyboard. */
    data class Cued(val cue: Cue) : DrillEffect()

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

/** What a closed run's tile names beside its tally ([DrillRunSummary.highlight]): a Sprosse reached, or an answer streak. */
data class DrillHighlight(val kind: Kind, val value: Int) {
    enum class Kind { Sprosse, Streak }

    /** Which of [count] phrasings names it. */
    fun line(count: Int): Int = value.mod(count)
}

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
    /** A challenge's score and the challenge it answered; null for every other run. */
    val timed: TimedOutcome? = null,
    /** The roster entry the run belongs to — its icon names the run where a tile stands among every drill (the hub). */
    val drill: Drill? = null,
    /** The highest Sprosse the run stood on, where above the one it opened on ([DrillPacing.peak]). */
    val peak: Int? = null,
    /** The run cleared a Sprosse the store did not hold ([DrillPacing.newSprossen]). */
    val newSprosse: Boolean = false,
) {
    /**
     * Whether the figures are worth reporting where nothing asked for them — a drill with no
     * page of its own reports on the hub only after a stretch a pause could also have
     * stopped ([DrillPacing.STRUGGLING_AFTER]); a couple of answers is a peek, not a run.
     */
    val worthReporting: Boolean get() = done >= DrillPacing.STRUGGLING_AFTER

    /**
     * Whether the close earns the celebration — a record beaten, or any run long enough to
     * report: finishing a stretch of practice is cheered as a finished round is, record or not.
     */
    val celebrated: Boolean get() = newRecord || worthReporting

    /**
     * The one thing the tile names beside the tally — a highlight, never a statistic:
     * a Sprosse first cleared outranks the streak, a streak of [HIGHLIGHT_STREAK] or more
     * outranks a climb the store already knew, and a run with neither names nothing.
     */
    val highlight: DrillHighlight?
        get() {
            val sprosse = peak?.let { DrillHighlight(DrillHighlight.Kind.Sprosse, it) }
            val streak = DrillHighlight(DrillHighlight.Kind.Streak, bestAnswerStreak)
                .takeIf { bestAnswerStreak >= HIGHLIGHT_STREAK }
            return if (newSprosse) sprosse ?: streak else streak ?: sprosse
        }

    val milestone: AnswerStreakMilestone
        get() = when {
            bestAnswerStreak >= TROPHY_ANSWER_STREAK -> AnswerStreakMilestone.Trophy
            bestAnswerStreak >= CHEER_ANSWER_STREAK -> AnswerStreakMilestone.Cheer
            bestAnswerStreak >= EFFORT_ANSWER_STREAK -> AnswerStreakMilestone.Effort
            else -> AnswerStreakMilestone.Sprout
        }

    private companion object {
        const val TROPHY_ANSWER_STREAK = 15
        const val CHEER_ANSWER_STREAK = 10
        const val EFFORT_ANSWER_STREAK = 5
        const val HIGHLIGHT_STREAK = 4
    }
}
