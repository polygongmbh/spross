package net.spross.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
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
// Leaves are the level past the last stems (Weber & Penn §4.6): they grow along every limb
// but the trunk, spread evenly and alternating sides, clear of each fork.

/**
 * One length of wood: a bowed center line tapering from [startWidth] to [endWidth];
 * [tip] if it is a twig that forks no further, drawn only once a mark hangs on it.
 */
internal class TreeLimb(
    val start: Offset,
    val control: Offset,
    val end: Offset,
    val startWidth: Float,
    val endWidth: Float,
    val depth: Int,
    val tip: Boolean,
)

/** Somewhere a mark hangs, facing [angle] (radians) outward from its limb, the [limb]-th. */
internal class TreeSlot(val point: Offset, val angle: Float, val limb: Int)

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
                    it.startWidth * scale, it.endWidth * scale, it.depth, it.tip)
            },
            slots.map { TreeSlot(at(it.point), it.angle, it.limb) },
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
            val trunk = habit.range(0.13f, 0.19f)
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
    /** The wood marks hang on: every limb but the trunk. */
    val twigs = mutableListOf<Twig>()

    fun limb(path: Long, origin: Offset, heading: Float, length: Float, width: Float, depth: Int, side: Float) {
        val grown = (vigor - depth).coerceIn(0f, 1f)
        if (grown <= 0f) return
        // why: a limb may dip a little below horizontal, no further — turns add up over the
        // generations, and a drooping limb hangs its leaves under the crown.
        val angle = heading.coerceIn(-PI_F - 0.25f, 0.25f)
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
        // why: a twig sprouts at half length, never as a stub — a stub's marks would all
        // sit on the fork it grows from.
        val forks = grown >= 1f && depth < TreeSkeleton.MAX_DEPTH && vigor >= depth + 1.5f
        limbs += TreeLimb(origin, control, end, if (depth == 0) width * 1.3f else width, endWidth, depth,
            tip = depth >= 1 && !forks)

        // why: drawn in full even for a limb that forks no further, so the sequence — and
        // with it every child's shape — never depends on how deep the tree has grown.
        val lean = rng.sign()
        // why: the lead bends little and the others turn well away, so no two siblings part at
        // less than about 25° and run side by side.
        val leadTurn = rng.range(0.05f, 0.20f) * lean
        val leadLength = rng.range(0.86f, 0.95f)
        val sideTurn = rng.range(0.75f, 1.10f) * spread
        val sideLength = rng.range(0.76f, 0.90f)
        // why: the trunk always forks three ways, so the crown has low limbs on both sides.
        val third = rng.next() < 0.5f || depth == 0
        val thirdTurn = rng.range(0.80f, 1.10f) * spread

        if (depth >= 1) twigs += Twig(limbs.size - 1, path, !forks)
        if (!forks) return
        // why: a short trunk under long first limbs — a low, bushy crown that fits an
        // orchard row instead of a tall stem with a tuft on top.
        val next = if (depth == 0) length * 1.2f else length
        // why: limbs sag under their weight, the more level and the later-born the further.
        val sagged = angle + 0.02f * (depth + 1) * cos(angle)
        limb(path * 4 + 1, end, sagged + leadTurn, next * leadLength, width * 0.80f, depth + 1, -side)
        limb(path * 4 + 2, end, sagged + side * sideTurn, next * sideLength, width * 0.60f, depth + 1, -side)
        if (third) {
            limb(path * 4 + 3, end, sagged - side * thirdTurn, next * 0.7f, width * 0.45f, depth + 1, side)
        }
    }
}

/** A limb marks hang on, with the path that seeds it; [tip] if it forks no further. */
private class Twig(val limb: Int, val path: Long, val tip: Boolean)

