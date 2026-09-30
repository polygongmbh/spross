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
    private const val MAX_HEIGHT = 48f
    private const val MIN_CELL = 52f
    private const val ROW_HEIGHT = 70f
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
     * stand half a row apart and every second one opens half a cell further on, so a tree
     * grows up through the gap between two of the row above — one mass, not a shelf of
     * drawers. A tree claims room in proportion to its own size, and drifts a little.
     */
    fun plant(trees: List<AreaTree>, width: Float, density: Float): List<ForestSpot> {
        if (width <= 0f || trees.isEmpty()) return emptyList()
        val room = trees.map { max(MIN_CELL * 0.62f, treeHeight(it) * 1.28f + 12f) * density }
        // One gap for the whole forest, from the row that can give the least,
        // so every row walks the same lattice.
        val gap = rows(room, width, 0f).minOf { row ->
            max(0f, width - row.sumOf { room[it].toDouble() }.toFloat()) / (row.size + 1)
        }
        val cells = mutableListOf<Pair<AreaTree, Rect>>()
        var stand = 0f
        var band = 0f
        for ((rank, row) in rows(room, width, gap).withIndex()) {
            val tallest = row.maxOf { treeHeight(trees[it]) }
            val next = max(ROW_HEIGHT, tallest + 10f) * density
            // why: half a row on from the row behind, but never so little that this row's
            // tallest crown reaches up over the labels standing under that row.
            stand = if (rank == 0) next else stand + max(
                (band + (LABEL_HEIGHT + ROW_GAP) * density) / 2 + next - band,
                (tallest + LABEL_HEIGHT + 10f) * density,
            )
            band = next
            var x = if (rank % 2 == 0) 0f else (room[row[0]] + gap) / 2
            for (index in row) {
                val tree = trees[index]
                val drift = (Mix.noise(tree.area, 31) - 0.5f) * min(gap, 10f * density)
                // why: the tap target follows THIS tree's crown, never the row's band, so a
                // seedling's target does not reach into the row above.
                val crown = (max(treeHeight(tree), MIN_HEIGHT) + TAP_MARGIN) * density
                val reach = max(crown, (MIN_TAP - LABEL_HEIGHT) * density)
                cells += tree to Rect(x + drift, stand - reach, x + drift + room[index], stand + LABEL_HEIGHT * density)
                x += room[index] + gap
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

    /** Indices bound into rows by [room], each tree costing [gap] more, odd rows opening half a cell on. */
    private fun rows(room: List<Float>, width: Float, gap: Float): List<List<Int>> {
        val rows = mutableListOf<MutableList<Int>>()
        var used = 0f
        for (index in room.indices) {
            if (rows.isEmpty() || (rows.last().isNotEmpty() && used + gap + room[index] > width)) {
                rows += mutableListOf<Int>()
                used = if (gap > 0f && rows.size % 2 == 0) (room[index] + gap) / 2 else 0f
            }
            rows.last() += index
            used += gap + room[index]
        }
        return rows
    }
}
