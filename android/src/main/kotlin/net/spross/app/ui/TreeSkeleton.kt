package net.spross.app.ui

import androidx.compose.ui.geometry.Offset
import net.spross.kern.design.GrownTree
import net.spross.kern.design.TreeFit

// Kern's grown tree ([net.spross.kern.design.AreaTreeLayout.grow]) placed in pixels for drawing.

/** One length of wood, placed; [parent] is the limb it grows from, -1 for the trunk. */
internal class TreeLimb(
    val start: Offset,
    val control: Offset,
    val end: Offset,
    val startWidth: Float,
    val endWidth: Float,
    val depth: Int,
    val parent: Int,
)

/** Somewhere a mark hangs, facing [angle] (radians) outward from its twig, the [limb]-th. */
internal class TreeSlot(val point: Offset, val angle: Float, val limb: Int)

internal class TreeSkeleton(
    val limbs: List<TreeLimb>,
    /** One slot per mark, in rank order: the render hangs fruit first, then blossom, leaf, bud. */
    val slots: List<TreeSlot>,
    /** The side of the square each mark would get if the crown were shared out evenly. */
    val pitch: Float,
) {
    companion object {
        fun placed(grown: GrownTree, fit: TreeFit): TreeSkeleton {
            val scale = fit.scale
            fun at(x: Double, y: Double) = Offset((fit.x + x * scale).toFloat(), (fit.y + y * scale).toFloat())
            return TreeSkeleton(
                grown.limbs.map {
                    TreeLimb(at(it.startX, it.startY), at(it.controlX, it.controlY), at(it.endX, it.endY),
                        (it.startWidth * scale).toFloat(), (it.endWidth * scale).toFloat(), it.depth, it.parent)
                },
                grown.slots.map { TreeSlot(at(it.x, it.y), it.angle.toFloat(), it.limb) },
                (grown.pitch * scale).toFloat(),
            )
        }
    }
}

internal const val PI_F = Math.PI.toFloat()

/** Stable 0…1 noise for one (name, property): SplitMix64 over an FNV-1a fold of the name. */
internal fun noise(name: String, salt: Int): Float {
    val seed = name.fold(-0x340d631b7bdddcdbL) { h, c -> (h xor c.code.toLong()) * 0x100000001b3L }
    var x = seed + salt + -0x61c8864680b583ebL
    x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L
    x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L
    return ((x xor (x ushr 31)) ushr 40).toFloat() / (1 shl 24)
}
