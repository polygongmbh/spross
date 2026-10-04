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
     * (Re)learning steps in seconds — ONE ladder, the same cadence whether a word has
     * never graduated (Learning) or lapsed after it did (Relearning). Minutes and
     * day-scale waits ALTERNATE — 10 min, 1 day, 10 min, 3 days, 10 min, 7 days,
     * 10 min, 30 days (user ruling 2026-09-02, supersedes the purely growing ladder of
     * 2026-09-01) — so a word that will not stick comes back at most TWICE in a day
     * while the gaps between those pairs still widen.
     *
     * The short step is the load-bearing one, and it is why the ladder is not simply
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
     * [net.spross.kern.fsrs.FsrsScheduler]) and stop at the last step, which is a MONTH:
     * a word still missed after four same-day pairs has earned no further repetition —
     * what the evidence supports there is rewriting the word or letting it go, and
     * nothing supports drilling it — but the box does not suspend on its own, so the
     * last step parks it within reach instead of dropping it. Every rating but `Again`
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
         * numbers drift apart quietly.
         */
        fun product(): BoxConfig = BoxConfig()
    }
}
