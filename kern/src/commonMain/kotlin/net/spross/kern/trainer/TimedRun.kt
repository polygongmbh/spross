package net.spross.kern.trainer

/**
 * A run against a clock ([DrillModifier.Timed]), scored instead of streaked.
 *
 * Each clean answer scores its Sprosse, so the ramp ([DrillRamp.step]) is the penalty:
 * a miss drops a Sprosse, an almost scores nothing.
 * The platform owns the timer and sends [NumbersIntent.TimeUp].
 */
object TimedRun {

    /** How long a timed run lasts. */
    const val SECONDS: Int = 60

    /** What one booked answer adds to the score. */
    fun points(level: Int, correct: Boolean, clean: Boolean): Int =
        if (correct && clean) maxOf(1, level) else 0
}

/** A timed run's score and, for a challenge, the challenge it answered. */
data class TimedOutcome(
    val score: Int,
    val challenge: NumbersChallenge?,
) {
    /** The challenge's code carrying this score, for sending back; null outside a challenge. */
    val replyCode: String? get() = challenge?.code(score)

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
