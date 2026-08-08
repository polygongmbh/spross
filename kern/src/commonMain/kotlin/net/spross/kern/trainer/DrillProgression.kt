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
     * Clock and Phrases are deliberately absent from the [DrillModifier.Mix] gate:
     * a pair's phrase ceiling depends on which frames the catalog happens to realize
     * for it, so a fixed rung there would be unreachable for some pairs.
     */
    private val modifierRequirements: Map<DrillModifier, Map<DrillVariant, Int>> = mapOf(
        // why: reading a number back is the other half of writing one, so it is the
        // first thing the ladder hands out — but not on the opening task, where a
        // learner who has produced nothing yet would be asked to recognize it.
        DrillModifier.Reverse to mapOf(DrillVariant.Numbers to 3),
        DrillModifier.Fast to mapOf(DrillVariant.Numbers to 10),
        DrillModifier.Mix to mapOf(DrillVariant.Numbers to 10, DrillVariant.Forms to 5),
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
     * An amber answer ([clean] false: a typo, a revealed hint, a synonym) moves NOTHING.
     * It is neither a win to bank nor a miss to punish, and letting it count either way
     * would make the ramp disagree with what the learner just saw on screen.
     */
    fun step(
        level: Int,
        winsAtLevel: Int,
        correct: Boolean,
        clean: Boolean,
        maxLevel: Int,
        winsRequired: Int,
    ): RungStep {
        val ceiling = maxOf(1, maxLevel)
        val current = level.coerceIn(1, ceiling)
        val wins = maxOf(0, winsAtLevel)
        if (!correct) return RungStep(maxOf(1, current - 1), 0)
        if (!clean) return RungStep(current, wins)
        val earned = wins + 1
        return if (earned >= maxOf(1, winsRequired) && current < ceiling) {
            RungStep(current + 1, 0)
        } else {
            RungStep(current, earned)
        }
    }

    /** Where the ramp leaves the run: the rung to ask at next, and the wins banked on it. */
    data class RungStep(val level: Int, val winsAtLevel: Int)
}
