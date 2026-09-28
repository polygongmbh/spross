package net.spross.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import net.spross.kern.box.AreaTree

// One placed tree as the paths it is filled with — built once, drawn on every frame.
//
// Which mark a word hangs as is kern's tier, read rank by rank off [AreaTree] (most grown
// first, so the twig ends fill first):
//   fruit    — long held      blossom — matured
//   leaf     — growing        bud     — arriving
// Told apart by shape before color: a sprig of pointed leaflets, a small disc, five petals
// round an eye, a larger disc on a stalk.

/** A leaf runs longer than the base a disc is cut to: it is the one mark meant to merge. */
private const val LEAF_STRETCH = 1.45f
private const val LEAF_WAIST = 0.27f
private const val BUD_RADIUS = 0.36f

internal class TreeArt(
    val wood: Path,
    val joints: Path,
    val shade: Path,
    val twigs: Path,
    val masses: Path,
    /** Leaves in four tones, lit from above: the crown's top first, its underside last. */
    val tones: List<Path>,
    val buds: Path,
    val petals: Path,
    val eyes: Path,
    val stalks: Path,
    val fruit: Path,
    val shine: Path,
) {
    companion object {
        /** The mark size a crown of this pitch cuts to; [floor] keeps a tiny crown legible. */
        fun base(pitch: Float, floor: Float) = max(floor, pitch * 0.85f)

        /**
         * The paths for [tree] hung on [skeleton] (already placed). [scale] is how big the
         * mark at a rank is drawn against its settled size — the summary's arriving marks.
         */
        fun build(tree: AreaTree, skeleton: TreeSkeleton, floor: Float, scale: (Int) -> Float = { 1f }): TreeArt {
            val art = TreeArt(Path(), Path(), Path(), Path(), Path(), List(4) { Path() },
                Path(), Path(), Path(), Path(), Path(), Path())
            art.wood(skeleton, floor)
            art.canopy(tree, skeleton, base(skeleton.pitch, floor), scale)
            return art
        }
    }

    private fun wood(skeleton: TreeSkeleton, floor: Float) {
        val hairline = floor * 0.4f
        for (limb in skeleton.limbs) {
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

    private fun canopy(tree: AreaTree, skeleton: TreeSkeleton, base: Float, scale: (Int) -> Float) {
        val hanging = skeleton.slots.take(tree.met)
        if (hanging.isEmpty()) return
        val top = hanging.minOf { it.point.y }
        val depth = max(hanging.maxOf { it.point.y } - top, 1f)
        mass(hanging, base)
        val heavy = tree.longHeld + tree.matured
        val leafy = heavy + tree.growing
        for ((rank, slot) in hanging.withIndex()) {
            val grain = Mix.noise(tree.area, rank * 41 + 7)
            val reach = tree.reaches.getOrElse(rank) { 0.4 }.toFloat()
            // A mark's SIZE is its own word's standing; only its lean is hashed.
            val size = base * (0.74f + 0.62f * reach) * scale(rank)
            if (size <= 0.2f) continue
            val turned = slot.angle + (grain - 0.5f) * 0.9f
            val lean = atan2(sin(turned) - 0.35f, cos(turned))
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

    /** A faint mass behind the marks, so foliage has depth rather than pinned leaves. */
    private fun mass(hanging: List<TreeSlot>, base: Float) {
        val points = hanging.map { it.point }
        val center = Offset(points.sumOf { it.x.toDouble() }.toFloat() / points.size,
            points.sumOf { it.y.toDouble() }.toFloat() / points.size)
        val far = max(points.maxOf { hypot(it.x - center.x, it.y - center.y) }, base * 0.5f) + base * 0.55f
        masses.addOval(Rect(center.x - far, center.y - far * 0.85f, center.x + far, center.y + far * 0.85f))
    }

    /** A landed word: three leaflets off one stalk — one mark, but foliage. */
    private fun sprig(path: Path, at: Offset, size: Float, angle: Float) {
        leaf(path, at, size, angle)
        val fork = Offset(at.x + cos(angle) * size * 0.28f, at.y + sin(angle) * size * 0.28f)
        leaf(path, fork, size * 0.66f, angle - 0.72f)
        leaf(path, fork, size * 0.6f, angle + 0.68f)
    }

    /** A matured word: five petals round an ochre eye, small enough to stay a tree in flower. */
    private fun blossom(at: Offset, size: Float, angle: Float) {
        val span = size * 0.9f
        for (petal in 0 until 5) {
            val turn = angle + petal * 2 * PI_F / 5
            petals.addOval(Rect(Offset(at.x + cos(turn) * span * 0.25f, at.y + sin(turn) * span * 0.25f), span * 0.22f))
        }
        eyes.addOval(Rect(at, span * 0.13f))
    }

    /** A word held for months — heavier than a blossom, so promotion reads as a gain. */
    private fun fruit(at: Offset, size: Float) {
        stalks.moveTo(at.x, at.y - size * 0.62f)
        stalks.quadraticTo(at.x + size * 0.12f, at.y - size * 0.45f, at.x, at.y - size * 0.26f)
        fruit.addOval(Rect(at, size * 0.56f))
        shine.addOval(Rect(Offset(at.x - size * 0.17f, at.y - size * 0.17f), size * 0.15f))
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

