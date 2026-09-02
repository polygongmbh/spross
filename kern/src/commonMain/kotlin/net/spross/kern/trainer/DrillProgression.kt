package net.spross.kern.trainer

/**
 * What a RUN can offer — the learner-facing choice on the numbers overview.
 *
 * Not to be confused with [TrainerKind], which is what fills a SLOT: a Phrases run
 * draws tasks whose kind is Numbers, Years or Clock, because the kind names the
 * generator behind the slot while the variant names the exercise around it.
 * Progress is kept per variant, so the two must never be collapsed.
 */
enum class DrillVariant { Numbers, Clock, Phrases, Forms }

/**
 * How a run is played, orthogonal to which [DrillVariant]s it offers.
 * [Reverse] flips the direction (words shown, digits typed), [Fast] halves the
 * rung length, [Mix] flips direction per task and lets Forms draw their magnitude
 * from the Numbers ladder instead of their own gentler one.
 */
enum class DrillModifier { Reverse, Fast, Mix }

/**
 * The unlock ladder, as one table rather than a chain of conditions:
 * every requirement is a map from a variant to the level that must have been
 * reached in it, and an empty map means always available.
 *
 * Progress is the highest level ever reached per variant per language, persisted
 * by the app — there is no second source of truth and kern stores nothing.
 */
object DrillUnlocks {

    private val variantRequirements: Map<DrillVariant, Map<DrillVariant, Int>> = mapOf(
        DrillVariant.Numbers to emptyMap(),
        DrillVariant.Clock to mapOf(DrillVariant.Numbers to 4),
        // why: a sentence puts a time inside a clause, so it opens on a clock that is
        // finished — the ladder's top rung, tracked by name so growing the ladder
        // moves the gate with it instead of quietly cheapening it.
        DrillVariant.Phrases to mapOf(DrillVariant.Clock to CLOCK_MAX_LEVEL),
        DrillVariant.Forms to mapOf(DrillVariant.Numbers to 7),
    )

    /**
     * Phrases is deliberately absent from every modifier gate: a pair's phrase ceiling
     * depends on which frames the catalog happens to realize for it, so a fixed rung
     * there would be unreachable for some pairs.
     */
    private val modifierRequirements: Map<DrillModifier, Map<DrillVariant, Int>> = mapOf(
        // why: decoding is a different exercise from producing, not an easier one —
        // it waits until numbers and the clock have both been worked a while. The
        // clock carries the gate because reaching its third rung already means the
        // numbers below it were climbed.
        DrillModifier.Reverse to mapOf(DrillVariant.Clock to 3),
        DrillModifier.Fast to mapOf(DrillVariant.Numbers to 10),
        // why: the forms rung alone — Forms costs seven digits to open at all, so it
        // carries the numbers climb with it, and asking for the billions on top would
        // price a way of PLAYING a run above the exercises it plays.
        DrillModifier.Mix to mapOf(DrillVariant.Forms to 5),
    )

    /** What [variant] costs, as variant → level reached. Empty = always available. */
    fun requirements(variant: DrillVariant): Map<DrillVariant, Int> =
        variantRequirements.getValue(variant)

    /** What [modifier] costs, as variant → level reached. Empty = always available. */
    fun requirements(modifier: DrillModifier): Map<DrillVariant, Int> =
        modifierRequirements.getValue(modifier)

    fun unlocked(variant: DrillVariant, progress: Map<DrillVariant, Int>): Boolean =
        met(requirements(variant), progress)

    fun unlocked(modifier: DrillModifier, progress: Map<DrillVariant, Int>): Boolean =
        met(requirements(modifier), progress)

    private fun met(required: Map<DrillVariant, Int>, progress: Map<DrillVariant, Int>): Boolean =
        required.all { (variant, level) -> (progress[variant] ?: 0) >= level }
}

/**
 * The rung ramp INSIDE a run — the other half of the progression, and the one every
 * drill shares. How long a rung is stays the caller's rule
 * ([LetterDrill.winsToAdvance] counts a held vocabulary, [Trainer.winsToAdvance]
 * reads the Fast modifier); what a rung DOES with an answer is decided here once.
 */
object DrillRamp {

    /**
     * [winsRequired] clean wins up, one miss down, floor 1.
     *
     * An almost answer ([clean] false: a typo, a revealed hint, a synonym) moves NOTHING.
     * It is neither a win to bank nor a miss to punish, and letting it count either way
     * would make the ramp disagree with what the learner just saw on screen.
     *
     * **The rung has no ceiling.** A ladder's named rungs are a CONTENT ceiling — each
     * drill clamps its own draw to the top rung it can fill — but the number keeps
     * counting past them, so someone who has climbed a ladder out has a rung to go on
     * beating rather than a wall to bank wins into. Which rungs are named, and what the
     * one above the last name asks, is each drill's own business.
     */
    fun step(
        level: Int,
        winsAtLevel: Int,
        correct: Boolean,
        clean: Boolean,
        winsRequired: Int,
    ): RungStep {
        val current = maxOf(1, level)
        val wins = maxOf(0, winsAtLevel)
        if (!correct) return RungStep(maxOf(1, current - 1), 0)
        if (!clean) return RungStep(current, wins)
        val earned = wins + 1
        return if (earned >= maxOf(1, winsRequired)) RungStep(current + 1, 0) else RungStep(current, earned)
    }

    /** Where the ramp leaves the run: the rung to ask at next, and the wins banked on it. */
    data class RungStep(val level: Int, val winsAtLevel: Int)
}

/**
 * Where the next question comes from — the other half of a rung, and the other thing every
 * drill does the same way. [DrillRamp] says which rung a run stands on; this says what that
 * rung has left to ask.
 *
 * Each drill keeps its own draw type (they cross to Swift, where a generic would arrive
 * opaque) and its own sampler; what they share, and what lived four times before, is the
 * climb itself.
 */
internal object DrillLadder {

    /** A drawn question and the rung it is booked at; a null task is a ladder answered out. */
    data class Rung<T>(val task: T?, val level: Int)

    /**
     * The first rung at or above [from] with something left to ask, drawn by [sample].
     *
     * A rung a run has answered out is climbed past rather than repeated ([DrillSolved]),
     * and where the whole ladder above is spent the task is null — which is what ends a run
     * on its summary. Past [top] the content stands still and the NUMBER goes on
     * ([DrillRamp.step]): a question found up there keeps the rung it was asked at, never
     * the clamped one, so a tail rung survives its own draw.
     */
    fun <T : Any> climb(from: Int, top: Int, sample: (Int) -> T?): Rung<T> {
        val standing = maxOf(1, from)
        val ceiling = maxOf(1, top)
        for (rung in minOf(standing, ceiling)..ceiling) {
            val task = sample(rung)
            if (task != null) return Rung(task, maxOf(standing, rung))
        }
        return Rung(null, standing)
    }
}
