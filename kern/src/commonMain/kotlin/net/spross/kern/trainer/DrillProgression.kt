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
        DrillVariant.Phrases to mapOf(DrillVariant.Clock to 3),
        DrillVariant.Forms to mapOf(DrillVariant.Numbers to 7),
    )

    /**
     * Clock and Phrases are deliberately absent from the [DrillModifier.Mix] gate:
     * a pair's phrase ceiling depends on which frames the catalog happens to realize
     * for it, so a fixed rung there would be unreachable for some pairs.
     */
    private val modifierRequirements: Map<DrillModifier, Map<DrillVariant, Int>> = mapOf(
        DrillModifier.Reverse to emptyMap(),
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
