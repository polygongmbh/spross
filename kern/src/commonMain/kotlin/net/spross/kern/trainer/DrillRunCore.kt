package net.spross.kern.trainer

import net.spross.kern.session.AnswerOutcome

/**
 * The figures every drill run keeps, whatever it happens to ask: the streak on screen and the
 * best it reached, the misses in a row, the outcomes the tally reads, how many questions are
 * done, the prompts already answered right, whether the run has slipped yet, and where the run
 * stands against its next pause.
 *
 * A run's own business is the questions it draws and the ladder it climbs where that ladder is
 * its own; the typed runs share theirs down to the verdicts ([TypedDrillVerdicts]). These
 * move the same way in every drill, so an answer is booked here once ([book]); four copies of
 * one piece of arithmetic is how two streaks come to disagree.
 *
 * Each run state embeds one and forwards the fields it shows, so nothing outside kern has to
 * know the counters moved house.
 */
data class DrillRunCore(
    val done: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /**
     * Misses in a row already BOOKED — the one on screen is not among them, so 1 while a miss
     * shows means this is the second in a row.
     */
    val missRun: Int = 0,
    val outcomes: List<AnswerOutcome> = emptyList(),
    /**
     * The prompts this run has already answered RIGHT ([DrillSolved]): never asked again, and
     * a Sprosse with nothing left outside them is climbed past rather than repeated.
     */
    val solved: Set<String> = emptySet(),
    /**
     * Whether the run has missed or almost-answered anywhere yet: from then on it clears no
     * Sprosse ([DrillSprossen]).
     */
    val slipped: Boolean = false,
    /**
     * [solved] as it stood before the run's first slip — what an enumerated ladder may call
     * answered out for the store ([DrillSolved.cleared]).
     */
    val solvedClean: Set<String> = emptySet(),
    /** Where the run stands against its next natural stop ([DrillPacing]). */
    val pacing: DrillPacing = DrillPacing(),
) {

    /**
     * One answer, booked. [correct] and [clean] are the same two the ramp reads
     * ([DrillRamp.step]), so the counters and the Sprosse can never disagree about what an
     * answer was: a miss cuts the streak and lengthens the miss run, an almost holds both.
     *
     * [solves] is the key the question would retire, null where the run has none to name it
     * by. Only a CLEAN correct answer retires it — a slip, a look-up and a reveal all leave
     * the prompt in the pool, which is what the ramp already says an almost is worth.
     */
    fun book(correct: Boolean, clean: Boolean, solves: String?): DrillRunCore {
        val run = if (correct) streak + 1 else 0
        val solvedNow = if (correct && clean && solves != null) solved + solves else solved
        val slips = DrillSprossen.slipped(slipped, correct, clean)
        return copy(
            done = done + 1,
            streak = run,
            bestStreak = maxOf(bestStreak, run),
            missRun = if (correct) 0 else missRun + 1,
            outcomes = outcomes + outcome(correct, clean),
            solved = solvedNow,
            slipped = slips,
            solvedClean = if (slips) solvedClean else solvedNow,
        )
    }

    /**
     * After [book] and the draw that follows it: the pacing brought up to date, and the pause
     * due now, if one is ([DrillPacing]).
     */
    internal fun paced(level: Int?, newSprossen: Int, endless: Boolean): DrillRunCore =
        copy(pacing = pacing.after(this, level, newSprossen, endless))

    /** Going on from a pause: the same run, and a fresh stretch from here. */
    internal fun resumed(): DrillRunCore =
        copy(pacing = pacing.copy(stretchFrom = done, gainedBefore = pacing.gained, pause = null))

    private fun outcome(correct: Boolean, clean: Boolean): AnswerOutcome = when {
        !correct -> AnswerOutcome.Wrong
        clean -> AnswerOutcome.Right
        else -> AnswerOutcome.Almost
    }
}
