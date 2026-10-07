package net.spross.kern.design

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// The wood and the marks of a tree that carries something, placed in points.

/** The smallest a mark is cut, so a young crown's words stay legible. */
private const val MARK_FLOOR = 2.4
/** Wood thinner than this is a hairline: a filled taper that fine collapses. */
private const val HAIRLINE = 0.9
private const val TWIG_STROKE = 0.7
/** The trunk takes a shaded side only once it is wide enough to show one. */
private const val SHADED_TRUNK = 2.4

/** A leaf runs longer than the base a disc is cut to: it is the one mark meant to merge with its neighbors. */
private const val LEAF_STRETCH = 1.45
private const val LEAF_WAIST = 0.27
private const val BUD_RADIUS = 0.11
/** The smallest a fruit's radius is drawn, so a small tree's fruit stays visible. */
private const val FRUIT_FLOOR = 1.6
/** Below this size a blossom's petals are one disc: they blur. */
private const val PLAIN_BLOSSOM = 12.0
private const val GRAIN = 0x6c656166L

/** Every limb carrying one of [GrownTree.slots], and the wood under it down to the trunk: only these are drawn, so no twig stands bare. */
internal fun GrownTree.carrying(): Set<Int> {
    val carrying = HashSet<Int>()
    for (slot in slots) {
        var next = slot.limb
        while (next >= 0 && carrying.add(next)) next = limbs[next].parent
    }
    return carrying
}

/** How big a mark is drawn on a crown cut to [base]: its own word's [strength], 0…1. */
internal fun markSize(base: Double, strength: Double) = base * (0.74 + 0.62 * strength)

internal fun Painter.crown() {
    val grown = AreaTree.grow(seed, tree.met, tree.stages.fresh)
    val fit = grown.fit(footX, footY, height)
    wood(grown, fit)
    marks(grown, fit)
}

/**
 * Every branch filled as one tapering path, its joints rounded so a fork reads as grown rather than glued;
 * the trunk carries a shaded side, light from the upper left, which gives the wood a body.
 */
private fun Painter.wood(grown: GrownTree, fit: TreeFit) {
    val carrying = grown.carrying()
    val wood = Pen(); val joints = Pen(); val shade = Pen(); val twigs = Pen()
    for ((index, unit) in grown.limbs.withIndex()) {
        if (index !in carrying) continue
        val limb = unit.placed(fit)
        val width = max(limb.startWidth, limb.endWidth)
        if (width < HAIRLINE) {
            twigs.move(limb.startX, limb.startY)
            twigs.quad(limb.controlX, limb.controlY, limb.endX, limb.endY)
            continue
        }
        wood.taper(limb, -1.0, 1.0)
        joints.circle(limb.endX, limb.endY, limb.endWidth / 2)
        if (limb.depth == 0 && width >= SHADED_TRUNK) shade.taper(limb, 0.3, 1.0)
    }
    layer(TreeInk.WOOD, 1.0, wood)
    // why: the joints fill apart from the wood — a circle wound against a taper
    // cancels it under the nonzero rule and cuts a notch into the fork.
    layer(TreeInk.WOOD, 1.0, joints)
    layer(TreeInk.WOOD_SHADE, 0.08, shade)
    layer(TreeInk.WOOD, 1.0, twigs, stroke = TWIG_STROKE)
}

/**
 * The marks along the twigs: buds and fruit under the leaves, which take four tones lit from above,
 * then blossom on top, its eye in the wood's tone.
 */
