package net.spross.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
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
// Leaves are the level past the last stems (Weber & Penn §4.6): they grow only on the twigs
// that fork no further, spaced along each and alternating sides — never on older wood,
// never at a fork.

/** One length of wood: a bowed center line tapering from [startWidth] to [endWidth]. */
internal class TreeLimb(
    val start: Offset,
    val control: Offset,
    val end: Offset,
    val startWidth: Float,
    val endWidth: Float,
    val depth: Int,
)

/** Somewhere a mark hangs, facing [angle] (radians) outward from its limb. */
internal class TreeSlot(val point: Offset, val angle: Float)

internal class TreeSkeleton(
    val limbs: List<TreeLimb>,
    /** Slots in the order they were hung: adding a mark moves none placed. */
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
            slots.map { TreeSlot(at(it.point), it.angle) },
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
        fun vigor(met: Int): Float = 1.3f + (MAX_DEPTH - 1.3f) * min(1f, sqrt(met / 30f))

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
            val hung = hang(growth.limbs, growth.twigs, seed, slots)
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

private class Growth(val seed: Long, val vigor: Float, val spread: Float) {
    val limbs = mutableListOf<TreeLimb>()
    /** The limbs that fork no further, with the path that seeds each. */
    val twigs = mutableListOf<Twig>()

    fun limb(path: Long, origin: Offset, angle: Float, length: Float, width: Float, depth: Int, side: Float) {
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

        // why: drawn in full even for a limb that forks no further, so the sequence — and
        // with it every child's shape — never depends on how deep the tree has grown.
        val lean = rng.sign()
        val leadTurn = rng.range(0.10f, 0.30f) * lean
        val leadLength = rng.range(0.86f, 0.95f)
        val sideTurn = rng.range(0.62f, 1.08f) * spread
        val sideLength = rng.range(0.76f, 0.90f)
        val third = rng.next() < 0.5f
        val thirdTurn = rng.range(0.45f, 0.85f) * spread

        // why: a twig sprouts at half length, never as a stub — a stub's marks would all
        // sit on the fork it grows from.
        if (grown < 1f || depth >= TreeSkeleton.MAX_DEPTH || vigor < depth + 1.5f) {
            twigs += Twig(limbs.size - 1, path)
            return
        }
        // why: each generation shrinks little, so the first limbs start shorter — the
        // crown sits on its trunk rather than dwarfing it.
        val next = if (depth == 0) length * 0.8f else length
        // Each generation reaches a little further toward the light.
        val lifted = angle + (-PI_F / 2 - angle) * 0.06f * (depth + 1)
        limb(path * 4 + 1, end, lifted + leadTurn, next * leadLength, width * 0.80f, depth + 1, -side)
        limb(path * 4 + 2, end, lifted + side * sideTurn, next * sideLength, width * 0.60f, depth + 1, -side)
        if (third) {
            limb(path * 4 + 3, end, lifted - side * thirdTurn, next * 0.7f, width * 0.45f, depth + 1, side)
        }
    }
}

private class Twig(val limb: Int, val path: Long)

/**
 * [count] slots on the twigs. Each twig's first mark hangs at its end, where fruit and
 * blossom belong; after that the next goes to whichever twig holds the fewest for its
 * length, so no twig crowds while another stands bare. The sequence never depends on
 * [count], so hanging another word moves none already hanging.
 */
private fun hang(limbs: List<TreeLimb>, twigs: List<Twig>, seed: Long, count: Int): List<TreeSlot> {
    if (twigs.isEmpty() || count <= 0) return emptyList()
    // why: from a seeded first twig, each next is the one whose end lies farthest from every
    // end already dealt, so the first marks — fruit and blossom — land far apart instead of
    // clumping where neighboring twigs end.
    val rest = twigs.sortedBy { Mix.hash(seed xor it.path) }.toMutableList()
    val order = mutableListOf(rest.removeAt(0))
    fun gap(a: Int, b: Int): Float {
        val p = limbs[a].end; val q = limbs[b].end
        return hypot(p.x - q.x, p.y - q.y)
    }
    val nearest = rest.map { gap(it.limb, order[0].limb) }.toMutableList()
    while (rest.isNotEmpty()) {
        val far = nearest.indices.maxBy { nearest[it] }
        val picked = rest.removeAt(far)
        nearest.removeAt(far)
        order += picked
        for (i in rest.indices) nearest[i] = min(nearest[i], gap(rest[i].limb, picked.limb))
    }
    val wood = order.map { limbs[it.limb] }
    val lengths = wood.map { max(hypot(it.end.x - it.start.x, it.end.y - it.start.y), 1e-6f) }
    val held = IntArray(wood.size)
    val slots = mutableListOf<TreeSlot>()
    while (slots.size < count) {
        val twig = if (slots.size < wood.size) slots.size
            else held.indices.minBy { held[it] / lengths[it] }
        slots += slot(held[twig], wood[twig], flip = order[twig].path and 1L == 0L)
        held[twig]++
    }
    return slots
}

/**
 * A twig's k-th mark: the first at its end, the rest where the base-2 van der Corput
 * sequence puts them between there and a third of the way up, so each new one halves a
 * gap the earlier ones left and none sits at the fork the twig grew from. Off the end,
 * marks alternate sides and sit on the bark, leaning away from the twig.
 */
private fun slot(k: Int, twig: TreeLimb, flip: Boolean): TreeSlot {
    var spread = 0f; var step = 0.5f; var n = k
    while (n > 0) { spread += (n and 1) * step; n = n shr 1; step /= 2 }
    val t = 1 - 0.65f * spread; val u = 1 - t
    val point = Offset(
        u * u * twig.start.x + 2 * u * t * twig.control.x + t * t * twig.end.x,
        u * u * twig.start.y + 2 * u * t * twig.control.y + t * t * twig.end.y,
    )
    val along = atan2(
        2 * u * (twig.control.y - twig.start.y) + 2 * t * (twig.end.y - twig.control.y),
        2 * u * (twig.control.x - twig.start.x) + 2 * t * (twig.end.x - twig.control.x),
    )
    if (k == 0) return TreeSlot(point, along)
    val side = if ((k % 2 == 1) != flip) 1f else -1f
    val bark = (twig.startWidth * u + twig.endWidth * t) / 2 * side
    return TreeSlot(
        Offset(point.x + cos(along + PI_F / 2) * bark, point.y + sin(along + PI_F / 2) * bark),
        along + side * 0.9f,
    )
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
