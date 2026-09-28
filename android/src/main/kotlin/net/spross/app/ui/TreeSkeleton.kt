package net.spross.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The wood of one tree and the slots its marks hang on — plain geometry, no drawing.
//
// A tree grows from its tips and nowhere else: every limb takes its angle, length and bow
// from a generator seeded by its own path from the trunk, never by how far the tree has
// grown, so a limb keeps its shape for life. More vigor only lengthens the youngest wood
// and, once a twig is full length, forks it.
//
// One child carries its parent's line on and the other leaves it sharply (monopodial), the
// width splits so the children's cross-sections add up to the parent's, and every limb bows.
// Marks gather at the twig ends first — each tip holds a cluster, dealt out tip by tip — and
// every mark past a tip's own first one runs back along that tip's limb toward the trunk
// instead, biased inward, from the first extra word a tip gets onward, so a crown leafs out
// along its wood as it grows rather than piling every mark on top of the one at the twig's end.

/** One length of wood: a bowed center line tapering from [startWidth] to [endWidth]. */
internal class TreeLimb(
    val start: Offset,
    val control: Offset,
    val end: Offset,
    val startWidth: Float,
    val endWidth: Float,
    val depth: Int,
)

/** Somewhere a mark hangs: a point in one tip's cluster, facing [angle] (radians). */
internal class TreeSlot(val point: Offset, val angle: Float, val cluster: Int)

internal class TreeSkeleton(
    val limbs: List<TreeLimb>,
    /** Every tip's first slot, then every tip's second, …: adding a mark moves none placed. */
    val slots: List<TreeSlot>,
    /** The side of the square each hung mark would get if the crown were shared out evenly. */
    val pitch: Float,
) {
    /** The same tree scaled by [scale] and moved so its unit origin lands on [foot]. */
    fun placed(foot: Offset, scale: Float): TreeSkeleton {
        fun at(p: Offset) = Offset(foot.x + p.x * scale, foot.y + p.y * scale)
        return TreeSkeleton(
            limbs.map {
                TreeLimb(at(it.start), at(it.control), at(it.end),
                    it.startWidth * scale, it.endWidth * scale, it.depth)
            },
            slots.map { TreeSlot(at(it.point), it.angle, it.cluster) },
            pitch * scale,
        )
    }

    /** The unit-space bounds of the wood and the first [hung] slots. */
    fun bounds(hung: Int): Rect {
        var left = 0f; var right = 0f; var top = 0f
        for (p in limbs.flatMap { listOf(it.start, it.control, it.end) } + slots.take(hung).map { it.point }) {
            left = min(left, p.x); right = max(right, p.x); top = min(top, p.y)
        }
        return Rect(left, top, right, 0f)
    }

    companion object {
        /** The deepest generation a tree forks to; past it the twigs are under a pixel. */
        const val MAX_DEPTH = 5

        /** How many generations the tree has grown — from the words met, never from height. */
        fun vigor(met: Int): Float = 1.3f + (MAX_DEPTH - 1.3f) * min(1f, sqrt(met / 60f))

        /** One slot per word, with a floor so a handful of words do not each claim a limb. */
        fun slotCount(met: Int): Int = max(8, met)

        /** Grows one tree in unit space: foot at the origin, growing toward negative y. */
        fun grow(seed: Long, vigor: Float, slots: Int): TreeSkeleton {
            // The tree's habit — how tall its trunk, how far it leans, how wide it forks —
            // comes from its seed alone, so no two areas grow the same tree.
            val habit = Mix(seed)
            val trunk = habit.range(0.19f, 0.29f)
            val lean = habit.range(-0.12f, 0.12f)
            val growth = Growth(seed, vigor, spread = habit.range(0.8f, 1.25f))
            growth.limb(1L, Offset.Zero, -PI_F / 2 + lean, trunk, 0.014f + 0.011f * vigor, 0, 1f)
            val hung = growth.hang(slots)
            return TreeSkeleton(growth.limbs, hung, pitch(hung.take(slots)))
        }

        private fun pitch(slots: List<TreeSlot>): Float {
            if (slots.size < 2) return 0.05f
            val spread = slots.maxOf { it.point.x } - slots.minOf { it.point.x }
            val rise = slots.maxOf { it.point.y } - slots.minOf { it.point.y }
            return sqrt(max(spread, 0.02f) * max(rise, 0.02f) / slots.size)
        }
    }
}

internal const val PI_F = Math.PI.toFloat()

/** One length of a tip's own lineage — the limbs from the trunk down to it — each a place a mark can hang. */
private class LineageLimb(val base: Offset, val end: Offset, val angle: Float, val reach: Float)

/**
 * [lineage] runs trunk-first, the tip's own limb last — its last entry IS the tip's own
 * limb, so nothing about the tip itself needs repeating outside this list.
 */
private class Tip(val path: Long, val lineage: List<LineageLimb>)

private class Growth(val seed: Long, val vigor: Float, val spread: Float) {
    val limbs = mutableListOf<TreeLimb>()
    private val tips = mutableListOf<Tip>()

