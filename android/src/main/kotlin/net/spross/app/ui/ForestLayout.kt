package net.spross.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import net.spross.kern.box.AreaTree

/** One tree placed in the forest, and the cell its label and tap target fill. */
internal class ForestSpot(val planted: PlantedTree, val cell: Rect)

/**
 * Where each tree stands and how tall — lengths in dp unless a function takes a density,
 * which then works in pixels.
 *
 * Height comes from what has grown ([AreaTree.mass]), never from how many words the
 * catalog holds; the crown's shape comes from the words met ([TreeSkeleton.grow]).
 */
internal object ForestLayout {
    const val MIN_HEIGHT = 9f
    private const val MAX_HEIGHT = 42f
    /** What a crown spans, in tree heights, and the narrowest a tree ever stands: a sapling's label and tap target need room a stem does not. */
    private const val CROWN_SPAN = 1.4f
    private const val MIN_TREE_WIDTH = 32f
    /** The closest two crowns in a row stand to each other, and the half column every second row gives up at its start. */
    private const val MIN_GAP = 6f
    private const val ROW_SHIFT = 32f
    private const val ROW_HEIGHT = 72f
    const val LABEL_HEIGHT = 18f
    private const val ROW_GAP = 8f
    private const val MIN_TAP = 44f
    private const val TAP_MARGIN = 6f

    /** The mass at which a tree reaches full height — a large area, thoroughly learned. */
    private const val FULL_MASS = 24.0

    /** The summary's tree: a seedling's box and a grown tree's, before a screen lifts them. */
    private const val HERO_MIN = 78f
    private const val HERO_MAX = 190f

    /**
     * How far along the area stands, 0…1. Square-rooted because mass is a sum over words:
     * otherwise the first area worked dwarfs every other for months.
     */
    fun standing(tree: AreaTree): Float =
        if (tree.isBare) 0f else min(1.0, sqrt(tree.mass / FULL_MASS)).toFloat()

    fun treeHeight(tree: AreaTree): Float =
        if (tree.isBare) 0f else MIN_HEIGHT + (MAX_HEIGHT - MIN_HEIGHT) * standing(tree)

    /**
     * The summary's box for [tree]. [ceiling] lifts a grown tree's box on a screen with
     * room to give, and a seedling's floor rises in proportion, so standing still reads
     * as height.
     */
    fun heroHeight(tree: AreaTree, ceiling: Float): Float {
        val top = max(ceiling, HERO_MAX)
        val floor = top * HERO_MIN / HERO_MAX
        return floor + (top - floor) * standing(tree)
    }

    /** One tree alone in a box of its own, its ground a little clear of the bottom edge. */
    fun solitary(tree: AreaTree, width: Float, height: Float, density: Float): PlantedTree {
        val baseline = height - 7f * density
        return PlantedTree(tree, Offset(width / 2, baseline), min(baseline, width * 0.8f), density)
    }

    /**
     * The trees in rows across [width] px, in the order given, drawn back to front.
     *
     * Every tree in a row stands on one baseline, so two areas compare at a glance. Rows
     * stand half a row apart, so a tree grows up through the gap between two of the row above:
     * one mass, not a shelf of drawers. A tree takes the width of its own crown, a row takes
     * trees while they fit, and the spare width is shared out evenly between them. Rows
     * alternate: one touches the left side and leaves half a column free on the right, the
     * next the other way round.
     */
    fun plant(trees: List<AreaTree>, width: Float, density: Float): List<ForestSpot> {
        if (width <= 0f || trees.isEmpty()) return emptyList()
        val span = width - ROW_SHIFT * density
        val widths = trees.map { max(MIN_TREE_WIDTH, treeHeight(it) * CROWN_SPAN) * density }
        val rows = mutableListOf(mutableListOf<Int>())
        var used = 0f
        for (index in trees.indices) {
            val add = widths[index] + if (rows.last().isEmpty()) 0f else MIN_GAP * density
            if (rows.last().isNotEmpty() && used + add > span) {
                rows += mutableListOf<Int>()
                used = 0f
            }
            used += widths[index] + if (rows.last().isEmpty()) 0f else MIN_GAP * density
            rows.last() += index
        }
        val cells = mutableListOf<Pair<AreaTree, Rect>>()
        var stand = 0f
        var band = 0f
        for ((rank, row) in rows.withIndex()) {
            val tallest = row.maxOf { treeHeight(trees[it]) }
            val next = max(ROW_HEIGHT, tallest + 10f) * density
            // why: half a row on from the row behind, but never so little that this row's
            // tallest crown reaches up over the labels standing under that row.
            stand = if (rank == 0) next else stand + max(
                (band + (LABEL_HEIGHT + ROW_GAP) * density) / 2 + next - band,
                (tallest + LABEL_HEIGHT + 10f) * density,
            )
            band = next
            // why: the last row stays as packed as it is.
            val spare = span - row.sumOf { widths[it].toDouble() }.toFloat()
            val gap = if (rank == rows.size - 1 || row.size < 2) MIN_GAP * density else spare / (row.size - 1)
            var left = if (rank % 2 == 0) 0f else ROW_SHIFT * density
            for (index in row) {
                val tree = trees[index]
                val x = left + widths[index] / 2
                left += widths[index] + gap
                // why: the tap target follows THIS tree's crown, never the row's band, so a
                // seedling's target does not reach into the row above.
                val crown = (max(treeHeight(tree), MIN_HEIGHT) + TAP_MARGIN) * density
                val reach = max(crown, (MIN_TAP - LABEL_HEIGHT) * density)
                cells += tree to Rect(x - widths[index] / 2, stand - reach, x + widths[index] / 2, stand + LABEL_HEIGHT * density)
            }
        }
        // The forest starts at its highest crown, not at a first row's empty band.
        val top = cells.minOf { it.second.top }
        return cells
            .map { (tree, cell) ->
                val placed = cell.translate(0f, -top)
                val foot = Offset(placed.center.x, placed.bottom - LABEL_HEIGHT * density)
                ForestSpot(PlantedTree(tree, foot, treeHeight(tree) * density, density), placed)
            }
            // why: once rows interleave, the tree in front may belong to another row —
            // only one order across the whole forest layers them right.
            .sortedBy { it.planted.foot.y }
    }
}
