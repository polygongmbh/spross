package net.spross.kern.model

/**
 * Product box configuration. Denomination:
 * [maxLearning] counts CONCEPTS; [sessionCap]/[dueSoftCap] count UNITS (answer events).
 */
data class BoxConfig(
    /** Target size of the learning pool, in concepts with any unit in learning. */
    val maxLearning: Int = 8,
    /** Session size in units. */
    val sessionCap: Int = 30,
    /** Backlog health threshold in units (≈ v1's 30-card backlog in concept terms). */
    val dueSoftCap: Int = 60,
    val desiredRetention: Double = 0.8,
    val maximumIntervalDays: Int = 365,
    /** Days of component stability required before a phrase unlocks (FSRS-6 recalibrated). */
    val phraseUnlockStability: Double = 2.0,
    /** Learning steps in seconds. */
    val learningStepsSeconds: List<Long> = listOf(60L, 600L),
    /** Relearning steps in seconds — [60] preserves v1's in-session retry. */
    val relearningStepsSeconds: List<Long> = listOf(60L),
)

/**
 * Per-day aggregates. reviews = answer events (units); introduced = concepts
 * (produce introductions); activeCount = concepts.
 */
data class DayStats(
    val reviews: Int = 0,
    val introduced: Int = 0,
    val activeCount: Int = 0,
)
