package net.spross.kern.model

/**
 * Product box configuration — v1 calibration (one schedule per card, so every
 * count is denominated in CARDS).
 */
data class BoxConfig(
    /**
     * Session size in cards — the one bound the evidence actually supports
     * (output interference falls on how many cards a sitting TESTS, not on how
     * many words enter; see `docs/growth-evidence.md`).
     */
    val sessionCap: Int = 25,
    /** Backlog health threshold in cards. */
    val dueSoftCap: Int = 30,
    val desiredRetention: Double = 0.8,
    val maximumIntervalDays: Int = 365,
    /**
     * Days of stability at which a card has SETTLED — the threshold behind the
     * presentation rules that support a word only while it is still landing
     * (see [net.spross.kern.box.Statistics.isSettled]). FSRS-6 recalibrated.
     * The stricter [consolidatedStability] governs the stats display and
     * phrase unlock instead.
     */
    val settledStability: Double = 2.0,
    /**
     * Days of stability at which a card counts as CONSOLIDATED — a stricter bar than
     * [settledStability], used only by the stats display (fresh/settled split, the
     * session-summary tally) and phrase unlock (see [net.spross.kern.box.Growth.isComponentStable]).
     * In-session presentation support keeps using [settledStability];
     * this one exists so a merely-Good first answer doesn't read as "landed" while a
     * genuinely known-on-sight Easy answer still does — set between S0(Good) = 2.3065
     * and S0(Easy) = 8.2956.
     */
    val consolidatedStability: Double = 6.0,
    /**
     * ONE learning step, deliberately longer than the sitting that earned it: the
     * retry belongs to the NEXT sitting or an endless run, not to the tail of this
     * one, where it would push the finish line back after the learner had already
     * counted the cards left. Two minutes clears a short sitting without pushing
     * the word out of the day (reference default was `[1m, 10m]`).
     */
    val learningStepsSeconds: List<Long> = listOf(120L),
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
    /** Cards that crossed into settled that day — the day's real gain. */
    val settled: Int = 0,
    val activeCount: Int = 0,
)
