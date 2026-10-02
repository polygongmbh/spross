package net.spross.kern.box

import kotlin.math.ln
import kotlin.math.min

/**
 * One area's standing, counted word by word — what the forest draws one tree from.
 *
 * The unit is the AREA: every word the learner has met stands in exactly one of the four
 * met tiers, so the tiers add up to the words the tree carries and nothing is counted twice.
 * Ranked most-grown first — [longHeld], then [matured], [growing], [arriving] — which is the
 * order [reaches] is in and the order [TreeTransition.changedRanks] counts in.
 */
data class AreaGrowth(
    val area: String,
    /** Met and still on its way in: [GrowthStage.Learning] or [GrowthStage.Fresh]. */
    val arriving: Int,
    /** [GrowthStage.Growing] — landed. */
    val growing: Int,
    /** [GrowthStage.Matured], short of [FRUIT_STABILITY]. */
    val matured: Int,
    /** [GrowthStage.Matured] at or past [FRUIT_STABILITY]. */
    val longHeld: Int,
    /** Packed and never met ([GrowthStage.Queued]) — why the area is growing at all. */
    val queued: Int,
    /** [GrowthStage.Relearning]: a word that slipped, never a smaller area. */
    val lapsed: Int,
    /** Something here was answered today. */
    val answeredToday: Boolean,
    /** How far each met word has come, 0…1, most-grown first — one entry per met word. */
    val reaches: List<Double>,
) {
    /** Every word the learner has met here and still holds. */
    val met: Int get() = longHeld + matured + growing + arriving

    /** Nothing has happened here: nothing met, nothing packed. */
    val isBare: Boolean get() = met + queued == 0

    /** Which tier the word at [rank] stands in, 1 (most grown) … 4, or 0 past [met]. */
    internal fun tierAt(rank: Int): Int = when {
        rank < longHeld -> 1
        rank < longHeld + matured -> 2
        rank < longHeld + matured + growing -> 3
        rank < met -> 4
        else -> 0
    }

    companion object {
        /** An area with nothing in it — the "before" of an area a round opened. */
        fun bare(area: String): AreaGrowth =
            AreaGrowth(area, 0, 0, 0, 0, 0, 0, false, emptyList())
    }
}

/**
 * One area before and after something happened to it — a finished round, most often.
 * The AFTER is what stands; the transition names which of its ranks the round moved.
 */
data class TreeTransition(val before: AreaGrowth, val after: AreaGrowth) {

    /**
     * [before] with no tier holding more than it ends with.
     *
     * A round can take a word out of a tier — a lapse, or a word maturing into the next
     * one — and played forward that reads as something taken away; a tier that shrank
     * simply starts where it ends, and what the round ADDED still counts.
     */
    private val start: AreaGrowth = AreaGrowth(
        area = before.area,
        arriving = min(before.arriving, after.arriving),
        growing = min(before.growing, after.growing),
        matured = min(before.matured, after.matured),
        longHeld = min(before.longHeld, after.longHeld),
        queued = min(before.queued, after.queued),
        lapsed = min(before.lapsed, after.lapsed),
        answeredToday = before.answeredToday,
        reaches = before.reaches,
    )

    /** How many ranks were already standing when the round began; from here on each is new. */
    val settledCount: Int get() = start.met

    /**
     * The ranks this round moved, in rank order: a word that arrived, and a rank whose
     * tier changed — a word maturing pushes one tier boundary out by one, and nothing else
     * shifts.
     */
    val changedRanks: List<Int>
        get() = (0 until after.met).filter { start.tierAt(it) != after.tierAt(it) }
}

/** How far one word has come, 0…1, on a log scale — stability grows multiplicatively. */
fun CardGrowth.reach(maximumIntervalDays: Int): Double {
    if (stability <= 1.0 || maximumIntervalDays <= 1) return 0.0
    return min(1.0, ln(stability) / ln(maximumIntervalDays.toDouble()))
}

/**
 * Every area the box holds, tallied from [growth] ([BoxEngine.growth]) — the one place
 * that decides which [GrowthStage] lands in which tier. Areas with no joined card are absent.
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
    var arriving = 0
    var growing = 0
    var matured = 0
    var longHeld = 0
    var queued = 0
    var lapsed = 0
    var answeredToday = false
    val reaches = mutableListOf<Double>()

    fun add(entry: CardGrowth, maximumIntervalDays: Int) {
        if (entry.touchedToday) answeredToday = true
        val reach = entry.reach(maximumIntervalDays)
        when (entry.stage) {
            GrowthStage.Unscheduled, GrowthStage.Suspended -> return
            GrowthStage.Queued -> { queued += 1; return }
            GrowthStage.Relearning -> { lapsed += 1; return }
            GrowthStage.Learning, GrowthStage.Fresh -> arriving += 1
            GrowthStage.Growing -> growing += 1
            GrowthStage.Matured ->
                if (entry.stability >= FRUIT_STABILITY) longHeld += 1 else matured += 1
        }
        reaches += reach
    }

    // why: most-grown first — the tiers ARE stability bands, so sorting by reach
    // reproduces them and entry n belongs to rank n.
    fun tree(area: String) = AreaGrowth(
        area, arriving, growing, matured, longHeld, queued, lapsed, answeredToday,
        reaches.sortedDescending(),
    )
}
