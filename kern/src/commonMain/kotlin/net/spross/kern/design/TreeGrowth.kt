package net.spross.kern.design

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.withSign

// The wood of one tree and the slots its marks hang on, grown together in unit space:
// foot at the origin, growing toward negative y.
//
// The marks come first and the wood is whatever it takes to carry them (Weber & Penn 1995):
// a branch carrying n marks hands half of them to side branches along its length and the
// rest to the lead that continues from its end, until a branch carrying one mark is a leaf
// twig holding it at its tip. Width follows the pipe model, w ∝ √n, so r_parent² = Σ r_child².
// The picture is a round tree's front view: a child leaves its parent at a down angle, wider nearer its
// base, turned on about it by ROTATE from the child before (the lead too, so the turn carries up the
// tree), and shows steeper and shorter the more it is turned toward or away from the viewer.

/** A leaf twig's slot, with the path that seeds its rank; [steep] if steeper than about 60°. */
private class Leaf(val slot: TreeSlot, val path: Long, val steep: Boolean)

private const val ROTATE = 140 * PI / 180

internal class TreeGrowth(private val seed: Long) {
    val limbs = mutableListOf<TreeLimb>()
    private val leaves = mutableListOf<Leaf>()

    /** Leaf twigs in a seeded order, the steep ones last so fruit and blossom hang on level wood. */
    fun ranked(): List<TreeSlot> {
        val shuffled = leaves.sortedBy { Stream.hash(seed xor it.path) }
        return (shuffled.filter { !it.steep } + shuffled.filter { it.steep }).map { it.slot }
    }

    // why: a branch dips a little below horizontal, no further, or it hangs its leaves under the crown.
    private fun clamped(heading: Double) = heading.coerceIn(-PI - 0.25, 0.25)

    /** One branch carrying [n] marks and all beyond it, seeded by [path], leaving [heading] at [down] turned [turn]. */
    fun branch(n: Int, path: Long, x: Double, y: Double, heading: Double, down: Double, turn: Double, depth: Int, parent: Int) {
        val rng = Stream(seed xor Stream.hash(path))
        // why: floored at half, so a branch turned toward the viewer still spreads, and the crown stays bushy.
        val splay = sin(down) * max(abs(cos(turn)), 0.5).withSign(cos(turn))
        val angle = clamped(heading + atan2(splay, cos(down)))
        val side = if (splay < 0) -1.0 else 1.0
        // why: a side branch starts out longer than a lead, so the crown spreads wider than it rises.
        val length = hypot(cos(down), splay) *
            if (n == 1) 0.03 else 0.045 * (1 + 0.6 * ln(n.toDouble())) * if (path and 3L >= 2L) 1.6 else 1.0
        val width = 0.0075 * sqrt(n.toDouble())
        val endX = x + cos(angle) * length
        val endY = y + sin(angle) * length
        // why: every branch leaves its parent wide and arches back toward it.
        val bow = length * rng.range(0.04, 0.10) * side
        val limb = TreeLimb(
            x, y,
            (x + endX) / 2 + cos(angle + PI / 2) * bow, (y + endY) / 2 + sin(angle + PI / 2) * bow,
            endX, endY,
            // The trunk flares where it meets the ground.
            if (depth == 0) width * 1.3 else width, width * 0.8, depth, parent,
        )
        limbs += limb
        val index = limbs.size - 1

        // why: measured from this branch's own heading, so a left-leaning tangent never wraps past ±π.
        fun along(t: Double): Double {
            val bend = heading(limb, t) - angle
            return angle + bend - 2 * PI * round(bend / (2 * PI))
        }
        if (n == 1) {
            // why: a leaf follows its twig, splayed to one side, and never points below horizontal.
            val slot = TreeSlot(endX, endY, (along(1.0) + side * 0.9).coerceIn(-PI + 0.3, -0.3), index)
            leaves += Leaf(slot, path, abs(endX - x) < 0.6 * abs(endY - y))
            return
        }
        // why: limbs sag under their weight, the more level and the later-born the further.
        val sag = 0.02 * (depth + 1) * cos(angle)
        val r = n / 2
        val sides = if (r == 1) listOf(r to 0.7) else listOf((r + 1) / 2 to 0.55, r / 2 to 0.8)
        sides.forEachIndexed { k, (count, t) ->
            val u = 1 - t
            branch(count, path * 4 + 2 + k,
                u * u * limb.startX + 2 * u * t * limb.controlX + t * t * limb.endX,
                u * u * limb.startY + 2 * u * t * limb.controlY + t * t * limb.endY,
                along(t) + sag, 1.6 - 0.8 * t, turn + (k + 1) * ROTATE, depth + 1, index)
        }
        branch(n - r, path * 4 + 1, endX, endY, angle + sag, 0.15, turn + (sides.size + 1) * ROTATE, depth + 1, index)
    }

    /** The direction [limb]'s center line runs [t] of the way along. */
    private fun heading(limb: TreeLimb, t: Double): Double {
        val u = 1 - t
        return atan2(u * (limb.controlY - limb.startY) + t * (limb.endY - limb.controlY),
            u * (limb.controlX - limb.startX) + t * (limb.endX - limb.controlX))
    }
}

/** SplitMix64: a stable stream per seed. */
internal class Stream(private var state: Long) {
    fun next(): Double = (hash(state.also { state += GOLDEN }) ushr 11).toDouble() / (1L shl 53)
    fun range(low: Double, high: Double) = low + (high - low) * next()

    companion object {
        private const val GOLDEN = -0x61c8864680b583ebL

        fun hash(value: Long): Long {
            var x = value + GOLDEN
            x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L
            x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L
            return x xor (x ushr 31)
        }
    }
}
