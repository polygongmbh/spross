package net.spross.kern.design

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import net.spross.kern.box.AreaTree
import net.spross.kern.model.fnv1a64

/**
 * One length of wood: a bowed center line (a quadratic from start through control to end)
 * tapering from [startWidth] to [endWidth]; [parent] is the limb it grows from, -1 for the trunk.
 */
data class TreeLimb(
    val startX: Double,
    val startY: Double,
    val controlX: Double,
    val controlY: Double,
    val endX: Double,
    val endY: Double,
    val startWidth: Double,
    val endWidth: Double,
    val depth: Int,
    val parent: Int,
)

/** Somewhere a mark hangs, facing [angle] (radians) outward from its twig, the [limb]-th. */
data class TreeSlot(val x: Double, val y: Double, val angle: Double, val limb: Int)

/** Where a unit-space point lands: `x + unitX * scale`, `y + unitY * scale`; widths scale alike. */
data class TreeFit(val x: Double, val y: Double, val scale: Double)

/** A tree standing on its own: its foot and its height, foot to crown. */
data class TreeStand(val footX: Double, val footY: Double, val height: Double)

/** One tree in unit space: foot at the origin, growing toward negative y. */
class GrownTree internal constructor(
    val limbs: List<TreeLimb>,
    /** One slot per mark, in rank order: fruit hangs on the first, then blossom, leaf, bud. */
    val slots: List<TreeSlot>,
    /** The side of the square each mark would get if the crown were shared out evenly. */
    val pitch: Double,
    private val left: Double,
    private val right: Double,
    private val rise: Double,
) {
    /**
     * Fitted flush into a box [TreeLayout.CROWN_BOX] heights wide, foot on its bottom edge at
     * ([footX], [footY]). The marks hang past it wherever the twigs reach its edge.
     */
    fun fit(footX: Double, footY: Double, height: Double): TreeFit {
        // why: the tighter of the two constraints wins, so a wide crown is narrowed rather than clipped.
        val scale = min(max(height, 1.0) / rise, max(height * TreeLayout.CROWN_BOX, 1.0) / (right - left))
        return TreeFit(footX - (left + right) / 2 * scale, footY, scale)
    }
}

/**
 * One tree: its size from what has grown, and its wood grown from its marks.
 * Lengths are in points (dp); [TreesLayout] stands many of them in rows.
 */
object TreeLayout {
    /** Tree heights, foot to crown: the floor is a seedling, the ceiling keeps the tallest area inside its row. */
    const val MIN_HEIGHT = 9.0
    const val MAX_HEIGHT = 42.0

    /**
     * The mass at which a tree reaches full height — a large area, thoroughly learned.
     * Near the top of what a real box produces, or every worked area saturates and the row stops being a skyline.
     */
    const val FULL_MASS = 24.0

    /** The summary's tree: a seedling's box and a grown tree's, before a screen lifts them. */
    const val HERO_MIN = 78.0
    const val HERO_MAX = 190.0

    /** The box a tree's wood is fitted into, in heights wide. */
    const val CROWN_BOX = 1.44

    /**
     * How far along the area stands, 0…1 — the one curve every height is cut from.
     * Square-rooted, because mass is a sum over words: otherwise the first area worked
     * dwarfs every other for months.
     */
    fun standing(tree: AreaTree): Double =
        if (tree.isBare) 0.0 else min(1.0, sqrt(tree.mass / FULL_MASS))

    /** How tall the area stands among the others; 0 for an area nothing has happened in. */
    fun height(tree: AreaTree): Double =
        if (tree.isBare) 0.0 else MIN_HEIGHT + (MAX_HEIGHT - MIN_HEIGHT) * standing(tree)

    /**
     * The height of the summary's box for [tree]: [ceiling] lifts a grown tree's box on a
     * screen with room to give, and a seedling's floor rises in proportion, so standing still reads as height.
     */
    fun heroHeight(tree: AreaTree, ceiling: Double): Double {
        val top = max(ceiling, HERO_MAX)
        val floor = top * HERO_MIN / HERO_MAX
        return floor + (top - floor) * standing(tree)
    }

    /**
     * One tree alone, filling a [width] × [height] box: the ground a little clear of the
     * bottom edge, so what a tree puts below it is not shaved off.
     */
    fun solitary(width: Double, height: Double): TreeStand {
        val baseline = height - 7
        return TreeStand(width / 2, baseline, min(baseline, width * 0.8))
    }

    /**
     * Grows one tree carrying [marks] marks, seeded by [area]: the same area and count grow
     * the same tree on every redraw. The count is the FINISHED tree's, never the drawn height's,
     * so a tree rising through a transition keeps every mark where it hangs.
     */
    fun grow(area: String, marks: Int): GrownTree {
        if (marks <= 0) return GrownTree(emptyList(), emptyList(), 0.05, -0.5, 0.5, 1.0)
        val seed = fnv1a64(area).toLong()
        val growth = TreeGrowth(seed)
        growth.branch(marks, 1L, 0.0, 0.0, -PI / 2, 0, -1, 1.0)
        val slots = growth.ranked()
        var left = 0.0; var right = 0.0; var top = 0.0
        fun take(x: Double, y: Double) { left = min(left, x); right = max(right, x); top = min(top, y) }
        for (limb in growth.limbs) {
            take(limb.startX, limb.startY); take(limb.controlX, limb.controlY); take(limb.endX, limb.endY)
        }
        for (slot in slots) take(slot.x, slot.y)
        val rise = max(-top, 0.001)
        return GrownTree(growth.limbs, slots, pitch(slots, rise), left, max(right, left + 0.001), rise)
    }

    /** Counted as at least eight marks, so a handful of words stay small. */
    private fun pitch(slots: List<TreeSlot>, rise: Double): Double {
        val floor = rise / 4
        val spread = max(floor, slots.maxOf { it.x } - slots.minOf { it.x })
        val tall = max(floor, slots.maxOf { it.y } - slots.minOf { it.y })
        return sqrt(spread * tall / max(8, slots.size))
    }
}
