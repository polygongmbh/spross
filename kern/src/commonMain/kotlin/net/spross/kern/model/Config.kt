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
     * recognized as a word recalled, and the word keeps its support until a second
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
     * (Re)learning steps in seconds — ONE growing-backoff ladder, the same cadence
     * whether a word has never graduated (Learning) or lapsed after it did
     * (Relearning): 10 min, 1 day, 3 days, 7 days (user ruling 2026-09-01,
     * supersedes both the single-step-Learning split and the 2026-08-07 leech
     * ruling — a lapse no longer auto-suspends). A retry belongs to the NEXT
     * sitting or an endless run, not the tail of this one — a composed session
     * never refills (no in-session retry, breadth ruling 2026-07-22), so the run
     * boundary keeps a lapsed word out of the sitting it lapsed in regardless of
     * the step length. Repeated fails climb the ladder instead of repeating its
     * first entry (see [net.spross.kern.fsrs.FsrsScheduler]), giving a word that
     * keeps slipping room to consolidate rather than being shoved at the learner
     * again the same day; every rating but `Again` graduates it immediately from
     * wherever the ladder sits.
     */
    val stepsSeconds: List<Long> = listOf(600L, 86_400L, 3 * 86_400L, 7 * 86_400L),
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
