package net.spross.kern.box

import net.spross.kern.model.CardScheduling

/** Active cards per stage; the combined counts are sums of these, never differences. */
data class StageCounts(
    val fresh: Int = 0,
    val growing: Int = 0,
    val lapsed: Int = 0,
    /** Settled and short of [MATURED_STABILITY]. */
    val settled: Int = 0,
    val matured: Int = 0,
) {
    val active: Int get() = fresh + growing + lapsed + settled + matured

    /** Everything short of the settled bar, lapsed words included. */
    val allGrowing: Int get() = fresh + growing + lapsed

    val allSettled: Int get() = settled + matured

    internal companion object {
        fun of(schedules: Iterable<CardScheduling>): StageCounts {
            val tally = StageTally()
            for (sched in schedules) tally.add(stageOf(sched), sched.memory?.stability ?: 0.0)
            return tally.counts()
        }
    }
}

/** The one place that sorts a stage into a [StageCounts] field — the box's counts and each tree's. */
internal class StageTally {
    private var fresh = 0
    private var growing = 0
    private var lapsed = 0
    private var settled = 0
    private var matured = 0

    /** Counts a card on [stage] at [stability]; false for a stage no count holds. */
    fun add(stage: GrowthStage, stability: Double): Boolean {
        when (stage) {
            GrowthStage.Fresh -> fresh += 1
            GrowthStage.Growing -> growing += 1
            GrowthStage.Lapsed -> lapsed += 1
            GrowthStage.Settled -> if (stability >= MATURED_STABILITY) matured += 1 else settled += 1
            GrowthStage.Unscheduled, GrowthStage.Queued, GrowthStage.Suspended -> return false
        }
        return true
    }

    fun counts() = StageCounts(fresh, growing, lapsed, settled, matured)
}
