package net.spross.kern.design

import kotlin.math.max
import net.spross.kern.box.AreaGrowth

/**
 * One tree placed among the others: the [index] of its area in the list laid out,
 * where its trunk meets the ground ([footX], [baseline]), how tall it stands,
 * and the cell its label and tap target fill.
 */
data class TreeSpot(
    val index: Int,
    val footX: Double,
    val baseline: Double,
    val height: Double,
    val cellX: Double,
    val cellY: Double,
    val cellWidth: Double,
    val cellHeight: Double,
)

/** The trees placed, back to front, and how tall they stand all together. */
data class TreesPlan(val spots: List<TreeSpot>, val height: Double)

/**
 * Where each tree of a box stands: one per area, in rows across a width, in points (dp).
 *
 * Rows, not a grid: every tree in a row stands on ONE baseline, so two areas compare at a glance.
 * A tree takes the width of its own crown, a row takes trees while they fit,
 * and the spare width is shared out evenly between them.
 * Rows alternate: one touches the left side and leaves [ROW_SHIFT] free on the right,
 * the next the other way round, and rows stand half a row apart —
 * a checkerboard, so a tree grows up through the gap between two of the row above
 * and the trees read as one growing mass rather than as drawers in a wall.
 */
object TreesLayout {
    /** What a crown spans, in tree heights, and the narrowest a tree ever stands: a sapling's label and tap target need room a stem does not. */
    const val CROWN_SPAN = 1.4
    const val MIN_TREE_WIDTH = 26.0
    /** The closest two crowns in a row stand to each other, and the strip every row leaves free on one side. */
    const val MIN_GAP = 6.0
    const val ROW_SHIFT = 32.0
    const val ROW_HEIGHT = 72.0
    /** The strip under the baseline that carries a tree's label. */
    const val LABEL_HEIGHT = 18.0
    /** An unopened area's label fades with its seedling. */
    const val UNOPENED_LABEL_OPACITY = 0.4
    const val ROW_GAP = 8.0
    /** The shortest a tap target is ever made, label strip included: a seedling is a few points of ink and a thumb is not. */
    const val MIN_TAP_HEIGHT = 44.0
    /** The clear air a tap target keeps above the crown it belongs to. */
    const val TAP_MARGIN = 6.0

    fun place(trees: List<AreaGrowth>, width: Double): TreesPlan {
        if (width <= 0 || trees.isEmpty()) return TreesPlan(emptyList(), 0.0)
        val heights = trees.map(AreaTree::height)
        val span = width - ROW_SHIFT
        val widths = heights.map { max(MIN_TREE_WIDTH, it * CROWN_SPAN) }
        val rows = mutableListOf(mutableListOf<Int>())
        var used = 0.0
        for (index in trees.indices) {
            val add = widths[index] + if (rows.last().isEmpty()) 0.0 else MIN_GAP
            if (rows.last().isNotEmpty() && used + add > span) {
                rows += mutableListOf<Int>()
                used = 0.0
            }
            used += widths[index] + if (rows.last().isEmpty()) 0.0 else MIN_GAP
            rows.last() += index
        }

        val spots = mutableListOf<TreeSpot>()
        var base = 0.0
        for ((rank, row) in rows.withIndex()) {
            val band = max(ROW_HEIGHT, row.maxOf { heights[it] } + 10)
            // why: the last row stays as packed as it is.
            val spare = span - row.sumOf { widths[it] }
            val gap = if (rank == rows.size - 1 || row.size < 2) MIN_GAP else spare / (row.size - 1)
            var left = if (rank % 2 == 0) 0.0 else ROW_SHIFT
            val stand = base + band
            for (index in row) {
                val x = left + widths[index] / 2
                left += widths[index] + gap
                // why: the cell follows THIS tree's own crown, never the row's band,
                // so a seedling's tap target does not reach into the row above.
                val reach = max(max(heights[index], AreaTree.MIN_HEIGHT) + TAP_MARGIN, MIN_TAP_HEIGHT - LABEL_HEIGHT)
                spots += TreeSpot(index, x, stand, heights[index],
                    x - widths[index] / 2, stand - reach, widths[index], reach + LABEL_HEIGHT)
            }
            // why: rows stand HALF a row apart, so a row's trees grow up through the gaps of the rows around them.
            base += (band + LABEL_HEIGHT + ROW_GAP) / 2
        }
        // why: once rows interleave, the tree in front may belong to another row —
        // only one order across all of them layers them right.
        return TreesPlan(spots.sortedBy { it.baseline }, spots.maxOf { it.cellY + it.cellHeight })
    }
}
