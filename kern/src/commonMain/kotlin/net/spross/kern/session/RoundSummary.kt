package net.spross.kern.session

import net.spross.kern.box.BoxEngine
import net.spross.kern.box.GrowthHeadline
import net.spross.kern.box.TallyPart
import net.spross.kern.box.TreeTransition
import net.spross.kern.box.grownArea
import net.spross.kern.box.growthHeadline

/**
 * Everything a finished round's summary says, taken together.
 * The words for each part, the tree's drawing and the area's name are the platform's.
 */
data class RoundSummary(
    /** The round's answers spelled out ([RoundTally.parts]); empty when it named nothing. */
    val parts: List<TallyPart>,
    /** The area the round worked hardest, as the round found it and as it left it ([grownArea]). */
    val grownArea: TreeTransition?,
    /** What the summary may claim over that area ([growthHeadline]). */
    val headline: GrowthHeadline?,
    /**
     * Today's recall is far enough under what the schedule expects that more reps buy little
     * ([net.spross.kern.box.TodayReport.recallStrained]) — a round that only celebrated
     * would be contradicted by the next one.
     */
    val restSuggested: Boolean,
) {
    /**
     * The tree that takes the hero slot, under the growth claim; null where there is none to
     * speak of, and the summary stands on its plain title and the popper instead.
     */
    val shownTree: TreeTransition? get() = grownArea?.takeIf { headline != null }

    companion object {
        /**
         * The summary of [run] against the box it opened on ([SessionRunState.startBox]).
         * [streakDays] is the run across every language, which only the caller holds;
         * it seeds the headline's pick.
         */
        fun of(
            run: SessionRunState,
            areaOrder: List<String>,
            streakDays: Int,
            nowEpochMillis: Long,
            tzId: String,
        ): RoundSummary = withArea(
            run,
            grownArea(run.startBox ?: run.box, run.box, run.tally.cardIds, areaOrder, nowEpochMillis, tzId),
            streakDays, nowEpochMillis, tzId,
        )

        /** [of] over a tree the caller supplies — a debug launch's sample area in place of the round's own. */
        fun withArea(
            run: SessionRunState,
            grownArea: TreeTransition?,
            streakDays: Int,
            nowEpochMillis: Long,
            tzId: String,
        ): RoundSummary {
            val rest = BoxEngine.today(run.box, nowEpochMillis, tzId).recallStrained
            val tally = run.tally
            return RoundSummary(
                parts = tally.parts(),
                grownArea = grownArea,
                headline = growthHeadline(grownArea, rest, tally.introduced, tally.settled, tally.reviewed, streakDays),
                restSuggested = rest,
            )
        }
    }
}
