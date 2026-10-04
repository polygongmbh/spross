package net.spross.kern.box

import net.spross.kern.model.CardScheduling

/** Active cards per Sprosse; the combined counts are sums of these, never differences. */
data class StageCounts(
    val fresh: Int = 0,
    val growing: Int = 0,
    val relearning: Int = 0,
    /** Settled and short of [MATURED_STABILITY]. */
    val settled: Int = 0,
    val matured: Int = 0,
) {
    val active: Int get() = fresh + growing + relearning + settled + matured

    /** Everything short of the settled bar, lapsed words included. */
    val allGrowing: Int get() = fresh + growing + relearning

    val allSettled: Int get() = settled + matured

    internal companion object {
        fun of(state: BoxState, schedules: Iterable<CardScheduling>): StageCounts {
            var fresh = 0
            var growing = 0
            var relearning = 0
            var settled = 0
            var matured = 0
            for (sched in schedules) {
                when (stageOf(state, sched)) {
                    GrowthStage.Fresh -> fresh += 1
                    GrowthStage.Growing -> growing += 1
                    GrowthStage.Relearning -> relearning += 1
                    GrowthStage.Settled ->
                        if ((sched.memory?.stability ?: 0.0) >= MATURED_STABILITY) matured += 1 else settled += 1
                    GrowthStage.Unscheduled, GrowthStage.Queued, GrowthStage.Suspended -> Unit
                }
            }
            return StageCounts(fresh, growing, relearning, settled, matured)
        }
    }
}
