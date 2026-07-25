package net.spross.kern.model

/**
 * Product box configuration — v1 calibration (one schedule per card, so every
 * count is denominated in CARDS).
 */
data class BoxConfig(
    /** Target size of the learning pool, in cards in Learning phase. */
    val maxLearning: Int = 8,
    /** Session size in cards. */
    val sessionCap: Int = 30,
    /** Backlog health threshold in cards. */
    val dueSoftCap: Int = 30,
    val desiredRetention: Double = 0.8,
    val maximumIntervalDays: Int = 365,
    /** Days of component stability required before a phrase unlocks (FSRS-6 recalibrated). */
    val phraseUnlockStability: Double = 2.0,
    /** Learning steps in seconds (FSRS-6 reference default). */
    val learningStepsSeconds: List<Long> = listOf(60L, 600L),
    /**
     * Relearning steps in seconds — FSRS-6 reference default; no in-session
     * lapse retry (breadth ruling 2026-07-22).
     */
    val relearningStepsSeconds: List<Long> = listOf(600L),
)

/** Per-day aggregates; every count is in cards (reviews = answer events). */
data class DayStats(
    val reviews: Int = 0,
    val introduced: Int = 0,
    val activeCount: Int = 0,
)
