package net.spross.kern.trainer

/**
 * A run played against a clock ([DrillModifier.Timed]): it ends when the time is up and is
 * worth a SCORE rather than a streak.
 *
 * Each clean answer scores the Sprosse it was given on, so the ramp ([DrillRamp.step]) is
 * the whole of the scoring: a miss drops the run a Sprosse and every later answer is worth
 * less, and an almost scores nothing and still spends the seconds it took. No penalty of its
 * own is needed on top.
 *
 * The TIMER is the platform's, like every other timer a run arms; kern names how long it
 * runs and takes [NumbersIntent.TimeUp] when it elapses.
 */
object TimedRun {

    /** How long a timed run lasts. */
    const val SECONDS: Int = 60

    /** What one booked answer adds to the score. */
    fun points(level: Int, correct: Boolean, clean: Boolean): Int =
        if (correct && clean) maxOf(1, level) else 0
}

/** What a timed run came to. */
data class TimedOutcome(val score: Int)
