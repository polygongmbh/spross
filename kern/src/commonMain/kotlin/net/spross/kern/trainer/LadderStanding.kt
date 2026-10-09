package net.spross.kern.trainer

/**
 * Where a laddered drill run stands, and the booking every such run shares:
 * [WordScrambleRun], [SentenceScrambleRun], [OppositesRun], [LetterDrillRun], [CountryDrillRun]
 * and [DateDrillRun] each read theirs out of their own concrete state and copy it back,
 * so the rule is written once while the run states stay concrete for Swift.
 *
 * [bestSprosse] is the Sprosse the run REACHED, not the one it ends on —
 * the ramp drops back on a miss, and the ladder rewards standing on a Sprosse rather than finishing there.
 * [clearedSprossen] are the Sprossen climbed off before the run's first slip ([DrillSprossen]),
 * for the store to add to the mask it holds — the Sprossen later runs pass on one clean answer
 * ([DrillSprossen.winsRequired]); unfiltered, since unlike [bestSprosse] there is no standing value to beat.
 * A run that clears by answering a Sprosse OUT ([DrillSolved.cleared]) reads its own and ignores this ledger.
 */
internal data class LadderStanding(
    val sprosse: Int,
    val bestSprosse: Int,
    val winsAtSprosse: Int,
    val clearedSprossen: Set<Int>,
    val core: DrillRunCore,
) {

    /**
     * One answer booked: the ramp ([DrillRamp.step]) and the counters ([DrillRunCore.book]) read the
     * same two flags, so neither can disagree about what the answer was.
     * [solves] is the key a clean answer retires ([DrillSolved]), null where the run has none;
     * [top] is the last Sprosse where the ladder stops rather than counting on ([DrillRamp.step]).
     */
    fun answered(
        answer: DrillBooking,
        winsRequired: Int,
        solves: String?,
        top: Int = Int.MAX_VALUE,
    ): LadderStanding {
        val step = DrillRamp.step(sprosse, winsAtSprosse, answer.correct, answer.clean, winsRequired, top)
        val booked = core.book(answer.correct, answer.clean, solves)
        return copy(
            sprosse = step.sprosse,
            bestSprosse = maxOf(bestSprosse, step.sprosse),
            winsAtSprosse = step.winsAtSprosse,
            clearedSprossen = DrillSprossen.leaving(clearedSprossen, sprosse, step.sprosse, booked.slipped),
            core = booked,
        )
    }

    /**
     * Moved to the Sprosse the next question was [drawn] at ([DrillLadder.climb]):
     * a Sprosse answered out is climbed past and books like one climbed off,
     * and the wins banked below it stay behind.
     */
    fun carriedTo(drawn: Int): LadderStanding = copy(
        sprosse = drawn,
        bestSprosse = maxOf(bestSprosse, drawn),
        winsAtSprosse = if (drawn == sprosse) winsAtSprosse else 0,
        clearedSprossen = DrillSprossen.leaving(clearedSprossen, sprosse, drawn, core.slipped),
    )

    companion object {

        /** What a booked answer and a close ask of the surface: the beat disarmed, nothing left speaking. */
        val EFFECTS: List<DrillEffect> = listOf(DrillEffect.CancelAdvance, DrillEffect.Silence)

        /**
         * Leaving the run: a pending accepted answer books exactly as the explicit tap would
         * ([TypedDrillVerdicts.pending]), so closing can neither lose it nor upgrade it;
         * a revealed answer nobody confirmed books nothing.
         */
        fun <S : DrillRunProgress> closing(state: S, advanced: (S, DrillBooking) -> S): S =
            TypedDrillVerdicts.pending(state.feedback)?.let { advanced(state, it) } ?: state

        /**
         * What the close reports: nothing for a run never answered, and a record only where the
         * drill keeps one ([standingRecord] non-null) and the run's best answer streak beat it — strictly.
         */
        fun summary(ended: DrillRunProgress, standingRecord: Int?): DrillRunSummary? =
            if (ended.done == 0) {
                null
            } else {
                DrillRunSummary(
                    ended.done,
                    ended.bestAnswerStreak,
                    standingRecord != null && ended.bestAnswerStreak > standingRecord,
                )
            }
    }
}
