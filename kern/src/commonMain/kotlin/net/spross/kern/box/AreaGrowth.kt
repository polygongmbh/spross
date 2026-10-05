package net.spross.kern.box

import kotlin.math.ln
import kotlin.math.min

/**
 * One area's standing, counted word by word — what the forest draws one tree from.
 *
 * The unit is the AREA: every word the learner has met stands in exactly one of the four
 * met stages, so the stages add up to the words the tree carries and nothing is counted twice.
 * Ranked most-grown first — matured, then settled, growing, fresh — which is the
 * order [strengths] is in and the order [TreeTransition.changedRanks] counts in.
 */
data class AreaGrowth(
    val area: String,
    /** The area's active words per stage, sorted the way [AreaStatistics] sorts them. */
    val stages: StageCounts,
    /** Queued and never met ([GrowthStage.Queued]) — why the area is growing at all. */
    val queued: Int,
    /** Something here was answered today. */
    val answeredToday: Boolean,
    /** How far each met word has come, 0…1, most-grown first — one entry per met word. */
    val strengths: List<Double>,
) {
    /** Every word on the tree: the active words short of the lapsed ones, which hang nowhere. */
    val met: Int get() = stages.active - stages.lapsed

    /** Nothing has happened here: nothing met, nothing queued. */
    val isBare: Boolean get() = met + queued == 0

    /**
     * Which stage the word at [rank] stands in, numbered most grown first —
     * 1 matured, 2 settled, 3 growing, 4 fresh — or 0 past [met].
     */
    internal fun stageAt(rank: Int): Int = when {
        rank < stages.matured -> 1
        rank < stages.allSettled -> 2
        rank < stages.allSettled + stages.growing -> 3
        rank < met -> 4
        else -> 0
    }

    companion object {
        /** An area with nothing in it — the "before" of an area a round opened. */
        fun bare(area: String): AreaGrowth = AreaGrowth(area, StageCounts(), 0, false, emptyList())
    }
}

/**
 * One area before and after something happened to it — a finished round, most often.
 * The AFTER is what stands; the transition names which of its ranks the round moved.
 */
data class TreeTransition(val before: AreaGrowth, val after: AreaGrowth) {

    /**
     * [before] with no stage holding more than it ends with.
     *
     * A round can take a word out of a stage — a lapse, or a word maturing into the next
     * one — and played forward that reads as something taken away; a stage that shrank
     * simply starts where it ends, and what the round ADDED still counts.
     */
    private val start: AreaGrowth = AreaGrowth(
        area = before.area,
        stages = StageCounts(
            fresh = min(before.stages.fresh, after.stages.fresh),
            growing = min(before.stages.growing, after.stages.growing),
            lapsed = min(before.stages.lapsed, after.stages.lapsed),
            settled = min(before.stages.settled, after.stages.settled),
            matured = min(before.stages.matured, after.stages.matured),
        ),
        queued = min(before.queued, after.queued),
        answeredToday = before.answeredToday,
        strengths = before.strengths,
    )

    /** How many ranks were already standing when the round began; from here on each is new. */
    val standingCount: Int get() = start.met

    /**
     * The ranks this round moved, in rank order: a word that arrived, and a rank whose
     * stage changed — a word maturing pushes one stage boundary out by one, and nothing else
     * shifts.
     */
    val changedRanks: List<Int>
        get() = (0 until after.met).filter { start.stageAt(it) != after.stageAt(it) }
}

/** How far one word has come, 0…1, on a log scale — stability grows multiplicatively. */
fun CardGrowth.strength(maximumIntervalDays: Int): Double {
    if (stability <= 1.0 || maximumIntervalDays <= 1) return 0.0
    return min(1.0, ln(stability) / ln(maximumIntervalDays.toDouble()))
}

/**
 * Every area the box holds, tallied from [growth] ([BoxEngine.growth]) by the same [StageTally]
 * the box's own counts use. Areas with no joined card are absent.
 */
fun growthByArea(state: BoxState, growth: List<CardGrowth>): Map<String, AreaGrowth> {
    val tallies = linkedMapOf<String, AreaTally>()
    for (entry in growth) {
        val area = state.cards[entry.cardId]?.area ?: continue
        tallies.getOrPut(area) { AreaTally() }.add(entry, state.config.maximumIntervalDays)
    }
    return tallies.mapValues { (area, tally) -> tally.tree(area) }
}

/**
 * The area a round worked hardest, as [before] (the box the round opened on) and [after]
 * hold it — null when the round touched nothing joinable. Ties go to the area first in
 * [areaOrder], so a round split evenly names the same one every time; an area the round
 * opened stands on [AreaGrowth.bare] before. Walks that one area's cards, nothing else.
 */
fun grownArea(
    before: BoxState,
    after: BoxState,
    answeredIds: List<String>,
    areaOrder: List<String>,
    nowEpochMillis: Long,
    tzId: String,
): TreeTransition? {
    val counts = answeredIds.mapNotNull { after.cards[it]?.area }.groupingBy { it }.eachCount()
    var best: String? = null
    for (area in areaOrder) {
        if ((counts[area] ?: 0) > (best?.let { counts[it] } ?: 0)) best = area
    }
    val area = best ?: return null
    fun tree(state: BoxState) = growthByArea(state, areaGrowth(state, area, nowEpochMillis, tzId))[area]
    val now = tree(after) ?: return null
    return TreeTransition(tree(before) ?: AreaGrowth.bare(area), now)
}

private class AreaTally {
    val stages = StageTally()
    var queued = 0
    var answeredToday = false
    val strengths = mutableListOf<Double>()

    fun add(entry: CardGrowth, maximumIntervalDays: Int) {
        if (entry.touchedToday) answeredToday = true
        if (entry.stage == GrowthStage.Queued) queued += 1
        if (stages.add(entry.stage, entry.stability) && entry.stage != GrowthStage.Lapsed) {
            strengths += entry.strength(maximumIntervalDays)
        }
    }

    // why: most-grown first — the stages ARE stability bands, so sorting by strength
    // reproduces them and entry n belongs to rank n.
    fun tree(area: String) = AreaGrowth(area, stages.counts(), queued, answeredToday, strengths.sortedDescending())
}
