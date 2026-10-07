package net.spross.kern.design

import kotlin.math.min

/** Which share of an area a stretch of its progress bar stands for; the platform picks its color. */
enum class AreaStretch { Settled, Growing, Queued }

/** One gradient stop of the area bar: [location] is a share of the filled part, 0 to 1. */
data class AreaBarStop(val stretch: AreaStretch, val location: Double)

/**
 * An area's progress bar: settled, then every other active card, then queued-but-unintroduced,
 * measured against [progressTotal] (`AreaStatistics.progressTotal`) so the untouched rest of an area
 * stays a bare track instead of a bar that always reads full.
 * One continuous capsule: each stretch holds its color up to a short fade into its neighbor.
 *
 * Counts arrive as Doubles so a platform easing between two counts can ask for every frame.
 */
class AreaBar(settled: Double, growing: Double, queued: Double, progressTotal: Int) {
    private val filled: Double = settled + growing + queued
    private val total: Double = maxOf(progressTotal, 1).toDouble()

    /** The share of the track the stretches fill; 0 leaves the track bare. */
    val fill: Double = if (filled > 0) min(filled / total, 1.0) else 0.0

    /** The gradient across the filled part: two stops per non-empty stretch, in stretch order. */
    val stops: List<AreaBarStop> = if (filled <= 0) emptyList() else {
        val halfBlend = BLEND * total / filled
        var start = 0.0
        listOf(AreaStretch.Settled to settled, AreaStretch.Growing to growing, AreaStretch.Queued to queued)
            .filter { it.second > 0 }
            .flatMap { (stretch, count) ->
                val from = start / filled
                val to = (start + count) / filled
                start += count
                val inset = min(halfBlend, (to - from) / 2)
                listOf(AreaBarStop(stretch, from + inset), AreaBarStop(stretch, to - inset))
            }
    }

    private companion object {
        /** Half the fade between two stretches, as a share of the whole track. */
        const val BLEND = 0.01
    }
}
