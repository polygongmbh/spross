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
    val desiredRetention: Double = 0.8,
    val maximumIntervalDays: Int = 365,
    /**
     * Days of stability at which a card counts as CONSOLIDATED — the ONE bar for
     * "has this word landed", read by every rule that asks the question:
     * the stats display (fresh/consolidated split, the session-summary tally),
     * phrase unlock (see [net.spross.kern.box.Growth.isComponentStable]), the drill
     * pools, [net.spross.kern.model.producePrompt] (which WITHDRAWS the meaning),
     * and [net.spross.kern.model.emojiCue] (which ADDS support).
     *
     * Set between S0(Good) = 2.3065 and S0(Easy) = 8.2956, so a merely-Good first
     * answer does not read as landed while a genuinely known-on-sight Easy one does.
     * That gap is the whole point: a first answer of Good is as easily an emoji
     * recognised as a word recalled, and the word keeps its support until a second
     * answer says otherwise — where Easy, which only a fast learner-reported Knew
     * can earn ([net.spross.kern.session.SelfGrading]), clears the bar on the spot.
     *
     * A separate, faster `settledStability` of 2.0 used to gate presentation support
     * on its own. It sat BELOW S0(Good), so a single Good — the emoji-lucky case
     * included — withdrew the emoji from the very next review, which is the first
     * TYPED one and the first that can actually catch the guess.
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
) {
    companion object {
        /**
         * The shipped calibration, handed out as a value: exactly the defaults above.
         * A factory because Kotlin default arguments do not cross the ObjC boundary —
         * without one, every platform that cannot see them restates the table and the
         * numbers drift apart quietly (see `docs/portability.md`).
         */
        fun product(): BoxConfig = BoxConfig()
    }
}

/** Per-day aggregates; every count is in cards (reviews = answer events). */
data class DayStats(
    val reviews: Int = 0,
    val introduced: Int = 0,
    /** Cards that crossed into CONSOLIDATED that day — the day's real gain. */
    val consolidated: Int = 0,
    val activeCount: Int = 0,
)
