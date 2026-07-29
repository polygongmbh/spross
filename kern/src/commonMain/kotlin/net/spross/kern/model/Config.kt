package net.spross.kern.model

/**
 * Product box configuration — v1 calibration (one schedule per card, so every
 * count is denominated in CARDS).
 */
data class BoxConfig(
    /**
     * How many words may be in flight at once, in cards that have not sat down
     * (see [net.spross.kern.box.Growth.newBudget]). Words answered on sight
     * settle immediately and never count against it.
     */
    val maxUnsettled: Int = 20,
    /** Session size in cards. */
    val sessionCap: Int = 30,
    /** Backlog health threshold in cards. */
    val dueSoftCap: Int = 30,
    val desiredRetention: Double = 0.8,
    val maximumIntervalDays: Int = 365,
    /**
     * Days of stability at which a card has SAT DOWN — the single threshold
     * behind every "has this word landed yet" question: the phrase-unlock gate,
     * the sitting/fresh split in the progress UI, and the presentation rules
     * that support a word only while it is still landing (see
     * [net.spross.kern.box.Statistics.isSitting]). FSRS-6 recalibrated.
     */
    val sittingStability: Double = 2.0,
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