/**
 * [count] slots on the wood, the levelest and oldest wood dealt first so the first marks,
 * fruit and blossom, hang as spur fruit does — but never within reach of a mark already dealt
 * while wood further off is free, the reach shrinking as the crown fills, so no two fruit
 * touch and the flowers spread over the whole crown. Once every carrier holds one, the next
 * goes to whichever holds the fewest for its weighted length. The sequence never depends on
 * [count], so hanging another word moves none already hanging.
 */
private fun hang(limbs: List<TreeLimb>, twigs: List<Twig>, seed: Long, count: Int): List<TreeSlot> {
    if (count <= 0 || twigs.isEmpty()) return emptyList()
    fun chord(twig: Twig): Float {
        val limb = limbs[twig.limb]
        return max(hypot(limb.end.x - limb.start.x, limb.end.y - limb.start.y), 1e-6f)
    }
    fun rank(twig: Twig): Float {
        val limb = limbs[twig.limb]
        return abs(limb.end.x - limb.start.x) / chord(twig) + if (twig.tip) 0f else 1f
    }
    fun first(twig: Twig) = slot(0, twig.limb, limbs, flip = twig.path and 1L == 0L).point
    val pending = twigs.sortedWith(compareByDescending<Twig> { rank(it) }.thenBy { Mix.hash(seed xor it.path) })
        .map { it to first(it) }.toMutableList()
    var reach = 2 * twigs.sumOf { chord(it).toDouble() }.toFloat() / twigs.size
    val dealt = mutableListOf<Offset>()
    val order = mutableListOf<Twig>()
    while (pending.isNotEmpty()) {
        val free = pending.indexOfFirst { (_, at) -> dealt.all { (it - at).getDistance() >= reach } }
        val i = if (free >= 0) free else if (reach < 1e-6f) 0 else { reach *= 0.8f; continue }
        val (twig, at) = pending.removeAt(i)
        dealt += at
        order += twig
    }
    // why: inner wood counts half its length, so the twigs carry most of the foliage.
    val weights = order.map { chord(it) * if (it.tip) 1f else 0.5f }
    val held = IntArray(order.size)
    val slots = mutableListOf<TreeSlot>()
    while (slots.size < count) {
        val i = if (slots.size < order.size) slots.size else held.indices.minBy { held[it] / weights[it] }
        slots += slot(held[i], order[i].limb, limbs, flip = order[i].path and 1L == 0L)
        held[i]++
    }
    return slots
}

/**
 * A carrier's k-th mark, where the base-2 van der Corput sequence puts it within [0.15, 0.95]
 * of the way along: each new mark halves a gap the earlier ones left. Marks alternate sides
 * and sit on the bark, leaning away from the wood.
 */
private fun slot(k: Int, limb: Int, limbs: List<TreeLimb>, flip: Boolean): TreeSlot {
    val twig = limbs[limb]
    var spread = 0f; var step = 0.5f; var n = k
    while (n > 0) { spread += (n and 1) * step; n = n shr 1; step /= 2 }
    val t = 0.95f - 0.8f * spread; val u = 1 - t
    val point = Offset(
        u * u * twig.start.x + 2 * u * t * twig.control.x + t * t * twig.end.x,
        u * u * twig.start.y + 2 * u * t * twig.control.y + t * t * twig.end.y,
    )
    val along = atan2(
        2 * u * (twig.control.y - twig.start.y) + 2 * t * (twig.end.y - twig.control.y),
        2 * u * (twig.control.x - twig.start.x) + 2 * t * (twig.end.x - twig.control.x),
    )
    val side = if ((k % 2 == 1) != flip) 1f else -1f
    val bark = (twig.startWidth * u + twig.endWidth * t) / 2 * side
    // why: a leaf follows its wood, splayed to one side, and never points below horizontal.
    return TreeSlot(
        Offset(point.x + cos(along + PI_F / 2) * bark, point.y + sin(along + PI_F / 2) * bark),
        (along + side * 0.9f).coerceIn(-PI_F + 0.3f, -0.3f),
        limb,
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