    fun limb(
        path: Long, origin: Offset, angle: Float, length: Float, width: Float, depth: Int, side: Float,
        lineage: List<LineageLimb> = emptyList(),
    ) {
        val grown = (vigor - depth).coerceIn(0f, 1f)
        if (grown <= 0f) return
        val rng = Mix(seed xor Mix.hash(path))
        val reach = length * grown
        val end = Offset(origin.x + cos(angle) * reach, origin.y + sin(angle) * reach)
        val bow = reach * rng.range(0.04f, 0.10f) * rng.sign()
        val control = Offset(
            (origin.x + end.x) / 2 + cos(angle + PI_F / 2) * bow,
            (origin.y + end.y) / 2 + sin(angle + PI_F / 2) * bow,
        )
        // A growing tip narrows to a point; a finished limb hands its width on.
        val endWidth = width * if (grown < 1f) 0.45f + 0.35f * grown else 0.80f
        limbs += TreeLimb(origin, control, end, if (depth == 0) width * 1.3f else width, endWidth, depth)
        val ownLineage = lineage + LineageLimb(origin, end, angle, reach)

        // why: drawn in full even for a limb that forks no further, so the sequence — and
        // with it every child's shape — never depends on how deep the tree has grown.
        val lean = rng.sign()
        val leadTurn = rng.range(0.10f, 0.30f) * lean
        val leadLength = rng.range(0.80f, 0.92f)
        val sideTurn = rng.range(0.62f, 1.08f) * spread
        val sideLength = rng.range(0.66f, 0.82f)
        val third = depth <= 2 && rng.next() < 0.32f
        val thirdTurn = rng.range(0.45f, 0.85f) * spread

        if (grown < 1f || depth >= TreeSkeleton.MAX_DEPTH || vigor <= depth + 1) {
            tips += Tip(path, ownLineage)
            return
        }
        // Each generation reaches a little further toward the light.
        val lifted = angle + (-PI_F / 2 - angle) * 0.06f * (depth + 1)
        limb(path * 4 + 1, end, lifted + leadTurn, length * leadLength, width * 0.80f, depth + 1, -side, ownLineage)
        limb(path * 4 + 2, end, lifted + side * sideTurn, length * sideLength, width * 0.60f, depth + 1, -side, ownLineage)
        if (third) {
            limb(path * 4 + 3, end, lifted - side * thirdTurn, length * 0.55f, width * 0.45f, depth + 1, side, ownLineage)
        }
    }

    /** Every tip's k-th slot before any tip's (k+1)-th, so every cluster thickens together. */
    fun hang(target: Int): List<TreeSlot> {
        if (tips.isEmpty() || target <= 0) return emptyList()
        val order = tips.indices.sortedBy { Mix.hash(seed xor tips[it].path) }
        val depth = target / tips.size + 2
        return (0 until depth).flatMap { k -> order.map { slot(k, tips[it], it) } }
    }

    /**
     * The k-th slot of a tip, seeded by the tip and k alone. k = 0 pins to the tip
     * itself; every slot past it ranges back along the tip's own FORKED wood instead,
     * biased toward the trunk end, so a cluster that keeps growing spreads leaves out
     * along the limb rather than piling up on top of the one at the twig's end — the
     * trunk itself stays out of reach, bare, same as the rest of the tree's shape.
     */
    private fun slot(k: Int, tip: Tip, index: Int): TreeSlot {
        val own = tip.lineage.last()
        if (k == 0) return TreeSlot(own.end, own.angle, index)
        val rng = Mix(seed xor Mix.hash(tip.path + k * 0x9E3779B9L))
        // lineage[0] is always the trunk (every tip's path starts there) — excluded
        // so a bare trunk before the first fork holds however deep a cluster grows.
        val forkedWood = tip.lineage.drop(1)
        val totalReach = forkedWood.sumOf { it.reach.toDouble() }.toFloat()
        val bias = rng.next().pow(1.6f)
        var remaining = bias * totalReach
        var chosen = own
        var localT = 1f
        for (seg in forkedWood) {
            if (remaining <= seg.reach) {
                chosen = seg
                localT = if (seg.reach > 0f) remaining / seg.reach else 1f
                break
            }
            remaining -= seg.reach
        }
        val along = Offset(
            chosen.base.x + (chosen.end.x - chosen.base.x) * localT,
            chosen.base.y + (chosen.end.y - chosen.base.y) * localT,
        )
        // why: the offset is the tip's OWN twig size regardless of which limb was
        // chosen — the trunk's limb is by far the tree's longest, and a radius
        // scaled to it flings a mark chosen there way off into empty air.
        val radius = max(own.reach, 0.07f) * rng.range(0.25f, 0.85f)
        val off = chosen.angle + rng.sign() * rng.range(0.6f, 1.9f)
        return TreeSlot(Offset(along.x + cos(off) * radius, along.y + sin(off) * radius), off, index)
    }
}

/** SplitMix64: a stable stream per seed, the same on every run and every device. */
internal class Mix(private var state: Long) {
    fun next(): Float = ((hash(state.also { state += GOLDEN }) ushr 40).toFloat() / (1 shl 24))
    fun range(low: Float, high: Float) = low + (high - low) * next()
    fun sign() = if (next() < 0.5f) -1f else 1f

    companion object {
        private const val GOLDEN = -0x61c8864680b583ebL

        fun hash(value: Long): Long {
            var x = value + GOLDEN
            x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L
            x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L
            return x xor (x ushr 31)
        }

        /** A seed for a name — the area's id, so its tree is the same wherever it is drawn. */
        fun seed(name: String): Long = name.fold(-0x340d631b7bdddcdbL) { h, c -> (h xor c.code.toLong()) * 0x100000001b3L }

        /** Stable 0…1 noise for one (name, property). */
        fun noise(name: String, salt: Int): Float = Mix(seed(name) + salt).next()
    }
}
