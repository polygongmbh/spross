package net.spross.kern.design

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// The wood of one tree and the slots its marks hang on, grown in unit space:
// foot at the origin, growing toward negative y.
//
// The rules are the standard ones for procedural trees at icon size
// (Weber & Penn 1995 §5: at 5–20% of screen height the BRANCH STRUCTURE has to be right):
//
//   · Monopodial branching — one child continues the parent's line and keeps most
//     of its length, the others depart sharply and are shorter.
//   · Da Vinci's rule for taper: r_parent² ≈ Σ r_child², so 0.80² + 0.60² = 1.
//   · Every segment bows slightly; a straight line never occurs in a tree.
//   · A bare trunk before the first fork.
//
// How far the tree has forked is its vigor, in generations: the youngest wood is short,
// and a twig forks once it is full length. Marks hang along every limb but the trunk.

/** A limb marks hang on; [tip] if it forks no further. */
private class Carrier(val limb: Int, val tip: Boolean)

internal class TreeGrowth(private val seed: Long, private val vigor: Double) {
    val limbs = mutableListOf<TreeLimb>()
    private val carriers = mutableListOf<Carrier>()

    /** One branch and everything above it, seeded by [path], its name from the trunk up. */
    fun branch(path: Long, x: Double, y: Double, heading: Double, length: Double, width: Double,
               depth: Int, parent: Int, side: Double) {
        val grown = (vigor - depth).coerceIn(0.0, 1.0)
        if (grown <= 0) return
        // why: a branch dips a little below horizontal, no further, or it hangs its leaves under the crown.
        val angle = heading.coerceIn(-PI - 0.25, 0.25)
        val rng = Stream(seed xor Stream.hash(path))
        val reach = length * grown
        val endX = x + cos(angle) * reach
        val endY = y + sin(angle) * reach
        val bow = reach * rng.range(0.04, 0.10) * (if (rng.next() < 0.5) -1 else 1)
        // A branch whose children have not started is a tip, full length or not.
        // why: a twig sprouts at half length, never as a stub — a stub's marks would all sit on its fork.
        val forks = grown >= 1 && vigor >= depth + 1.5
        limbs += TreeLimb(
            x, y,
            (x + endX) / 2 + cos(angle + PI / 2) * bow, (y + endY) / 2 + sin(angle + PI / 2) * bow,
            endX, endY,
            // The trunk flares where it meets the ground; a growing tip narrows to a point.
            if (depth == 0) width * 1.3 else width, width * if (grown < 1) 0.45 + 0.35 * grown else 0.8,
            depth, parent,
        )
        val index = limbs.size - 1
        if (depth >= 1) carriers += Carrier(index, !forks)
        if (!forks) return

        // why: the lead bends little and the others turn well away, so no two siblings
        // part at less than about 30° and run side by side.
        val leadTurn = rng.range(0.05, 0.20) * (if (rng.next() < 0.5) -1 else 1)
        val leadLength = rng.range(0.86, 0.95)
        val sideTurn = rng.range(0.75, 1.10)
        val sideLength = rng.range(0.76, 0.90)
        // why: the trunk always forks three ways, so the crown has low limbs on both sides.
        val third = rng.next() < 0.5 || depth == 0
        val thirdTurn = rng.range(0.80, 1.10)
        // why: a short trunk under long first limbs — a low, bushy crown that fits a row.
        val next = if (depth == 0) length * 1.2 else length
        // why: limbs sag under their weight, the more level and the later-born the further.
        val sagged = angle + 0.02 * (depth + 1) * cos(angle)
        branch(path * 4 + 1, endX, endY, sagged + leadTurn, next * leadLength, width * 0.8, depth + 1, index, -side)
        branch(path * 4 + 2, endX, endY, sagged + side * sideTurn, next * sideLength, width * 0.6, depth + 1, index, -side)
        if (third) branch(path * 4 + 3, endX, endY, sagged - side * thirdTurn, next * 0.7, width * 0.45, depth + 1, index, side)
    }

    /**
     * [count] slots on the wood in rank order. The first round gives each carrier one mark at
     * its outer end, the levelest and inner wood first so fruit and blossom hang as spur fruit
     * does — but never within reach of a mark already dealt while wood further off is free,
     * the reach shrinking as the crown fills, so they spread over the whole crown.
     * The rest go to whichever carrier holds the fewest for its weighted length.
     */
    fun hang(count: Int): List<TreeSlot> {
        if (count <= 0 || carriers.isEmpty()) return emptyList()
        fun chord(c: Carrier) = limbs[c.limb].let { hypot(it.endX - it.startX, it.endY - it.startY) }.coerceAtLeast(1e-6)
        fun rank(c: Carrier) = abs(limbs[c.limb].endX - limbs[c.limb].startX) / chord(c) + if (c.tip) 0 else 1
        val pending = carriers.sortedByDescending(::rank).toMutableList()
        var reach = 2 * carriers.sumOf(::chord) / carriers.size
        val outer = carriers.associateWith { at(it, 0.95) }
        val dealt = mutableListOf<Carrier>()
        while (pending.isNotEmpty()) {
            val free = pending.indexOfFirst { c -> dealt.all { distance(outer.getValue(it), outer.getValue(c)) >= reach } }
            if (free < 0 && reach > 1e-6) { reach *= 0.8; continue }
            dealt += pending.removeAt(free.coerceAtLeast(0))
        }
        // why: inner wood counts half its length, so the twigs carry most of the foliage.
        val weights = dealt.map { chord(it) * if (it.tip) 1.0 else 0.5 }
        val held = IntArray(dealt.size)
        val order = List(count) { n ->
            val i = if (n < dealt.size) n else held.indices.minBy { held[it] / weights[it] }
            i to held[i]++
        }
        return order.map { (i, k) -> slot(dealt[i], k, held[i], i % 2 == 0) }
    }

    /**
     * The [k]-th of a carrier's [of] marks, spread evenly from 0.95 of the way along down to 0.15,
     * alternating sides on the bark and leaning away from the wood.
     */
    private fun slot(c: Carrier, k: Int, of: Int, flip: Boolean): TreeSlot {
        val limb = limbs[c.limb]
        val t = 0.95 - 0.8 * k / of
        val (x, y) = at(c, t)
        val along = heading(limb, t)
        val side = if ((k % 2 == 1) != flip) 1 else -1
        val bark = (limb.startWidth * (1 - t) + limb.endWidth * t) / 2 * side
        // why: a leaf follows its wood, splayed to one side, and never points below horizontal.
        return TreeSlot(x + cos(along + PI / 2) * bark, y + sin(along + PI / 2) * bark,
            (along + side * 0.9).coerceIn(-PI + 0.3, -0.3), c.limb)
    }

    /** The point [t] of the way along [c]'s center line. */
    private fun at(c: Carrier, t: Double): Pair<Double, Double> {
        val l = limbs[c.limb]
        val u = 1 - t
        return (u * u * l.startX + 2 * u * t * l.controlX + t * t * l.endX) to
            (u * u * l.startY + 2 * u * t * l.controlY + t * t * l.endY)
    }

    private fun distance(a: Pair<Double, Double>, b: Pair<Double, Double>) = hypot(a.first - b.first, a.second - b.second)

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
