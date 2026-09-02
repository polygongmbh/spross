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
    val sessionCap: Int = 24,
    /**
     * Recall probability a graduated interval aims at — the schedule solves for `R = this`.
     *
     * What the number decides is not really the target but the FIRST interval it implies,
     * and a retrieval pays only where it can succeed. At 0.8 a word answered Good waited
     * 7.6 days and one answered Tough 4.3, with the second sighting at day 60 — longer
     * than a pair met once survives. At 0.85 those are 4.4 and 2.5 days, and the early
     * schedule reads 4.4 · 28 · 127 rather than 7.6 · 60 · 335.
     *
     * Close to free: a card still draws four reviews in its first year and about one more
     * by its second, because review count grows with the LOG of the interval rather than
     * its reciprocal. 0.9 is where it stops being free — six a year, which fills
     * [sessionCap] on its own (`docs/growth-evidence.md`).
     */
    val desiredRetention: Double = 0.85,
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
     * (Re)learning steps in seconds — ONE ladder, the same cadence whether a word has
     * never graduated (Learning) or lapsed after it did (Relearning). Minutes and
     * day-scale waits ALTERNATE — 10 min, 1 day, 10 min, 3 days, 10 min, 7 days,
     * 10 min, 30 days (user ruling 2026-09-02, supersedes the purely growing ladder of
     * 2026-09-01) — so a word that will not stick comes back at most TWICE in a day
     * while the gaps between those pairs still widen.
     *
     * The short rung is the load-bearing one, and it is why the ladder is not simply
     * growing. A retrieval pays only where it can SUCCEED: spacing beats massing by a
     * wide margin, but past that the schedule's SHAPE barely registers next to the
     * first interval being short enough to land, and a failed attempt with the answer
     * shown is worth about a restudy on a pair as arbitrary as a translation
     * (`docs/growth-evidence.md`). Pushing a word further out the moment it fails
     * spends its next look exactly where that look is worth least.
     *
     * A retry belongs to the NEXT sitting or an endless run, not the tail of this one —
     * a composed session never refills (no in-session retry, breadth ruling 2026-07-22),
     * so the run boundary keeps a lapsed word out of the sitting it lapsed in whatever
     * the step says. Repeated fails climb instead of repeating the first entry (see
     * [net.spross.kern.fsrs.FsrsScheduler]) and stop at the last rung, which is a MONTH:
     * a word still missed after four same-day pairs has earned no further repetition —
     * what the evidence supports there is rewriting the word or letting it go, and
     * nothing supports drilling it — but the box does not suspend on its own, so the
     * last rung parks it within reach instead of dropping it. Every rating but `Again`
     * graduates it immediately from wherever the ladder sits.
     */
    val stepsSeconds: List<Long> = listOf(
        600L, 86_400L, 600L, 3 * 86_400L, 600L, 7 * 86_400L, 600L, 30 * 86_400L,
    ),
) {
    /**
     * Due cards left over from a round go unsaid below this
     * ([net.spross.kern.session.SessionOffer.dueHeldBack]).
     *
     * Intake sits near what a sitting can service, so a box in good health almost always has a
     * few cards over (`docs/growth-evidence.md`). Naming three of them turns that standing,
     * healthy state into an arrears notice the learner reads every single day. Half the sitting
     * rather than a number, so the line means the same thing at every [sessionCap]: a remainder
     * is worth saying once it approaches another sitting's worth of work.
     */
    val heldBackNamedFrom: Int = sessionCap / 2

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
