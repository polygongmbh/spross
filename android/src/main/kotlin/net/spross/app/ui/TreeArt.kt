package net.spross.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import net.spross.kern.box.AreaGrowth

// One placed tree as the paths it is filled with — built once, drawn on every frame.
//
// Which mark a word hangs as is kern's tier, read rank by rank off [AreaGrowth] (most grown
// first, so fruit and blossom take the first slots):
//   fruit    — long held      blossom — matured
//   leaf     — growing        bud     — arriving
// Told apart by shape before color: a sprig of pointed leaflets, a small disc, a spur of three
// pale flowers, a round disc hanging under its twig.

/** A leaf runs longer than the base a disc is cut to: it is the one mark meant to merge. */
private const val LEAF_STRETCH = 1.45f
private const val LEAF_WAIST = 0.27f
private const val BUD_RADIUS = 0.22f

/** The smallest a mark is cut, in dp, so a young crown's words stay legible. */
private const val MARK_FLOOR = 2.4f
/** Below this size (dp) each of a blossom's flowers is one disc: petals blur. */
private const val PLAIN = 12f
/** From this size (dp) on a blossom's flowers show their eyes. */
private const val EYED = 20f

internal class TreeArt(
    val wood: Path,
    val joints: Path,
    val shade: Path,
    val twigs: Path,
    /** Leaves in four tones, lit from above: the crown's top first, its underside last. */
    val tones: List<Path>,
    val buds: Path,
    /** A blossom's two lateral flowers, drawn under its king flower in [kings]. */
    val laterals: Path,
    val kings: Path,
    val eyes: Path,
    val fruit: Path,
    /** Pixels per dp. */
    private val unit: Float,
) {
    companion object {
        /**
         * The paths for [tree] hung on [skeleton] (already placed), [unit] px to the dp.
         * [scale] is how big the mark at a rank is drawn against its settled size — the
         * summary's arriving marks.
         */
        fun build(tree: AreaGrowth, skeleton: TreeSkeleton, unit: Float, scale: (Int) -> Float = { 1f }): TreeArt {
            val floor = MARK_FLOOR * unit
            // The mark size a crown of this pitch cuts to.
            val base = max(floor, skeleton.pitch * 0.85f)
            val art = TreeArt(Path(), Path(), Path(), Path(), List(4) { Path() },
                Path(), Path(), Path(), Path(), Path(), unit)
            art.wood(skeleton, floor, tree.met)
            art.canopy(tree, skeleton, base, scale)
            return art
        }
    }

    /** Only wood carrying one of the first [marks] shows, so no twig stands bare. */
    private fun wood(skeleton: TreeSkeleton, floor: Float, marks: Int) {
        val hairline = floor * 0.4f
        val carrying = HashSet<Int>()
        for (slot in skeleton.slots.take(marks)) {
            var next = slot.limb
            while (next >= 0 && carrying.add(next)) next = skeleton.limbs[next].parent
        }
        for ((index, limb) in skeleton.limbs.withIndex()) {
            if (index !in carrying) continue
            if (max(limb.startWidth, limb.endWidth) < hairline) {
                // A taper under a pixel collapses; these are stroked as hairlines instead.
                twigs.moveTo(limb.start.x, limb.start.y)
                twigs.quadraticTo(limb.control.x, limb.control.y, limb.end.x, limb.end.y)
                continue
            }
            taper(wood, limb, -1f, 1f)
            // why: the joints fill apart from the wood — a circle wound against a taper
            // cancels it under the nonzero rule and cuts a notch into the fork.
            joints.addOval(Rect(limb.end, limb.endWidth / 2))
            if (limb.depth == 0) taper(shade, limb, 0.3f, 1f)
        }
    }

    /** The band of [limb] between two edges, -1 one side … 1 the other, bowing with it. */
    private fun taper(path: Path, limb: TreeLimb, from: Float, to: Float) {
        val angle = atan2(limb.end.y - limb.start.y, limb.end.x - limb.start.x)
        val nx = cos(angle + PI_F / 2); val ny = sin(angle + PI_F / 2)
        fun edge(p: Offset, width: Float, side: Float) = Offset(p.x + nx * width / 2 * side, p.y + ny * width / 2 * side)
        val middle = (limb.startWidth + limb.endWidth) / 2
        val a = edge(limb.start, limb.startWidth, to); val c = edge(limb.control, middle, to)
        val b = edge(limb.end, limb.endWidth, to)
        val d = edge(limb.end, limb.endWidth, from); val e = edge(limb.control, middle, from)
        val f = edge(limb.start, limb.startWidth, from)
        path.moveTo(a.x, a.y); path.quadraticTo(c.x, c.y, b.x, b.y)
        path.lineTo(d.x, d.y); path.quadraticTo(e.x, e.y, f.x, f.y); path.close()
    }

    private fun canopy(tree: AreaGrowth, skeleton: TreeSkeleton, base: Float, scale: (Int) -> Float) {
        val hanging = skeleton.slots.take(tree.met)
        if (hanging.isEmpty()) return
        val top = hanging.minOf { it.point.y }
        val depth = max(hanging.maxOf { it.point.y } - top, 1f)
        val heavy = tree.longHeld + tree.matured
        val leafy = heavy + tree.growing
        for ((rank, slot) in hanging.withIndex()) {
            val grain = noise(tree.area, rank * 41 + 7)
            val reach = tree.reaches.getOrElse(rank) { 0.4 }.toFloat()
            // A mark's SIZE is its own word's standing; only its lean is hashed.
            val size = base * (0.74f + 0.62f * reach) * scale(rank)
            if (size <= 0.2f) continue
            val turned = slot.angle + (grain - 0.5f) * 0.9f
            val lean = atan2(sin(turned) - 0.2f, cos(turned))
            when {
                rank < tree.longHeld -> fruit(slot.point, size)
                rank < heavy -> blossom(slot.point, size, lean)
                rank < leafy -> {
                    val height = (slot.point.y - top) / depth
                    val tone = ((height * 0.75f + grain * 0.55f) * 3.2f - 0.2f).toInt().coerceIn(0, 3)
                    sprig(tones[tone], slot.point, size * LEAF_STRETCH, lean)
                }
                else -> buds.addOval(Rect(slot.point, size * BUD_RADIUS))
            }
        }
    }

    /** A landed word: three leaflets off one stalk — one mark, but foliage. */
    private fun sprig(path: Path, at: Offset, size: Float, angle: Float) {
        leaf(path, at, size, angle)
        val fork = Offset(at.x + cos(angle) * size * 0.28f, at.y + sin(angle) * size * 0.28f)
        leaf(path, fork, size * 0.66f, angle - 0.72f)
        leaf(path, fork, size * 0.6f, angle + 0.68f)
    }

    /**
     * A matured word: a spur of three flowers based on the slot, so the twig runs into it —
     * two laterals and the larger king flower beyond them, along the mark's lean.
     * Among the Trees each flower is one disc, and only large ones show an eye.
     */
    private fun blossom(at: Offset, size: Float, angle: Float) {
        val ux = cos(angle); val uy = sin(angle)
        fun flower(along: Float, across: Float, radius: Float, turn: Float, into: Path) {
            val center = Offset(at.x + (ux * along - uy * across) * size, at.y + (uy * along + ux * across) * size)
            val r = radius * size
            if (size < PLAIN * unit) return into.addOval(Rect(center, r))
            for (petal in 0 until 5) {
                val spin = turn + petal * 2 * PI_F / 5
                into.addOval(Rect(Offset(center.x + cos(spin) * r * 0.45f, center.y + sin(spin) * r * 0.45f), r * 0.55f))
            }
            if (size >= EYED * unit) eyes.addOval(Rect(center, r * 0.12f))
        }
        // why: each flower turned its own way, so the three do not look stamped.
        flower(0.12f, 0.20f, 0.17f, angle, laterals)
        flower(0.10f, -0.22f, 0.15f, angle + 1.3f, laterals)
        flower(0.30f, 0f, 0.22f, angle + 2.1f, kings)
    }

    /** A word held for months: one round fruit, its top on the slot, so it hangs under its wood. */
    private fun fruit(slot: Offset, size: Float) {
        fruit.addOval(Rect(Offset(slot.x, slot.y + size * 0.25f), size * 0.25f))
    }
}

/** A leaf from [at] outward along [angle], pointed at both ends. */
internal fun leaf(path: Path, at: Offset, size: Float, angle: Float) {
    val ux = cos(angle); val uy = sin(angle)
    val belly = size * LEAF_WAIST * 1.9f
    fun point(along: Float, across: Float) = Offset(at.x + ux * along - uy * across, at.y + uy * along + ux * across)
    val tip = point(size, 0f); val upper = point(size * 0.42f, -belly); val lower = point(size * 0.42f, belly)
    path.moveTo(at.x, at.y)
    path.quadraticTo(upper.x, upper.y, tip.x, tip.y)
    path.quadraticTo(lower.x, lower.y, at.x, at.y)
    path.close()
}

