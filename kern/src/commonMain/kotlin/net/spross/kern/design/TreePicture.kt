package net.spross.kern.design

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import net.spross.kern.box.AreaGrowth
import net.spross.kern.model.fnv1a64

/** The theme color a layer is inked in; each platform maps it onto its own color table. */
enum class TreeInk { GROUND, WOOD, WOOD_SHADE, LEAF, LEAF_DEEP, BUD, FRUIT, BLOSSOM, FALLEN, ACCENT }

/** One outline as plain numbers, in points (dp): a run of commands, each its opcode, then its arguments. */
object TreePath {
    /** x, y */
    const val MOVE = 0
    /** x, y */
    const val LINE = 1
    /** controlX, controlY, x, y */
    const val QUAD = 2
    const val CLOSE = 3
    /** left, top, width, height: the ellipse inside that box. */
    const val OVAL = 4
}

/**
 * One piece of a layer: the mark at [rank], or −1 for anything that is not a mark.
 * A mark arriving in the summary's rise ([TreeRise]) is drawn scaled about its slot, ([pivotX], [pivotY]).
 */
class TreeShape(val rank: Int, val pivotX: Double, val pivotY: Double, val path: DoubleArray)

/** One layer, drawn in order: its shapes filled, or stroked [stroke] wide with round ends when above 0. */
class TreeLayer(val ink: TreeInk, val opacity: Double, val stroke: Double, val shapes: List<TreeShape>)

/**
 * One area's tree as it is drawn, in points (dp): a platform turns each layer into a path
 * and inks it in its theme's color for [TreeInk].
 *
 * The tree is ONE organism its whole life, never swapped for another:
 * a seedling thickens into a trunk, the crown fills with the words that have landed,
 * and blossom and fruit appear ON it rather than replacing it.
 * Each word the learner has met is exactly one mark, hung on its slot ([AreaTree.grow]) in rank order,
 * told apart by shape before color:
 *   fruit    — matured: a round disc hanging under its twig
 *   blossom  — settled: five petals round an eye
 *   leaf     — growing: a sprig of three pointed leaflets
 *   bud      — fresh: a small disc
 */
class TreePicture internal constructor(val layers: List<TreeLayer>) {
    companion object {
        /** [tree] grown from its [seed], foot at ([footX], [footY]), [height] tall. */
        fun of(tree: AreaGrowth, seed: String, footX: Double, footY: Double, height: Double): TreePicture =
            TreePicture(Painter(tree, seed, footX, footY, height).paint())
    }
}

internal class Painter(
    val tree: AreaGrowth,
    val seed: String,
    val footX: Double,
    val footY: Double,
    val height: Double,
) {
    private val layers = mutableListOf<TreeLayer>()
    private val noise = fnv1a64(seed).toLong()

    fun paint(): List<TreeLayer> {
        ground()
        when {
            // why: an area nobody has opened stands as a faded seedling on its own patch of ground —
            // a place to go rather than a chore not done.
            tree.isBare -> seedling(max(height, AreaTree.MIN_HEIGHT), 0.45)
            tree.met == 0 || height <= 0 -> seedling(height, 1.0)
            else -> crown()
        }
        fallen()
        if (tree.answeredToday) freshEarth()
        return layers
    }

    fun layer(ink: TreeInk, opacity: Double, shapes: List<TreeShape>, stroke: Double = 0.0) {
        if (shapes.isNotEmpty()) layers += TreeLayer(ink, opacity, stroke, shapes)
    }

    fun layer(ink: TreeInk, opacity: Double, pen: Pen, stroke: Double = 0.0) {
        if (!pen.isEmpty) layer(ink, opacity, listOf(TreeShape(-1, footX, footY, pen.done())), stroke)
    }

    /** A stable 0…1 draw for the [n]-th of something named by [salt], from this tree's seed alone. */
    fun draw(salt: Long, n: Int): Double = Stream(noise xor Stream.hash(salt * 65_536 + n)).next()

    /** What the tree stands on: a soft shadow under the trunk, never a line. */
    private fun ground() {
        val width = max(9.0, height * 0.42)
        layer(TreeInk.GROUND, 0.55, Pen().apply { oval(footX - width / 2, footY - 1.6, width, 3.2) })
    }

    /** Nothing met yet: a stem and two leaflets. */
    private fun seedling(height: Double, opacity: Double) {
        val top = footY - height
        layer(TreeInk.LEAF, opacity, Pen().apply {
            move(footX, footY)
            quad(footX + height * 0.08, footY - height * 0.5, footX, top)
        }, stroke = max(1.4, height * 0.055))
        val size = max(4.0, height * 0.34)
        layer(TreeInk.LEAF, opacity, Pen().apply {
            leaf(footX, top, size, -0.7)
            leaf(footX, top, size * 0.85, PI + 0.7)
        })
    }

    /** Words that lapsed: leaves on the ground beside the trunk; the tree never shrinks for them. */
    private fun fallen() {
        val clear = max(7.0, height * 0.2)
        val size = max(3.0, height * 0.055)
        val pen = Pen()
        for (index in 0 until min(tree.stages.lapsed, 3)) {
            val side = if (index % 2 == 0) -1 else 1
            val spread = clear + draw(FALLEN, index) * clear * 0.5
            pen.leaf(footX + side * spread, footY + 0.5, size, if (side > 0) 0.2 else PI - 0.2)
        }
        layer(TreeInk.FALLEN, 0.85, pen)
    }

    /**
     * Answered today: a short line of fresh earth at the foot, on the GROUND —
     * it says this area was tended today, not that anything in it grew a stage.
     */
    private fun freshEarth() {
        val half = max(6.0, height * 0.14)
        layer(TreeInk.ACCENT, 1.0, Pen().apply {
            move(footX - half, footY + 3.5)
            line(footX + half, footY + 3.5)
        }, stroke = max(1.6, height * 0.03))
    }

    private companion object {
        const val FALLEN = 0x66616c6cL
    }
}

/** A growing outline in [TreePath] commands. */
internal class Pen {
    private var data = DoubleArray(32)
    private var size = 0
    val isEmpty: Boolean get() = size == 0

    private fun put(vararg values: Double) {
        if (size + values.size > data.size) data = data.copyOf(max(data.size * 2, size + values.size))
        values.copyInto(data, size)
        size += values.size
    }

    fun move(x: Double, y: Double) = put(TreePath.MOVE.toDouble(), x, y)
    fun line(x: Double, y: Double) = put(TreePath.LINE.toDouble(), x, y)
    fun quad(cx: Double, cy: Double, x: Double, y: Double) = put(TreePath.QUAD.toDouble(), cx, cy, x, y)
    fun close() = put(TreePath.CLOSE.toDouble())
    fun oval(left: Double, top: Double, width: Double, height: Double) =
        put(TreePath.OVAL.toDouble(), left, top, width, height)
    fun circle(x: Double, y: Double, radius: Double) = oval(x - radius, y - radius, radius * 2, radius * 2)

    fun done(): DoubleArray = data.copyOf(size)
}
