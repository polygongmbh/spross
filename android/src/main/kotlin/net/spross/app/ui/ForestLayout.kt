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
 * catalog holds; the crown's shape comes from the words met ([TreeSkeleton.vigor]).
 */
internal object ForestLayout {
    const val MIN_HEIGHT = 9f
    private const val MAX_HEIGHT = 42f
    /** The narrowest column a grown tree claims — five across a phone; a crown may spill past it, its label never is. */
    private const val COLUMN = 64f
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
     * stand half a row apart, every row holds the same count of columns, and every second one
     * opens half a column further on — a checkerboard, so a tree grows up through the gap
     * between two of the row above: one mass, not a shelf of drawers. Every grown tree claims
     * the same column, an ungrown sapling half of one, and the columns stretch to fill the
     * width exactly — so spacing is shared between the trees, the sides touched but for the
     * half column the shifted rows give up, and a shifted row's trees stand midway between
     * two labels above. Each grown tree drifts a little off its column.
     */
    fun plant(trees: List<AreaTree>, width: Float, density: Float): List<ForestSpot> {
        if (width <= 0f || trees.isEmpty()) return emptyList()
        // why: the shifted row leaves half a column over, so a row's columns plus that half fill the width.
        val across = max(1, (width / (COLUMN * density) - 0.5f).toInt())
        // One unit is half a column; a tree takes two, an ungrown sapling — one that has not
        // yet grown half its way — one: a stem and a label need no more.
        val unit = width / (2 * across + 1)
        fun size(tree: AreaTree) = if (standing(tree) < 0.5f) 1 else 2
        val rows = mutableListOf(mutableListOf<Int>())
        var used = 0
        for (index in trees.indices) {
            if (used + size(trees[index]) > 2 * across) {
                rows += mutableListOf<Int>()
                used = 0
            }
            rows.last() += index
            used += size(trees[index])
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
            var taken = rank % 2
            for (index in row) {
                val tree = trees[index]
                val units = size(tree)
                val drift = if (units == 1) 0f else (Mix.noise(tree.area, 31) - 0.5f) * 8f * density
                val x = (taken + units / 2f) * unit + drift
                taken += units
                // why: the tap target follows THIS tree's crown, never the row's band, so a
                // seedling's target does not reach into the row above.
                val crown = (max(treeHeight(tree), MIN_HEIGHT) + TAP_MARGIN) * density
                val reach = max(crown, (MIN_TAP - LABEL_HEIGHT) * density)
                cells += tree to Rect(x - units * unit / 2, stand - reach, x + units * unit / 2, stand + LABEL_HEIGHT * density)
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
