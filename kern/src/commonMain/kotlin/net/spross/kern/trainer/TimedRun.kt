package net.spross.kern.trainer

/**
 * A run against a clock ([DrillModifier.Timed]), scored instead of streaked.
 *
 * Each clean answer scores its Sprosse, so the ramp ([DrillRamp.step]) is the penalty:
 * a miss drops a Sprosse, an almost scores nothing.
 * The clock is [SECONDS] plus what clean answers earned ([NumbersRunState.earnedSeconds]),
 * so a long number pays back the time it takes to type.
 * The platform owns the timer, holds it while [NumbersRunState.clockStopped], and sends [NumbersIntent.TimeUp].
 */
object TimedRun {

    /** How long a timed run lasts before any answer earned it more. */
    const val SECONDS: Int = 60

    /**
     * What is left of the clock after [runningMillis] of it ran —
     * time spent where [NumbersRunState.clockStopped] excluded — with what answers earned added.
     */
    fun remainingMillis(runningMillis: Long, earnedSeconds: Int): Long =
        (SECONDS + earnedSeconds) * 1_000L - runningMillis

    /** Whole seconds left of [remainingMillis], counted up: the clock reads 0:00 only once time is up. */
    fun secondsLeft(remainingMillis: Long): Int = ((remainingMillis + 999) / 1_000).toInt().coerceAtLeast(0)

    /** The clock at the head of the score line, as minutes and seconds. */
    fun clock(secondsLeft: Int): String = "⏱ ${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}"

    /** What one booked answer adds to the score. */
    fun points(sprosse: Int, correct: Boolean, clean: Boolean): Int =
        if (correct && clean) maxOf(1, sprosse) else 0

    /** What one booked answer adds to the clock: a second per non-zero digit of the asked number. */
    fun bonusSeconds(task: NumbersTask, correct: Boolean, clean: Boolean): Int =
        if (correct && clean) task.prompt.count { it in '1'..'9' } else 0
}

/** A timed run's score and, for a challenge, the challenge it answered. */
data class TimedOutcome(
    val score: Int,
    val challenge: NumbersChallenge?,
) {
    /** The challenge's code carrying this score, for sending back; null outside a challenge. */
    val replyCode: String? get() = challenge?.code(score)

    /** [replyCode] as the link a reply is sent as. */
    val replyLink: String? get() = challenge?.link(score)

    /** How this score stands against the one the code arrived with; null where none came. */
    val verdict: ChallengeVerdict?
        get() = challenge?.opponentScore?.let { theirs ->
            when {
                score > theirs -> ChallengeVerdict.Won
                score == theirs -> ChallengeVerdict.Tied
                else -> ChallengeVerdict.Lost
            }
        }
}

/** A challenge answered against a score the code carried. */
enum class ChallengeVerdict { Won, Tied, Lost }
