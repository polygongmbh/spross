package net.spross.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.IEEErem
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// The wood of one tree and the slots its marks hang on, grown together — plain geometry, no drawing.
//
// The marks come first and the wood is whatever it takes to carry them (Weber & Penn 1995): a
// branch carrying n marks hands half of them to side branches along its length and the rest to
// the lead that continues from its end, until a branch carrying one mark is a leaf twig holding
// it at its tip. Width follows the pipe model, w ∝ √n, so r_parent² = Σ r_child².

/**
 * One length of wood: a bowed center line tapering from [startWidth] to [endWidth];
 * [parent] is the limb it grows from, -1 for the trunk.
 */
internal class TreeLimb(
    val start: Offset,
    val control: Offset,
    val end: Offset,
    val startWidth: Float,
    val endWidth: Float,
    val depth: Int,
    val parent: Int,
) {
    /** The point [t] of the way along the bowed center line. */
    fun point(t: Float): Offset {
        val u = 1 - t
        return Offset(u * u * start.x + 2 * u * t * control.x + t * t * end.x,
            u * u * start.y + 2 * u * t * control.y + t * t * end.y)
    }

    /** The direction the center line runs [t] of the way along. */
    fun heading(t: Float): Float {
        val u = 1 - t
        return atan2(u * (control.y - start.y) + t * (end.y - control.y), u * (control.x - start.x) + t * (end.x - control.x))
    }
}

/** Somewhere a mark hangs, facing [angle] (radians) outward from its twig, the [limb]-th. */
internal class TreeSlot(val point: Offset, val angle: Float, val limb: Int)

internal class TreeSkeleton(
    val limbs: List<TreeLimb>,
    /** One slot per mark, in rank order: the render hangs fruit first, then blossom, leaf, bud. */
    val slots: List<TreeSlot>,
    /** The side of the square each mark would get if the crown were shared out evenly. */
    val pitch: Float,
) {
    /** The same tree scaled by [scale] and moved so its unit origin lands on [foot]. */
    fun placed(foot: Offset, scale: Float): TreeSkeleton {
        fun at(p: Offset) = Offset(foot.x + p.x * scale, foot.y + p.y * scale)
        return TreeSkeleton(
            limbs.map {
                TreeLimb(at(it.start), at(it.control), at(it.end),
                    it.startWidth * scale, it.endWidth * scale, it.depth, it.parent)
            },
            slots.map { TreeSlot(at(it.point), it.angle, it.limb) },
            pitch * scale,
        )
    }

    /** The unit-space bounds of the wood and the marks. */
    fun bounds(): Rect {
        var left = 0f; var right = 0f; var top = 0f
        for (p in limbs.flatMap { listOf(it.start, it.control, it.end) } + slots.map { it.point }) {
            left = min(left, p.x); right = max(right, p.x); top = min(top, p.y)
        }
        return Rect(left, top, right, 0f)
    }

    companion object {
        /** Grows one tree carrying [marks] marks in unit space: foot at the origin, growing toward negative y. */
        fun grow(seed: Long, marks: Int): TreeSkeleton {
            if (marks <= 0) return TreeSkeleton(emptyList(), emptyList(), 0.05f)
            val growth = Growth(seed)
            growth.branch(marks, 1L, Offset.Zero, -PI_F / 2, 0, -1, 1f)
            // why: steep twigs rank last, so fruit and blossom hang on level wood.
            val shuffled = growth.leaves.sortedBy { Mix.hash(seed xor it.path) }
            val slots = (shuffled.filter { !it.steep } + shuffled.filter { it.steep }).map { it.slot }
            val skeleton = TreeSkeleton(growth.limbs, slots, 0f)
            return TreeSkeleton(growth.limbs, slots, pitch(slots, -skeleton.bounds().top))
        }

        /** Counted as at least eight marks, so a handful of words stay small. */
        private fun pitch(slots: List<TreeSlot>, height: Float): Float {
            val floor = height / 4
            val spread = max(floor, slots.maxOf { it.point.x } - slots.minOf { it.point.x })
            val rise = max(floor, slots.maxOf { it.point.y } - slots.minOf { it.point.y })
            return sqrt(spread * rise / max(8, slots.size))
        }
    }
}