private fun Painter.marks(grown: GrownTree, fit: TreeFit) {
    val slots = grown.slots
    val top = fit.y + slots.minOf { it.y } * fit.scale
    val depth = max((slots.maxOf { it.y } - slots.minOf { it.y }) * fit.scale, 1.0)
    val base = max(MARK_FLOOR, grown.pitch * fit.scale * 0.85)
    val buds = mutableListOf<TreeShape>(); val fruit = mutableListOf<TreeShape>()
    val tones = List(4) { mutableListOf<TreeShape>() }
    val petals = mutableListOf<TreeShape>(); val eyes = mutableListOf<TreeShape>()
    for ((rank, slot) in slots.withIndex()) {
        val x = fit.x + slot.x * fit.scale
        val y = fit.y + slot.y * fit.scale
        fun shape(draw: Pen.() -> Unit) = TreeShape(rank, x, y, Pen().apply(draw).done())
        val size = markSize(base, tree.strengths.getOrElse(rank) { 0.4 })
        when (tree.stageAt(rank)) {
            1 -> fruit += shape {
                // Its top on the slot, so it hangs under its wood.
                val radius = max(size * 0.4, FRUIT_FLOOR)
                circle(x, y + radius, radius)
            }
            2 -> {
                val span = size * 0.9
                petals += shape { if (size < PLAIN_BLOSSOM) circle(x, y, span * 0.42) else petals(x, y, span, slot.angle) }
                eyes += shape { circle(x, y, span * 0.13) }
            }
            3 -> {
                // why: lit from above — the crown's upper leaves take the light tones, its lower and inner ones
                // the deep, with a seeded nudge so the tones fall in patches rather than bands.
                val tone = (((y - top) / depth * 0.75 + draw(GRAIN, rank) * 0.55) * 3.2 - 0.2).toInt().coerceIn(0, 3)
                tones[3 - tone] += shape { sprig(x, y, size * LEAF_STRETCH, slot.angle) }
            }
            else -> buds += shape { circle(x, y, size * BUD_RADIUS) }
        }
    }
    // Ochre, not green: a bud is a scale of wood, the word has not leafed out yet.
    layer(TreeInk.BUD, 0.8, buds)
    layer(TreeInk.FRUIT, 1.0, fruit)
    layer(TreeInk.LEAF_DEEP, 0.92, tones[0])
    layer(TreeInk.LEAF, 1.0, tones[1])
    layer(TreeInk.LEAF, 0.84, tones[2])
    layer(TreeInk.LEAF, 0.68, tones[3])
    layer(TreeInk.BLOSSOM, 0.9, petals)
    // why: the eye takes the wood's tone; ochre sits too close to the petals.
    layer(TreeInk.WOOD, 1.0, eyes)
}

private fun TreeLimb.placed(fit: TreeFit) = TreeLimb(
    fit.x + startX * fit.scale, fit.y + startY * fit.scale,
    fit.x + controlX * fit.scale, fit.y + controlY * fit.scale,
    fit.x + endX * fit.scale, fit.y + endY * fit.scale,
    startWidth * fit.scale, endWidth * fit.scale, depth, parent,
)

/** The band of [limb] between two edges, −1 one side … 1 the other: both bow with its center line. */
private fun Pen.taper(limb: TreeLimb, from: Double, to: Double) {
    val angle = atan2(limb.endY - limb.startY, limb.endX - limb.startX)
    val nx = cos(angle + PI / 2); val ny = sin(angle + PI / 2)
    val middle = (limb.startWidth + limb.endWidth) / 2
    fun edgeX(x: Double, width: Double, side: Double) = x + nx * width / 2 * side
    fun edgeY(y: Double, width: Double, side: Double) = y + ny * width / 2 * side
    move(edgeX(limb.startX, limb.startWidth, to), edgeY(limb.startY, limb.startWidth, to))
    quad(edgeX(limb.controlX, middle, to), edgeY(limb.controlY, middle, to),
        edgeX(limb.endX, limb.endWidth, to), edgeY(limb.endY, limb.endWidth, to))
    line(edgeX(limb.endX, limb.endWidth, from), edgeY(limb.endY, limb.endWidth, from))
    quad(edgeX(limb.controlX, middle, from), edgeY(limb.controlY, middle, from),
        edgeX(limb.startX, limb.startWidth, from), edgeY(limb.startY, limb.startWidth, from))
    close()
}

/** A leaf from ([x], [y]) outward along [angle]: pointed at both ends, broadest a little before its middle. */
internal fun Pen.leaf(x: Double, y: Double, size: Double, angle: Double) {
    val ux = cos(angle); val uy = sin(angle)
    val belly = size * LEAF_WAIST * 1.9
    fun px(along: Double, across: Double) = x + ux * along - uy * across
    fun py(along: Double, across: Double) = y + uy * along + ux * across
    move(x, y)
    quad(px(size * 0.42, -belly), py(size * 0.42, -belly), px(size, 0.0), py(size, 0.0))
    quad(px(size * 0.42, belly), py(size * 0.42, belly), x, y)
    close()
}

/** A landed word: three leaflets off one stalk — one mark, but foliage rather than a stamp. */
private fun Pen.sprig(x: Double, y: Double, size: Double, angle: Double) {
    leaf(x, y, size, angle)
    val forkX = x + cos(angle) * size * 0.28
    val forkY = y + sin(angle) * size * 0.28
    // why: the leaflets part narrowly and keep to the leaf's rule, so none points back at the wood or hangs.
    leaf(forkX, forkY, size * 0.66, level(angle - 0.45))
    leaf(forkX, forkY, size * 0.6, level(angle + 0.42))
}

/** A settled word: five petals round where its eye goes, kept small — forty of them are still a tree in flower. */
private fun Pen.petals(x: Double, y: Double, span: Double, angle: Double) {
    for (petal in 0 until 5) {
        val turn = angle + petal * 2 * PI / 5
        circle(x + cos(turn) * span * 0.25, y + sin(turn) * span * 0.25, span * 0.22)
    }
}

