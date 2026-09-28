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
// Marks are dart-thrown over the forked wood (Bridson 2007's Poisson-disk sampling): a
// candidate lands uniformly along the limbs, and is kept only if it clears every mark already
// kept by a minimum spacing, measured in the plane — so no two marks overlap wherever the
// limbs cross or crowd.

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
            // A few spare slots past the last word, so a prefix never runs short.
            val hung = hang(growth.limbs, seed, slots + 6)
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
        val leadLength = rng.range(0.80f, 0.92f)
        val sideTurn = rng.range(0.62f, 1.08f) * spread
        val sideLength = rng.range(0.66f, 0.82f)
        val third = depth <= 2 && rng.next() < 0.32f
        val thirdTurn = rng.range(0.45f, 0.85f) * spread

        if (grown < 1f || depth >= TreeSkeleton.MAX_DEPTH || vigor <= depth + 1) return
        // Each generation reaches a little further toward the light.
        val lifted = angle + (-PI_F / 2 - angle) * 0.06f * (depth + 1)
        limb(path * 4 + 1, end, lifted + leadTurn, length * leadLength, width * 0.80f, depth + 1, -side)
        limb(path * 4 + 2, end, lifted + side * sideTurn, length * sideLength, width * 0.60f, depth + 1, -side)
        if (third) {
            limb(path * 4 + 3, end, lifted - side * thirdTurn, length * 0.55f, width * 0.45f, depth + 1, side)
        }
    }
}

/** [count] slots over the forked wood, in the order they were accepted. */
private fun hang(limbs: List<TreeLimb>, seed: Long, count: Int): List<TreeSlot> {
    // why: the trunk stays bare below the first fork, as in any tree's structure.
    val wood = limbs.filter { it.depth >= 1 }.ifEmpty { limbs }
    val lengths = wood.map { hypot(it.end.x - it.start.x, it.end.y - it.start.y) }
    val total = lengths.sum()
    if (total <= 0f || count <= 0) return emptyList()
    val rng = Mix(seed xor Mix.hash(0x5EED1EAFL))
    // why: set by the wood alone, never by [count], so a longer run replays a shorter
    // one exactly and only appends — adding a word moves no mark already hanging.
    var spacing = total / 8

    // Uniform along the wood: a limb picked by its length, then a point along it,
    // nudged off the center line to either side.
    fun candidate(): TreeSlot {
        var pick = rng.next() * total
        var index = 0
        while (index < wood.size - 1 && pick > lengths[index]) {
            pick -= lengths[index]
            index++
        }
        val limb = wood[index]
        val along = rng.next()
        val offset = (rng.next() * 2 - 1) * 0.6f * spacing
        val turn = rng.range(0.6f, 1.4f)
        val angle = atan2(limb.end.y - limb.start.y, limb.end.x - limb.start.x)
        val point = Offset(
            limb.start.x + (limb.end.x - limb.start.x) * along + cos(angle + PI_F / 2) * offset,
            limb.start.y + (limb.end.y - limb.start.y) * along + sin(angle + PI_F / 2) * offset,
        )
        return TreeSlot(point, angle + if (offset < 0f) -turn else turn)
    }

    // why: the trunk counts as taken, so no mark sits at the fork on top of it.
    val trunk = limbs.firstOrNull { it.depth == 0 }
    val slots = mutableListOf<TreeSlot>()
    // why: a spacing the wood can no longer fit shrinks rather than stalls, so the
    // first marks spread over the whole crown and later ones fill the gaps between.
    for (epoch in 0 until 14) {
        if (slots.size >= count) break
        var misses = 0
        while (misses < 60 && slots.size < count) {
            val slot = candidate()
            if (clearance(slot.point, trunk) >= spacing &&
                slots.all { hypot(it.point.x - slot.point.x, it.point.y - slot.point.y) >= spacing }
            ) {
                slots += slot
                misses = 0
            } else {
                misses++
            }
        }
        spacing *= 0.65f
    }
    while (slots.size < count) slots += candidate()
    return slots
}

/** How far [point] stands from the straight line of [limb]. */
private fun clearance(point: Offset, limb: TreeLimb?): Float {
    if (limb == null) return Float.POSITIVE_INFINITY
    val dx = limb.end.x - limb.start.x; val dy = limb.end.y - limb.start.y
    val t = (((point.x - limb.start.x) * dx + (point.y - limb.start.y) * dy) / max(dx * dx + dy * dy, 1e-9f)).coerceIn(0f, 1f)
    return hypot(point.x - limb.start.x - dx * t, point.y - limb.start.y - dy * t)
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