internal const val PI_F = Math.PI.toFloat()

/** A leaf twig's slot, with the path that seeds its rank; [steep] if steeper than about 60°. */
private class Leaf(val slot: TreeSlot, val path: Long, val steep: Boolean)

private class Growth(val seed: Long) {
    val limbs = mutableListOf<TreeLimb>()
    val leaves = mutableListOf<Leaf>()

    // why: a branch may dip a little below horizontal, no further — a drooping limb hangs its
    // leaves under the crown.
    private fun clamped(heading: Float) = heading.coerceIn(-PI_F - 0.25f, 0.25f)

    /** One branch carrying [n] marks, and everything beyond it; [path] seeds everything about it. */
    fun branch(n: Int, path: Long, origin: Offset, angle: Float, depth: Int, parent: Int, side: Float) {
        val rng = Mix(seed xor Mix.hash(path))
        // why: a side branch starts out longer than a lead, so the crown spreads wider than it rises.
        val length = if (n == 1) 0.03f else 0.045f * (1 + 0.6f * ln(n.toFloat())) * if (path and 3L >= 2L) 1.6f else 1f
        val width = 0.0075f * sqrt(n.toFloat())
        val end = Offset(origin.x + cos(angle) * length, origin.y + sin(angle) * length)
        // why: every branch leaves its parent wide and arches back toward it.
        val bow = length * rng.range(0.04f, 0.10f) * side
        val control = Offset(
            (origin.x + end.x) / 2 + cos(angle + PI_F / 2) * bow,
            (origin.y + end.y) / 2 + sin(angle + PI_F / 2) * bow,
        )
        // The trunk flares where it meets the ground.
        val limb = TreeLimb(origin, control, end, if (depth == 0) width * 1.3f else width, width * 0.8f, depth, parent)
        limbs += limb
        val index = limbs.size - 1

        // why: measured from this branch's own heading, so a left-leaning tangent never wraps past ±π.
        fun along(t: Float) = angle + (limb.heading(t) - angle).IEEErem(2 * PI_F)
        if (n == 1) {
            // why: a leaf follows its twig, splayed to one side, and never points below horizontal.
            val slot = TreeSlot(end, (along(1f) + side * 0.9f).coerceIn(-PI_F + 0.3f, -0.3f), index)
            leaves += Leaf(slot, path, abs(end.x - origin.x) < 0.6f * abs(end.y - origin.y))
            return
        }
        // why: limbs sag under their weight, the more level and the later-born the further.
        val sag = 0.02f * (depth + 1) * cos(angle)
        val r = n / 2
        val sides = if (r == 1) listOf(r to 0.7f) else listOf((r + 1) / 2 to 0.55f, r / 2 to 0.8f)
        val ways = listOf(side, -side)
        val headings = sides.mapIndexed { k, (_, t) -> clamped(along(t) + ways[k] * rng.range(1.05f, 1.40f) + sag) }
        // The lead bends a little away from the first side branch.
        var lead = clamped(angle - side * rng.range(0.05f, 0.20f) + sag)
        // why: a side branch held up by the clamp would run along the lead, so the lead gives way.
        if (headings.any { abs(it - lead) < 0.45f }) {
            lead = if (headings.size == 2) (headings[0] + headings[1]) / 2 else clamped(headings[0] - side * 0.45f)
        }
        sides.forEachIndexed { k, (count, t) ->
            branch(count, path * 4 + 2 + k, limb.point(t), headings[k], depth + 1, index, ways[k])
        }
        branch(n - r, path * 4 + 1, end, lead, depth + 1, index, -side)
    }
}

/** SplitMix64: a stable stream per seed, the same on every run and every device. */
internal class Mix(private var state: Long) {
    fun next(): Float = ((hash(state.also { state += GOLDEN }) ushr 40).toFloat() / (1 shl 24))
    fun range(low: Float, high: Float) = low + (high - low) * next()

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
