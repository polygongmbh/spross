package net.spross.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max
import kotlin.math.min
import net.spross.kern.box.AreaTree
import net.spross.kern.design.TreeFit
import net.spross.kern.design.TreeLayout

// One area's tree, as the Trees picture and the round summary both draw it.
//
// The tree is one organism its whole life: a seedling thickens into a trunk, the words that
// landed fill the crown, and blossom and fruit appear ON it rather than replacing it. The
// skeleton comes from the area's name and its met count alone, so the same area stands as
// the same tree on Home and on the summary.

/** One tree standing somewhere: its foot, how tall, and the wood grown to that height. */
internal class PlantedTree(val tree: AreaTree, val foot: Offset, val height: Float, density: Float) {
    /** Pixels per dp. */
    val unit = 1f * density

    val skeleton: TreeSkeleton? = if (tree.met == 0 || height <= 0f) null else fitted()

    /** The settled paths — every mark at full size, which is everything but a summary's rise. */
    val art: TreeArt? by lazy { skeleton?.let { TreeArt.build(tree, it, unit) } }

    /** Grown and fitted in dp by kern, then scaled to pixels. */
    private fun fitted(): TreeSkeleton {
        val grown = TreeLayout.grow(tree.area, tree.met)
        val fit = grown.fit(foot.x / unit.toDouble(), foot.y / unit.toDouble(), height / unit.toDouble())
        return TreeSkeleton.placed(grown, TreeFit(fit.x * unit, fit.y * unit, fit.scale * unit))
    }

    fun dp(value: Float) = value * unit
}

/** Draws [planted]; [art] replaces the settled paths while the summary's marks arrive. */
internal fun DrawScope.drawTree(planted: PlantedTree, colors: ThemeColors, art: TreeArt? = planted.art) {
    val tree = planted.tree
    ground(planted, colors)
    when {
        // why: an area nobody has opened stands as a faded seedling — a place to go
        // rather than a chore not done.
        tree.isBare -> seedling(planted, max(planted.height, planted.dp(TreeLayout.MIN_HEIGHT.toFloat())),
            colors.success.copy(alpha = 0.45f))
        art == null -> seedling(planted, planted.height, colors.success)
        else -> {
            wood(art, colors, planted)
            canopy(art, colors)
        }
    }
    fallen(planted, colors)
    // Answered today: fresh earth at the foot — tended, which says nothing about growth.
    if (tree.answeredToday) {
        val half = max(planted.dp(6f), planted.height * 0.14f)
        val y = planted.foot.y + planted.dp(3.5f)
        drawLine(colors.accent, Offset(planted.foot.x - half, y), Offset(planted.foot.x + half, y),
            strokeWidth = max(planted.dp(1.6f), planted.height * 0.03f), cap = StrokeCap.Round)
    }
}

/** What the tree stands on: a soft shadow under the trunk, never a line. */
private fun DrawScope.ground(planted: PlantedTree, colors: ThemeColors) {
    val width = max(planted.dp(9f), planted.height * 0.42f)
    val foot = planted.foot
    drawOval(colors.separator.copy(alpha = 0.55f), Offset(foot.x - width / 2, foot.y - planted.dp(1.6f)),
        Size(width, planted.dp(3.2f)))
}

/** Nothing met yet: a stem and two leaflets. */
private fun DrawScope.seedling(planted: PlantedTree, height: Float, color: Color) {
    val foot = planted.foot
    val top = Offset(foot.x, foot.y - height)
    val stem = Path().apply {
        moveTo(foot.x, foot.y)
        quadraticTo(foot.x + height * 0.08f, foot.y - height * 0.5f, top.x, top.y)
    }
    drawPath(stem, color, style = Stroke(max(planted.dp(1.4f), height * 0.055f), cap = StrokeCap.Round))
    val size = max(planted.dp(4f), height * 0.34f)
    val leaves = Path()
    leaf(leaves, top, size, -0.7f)
    leaf(leaves, top, size * 0.85f, PI_F + 0.7f)
    drawPath(leaves, color)
}

/** The wood in bark, the trunk's shaded side lit from the upper left, twigs as hairlines. */
private fun DrawScope.wood(art: TreeArt, colors: ThemeColors, planted: PlantedTree) {
    drawPath(art.wood, colors.borderStrong)
    drawPath(art.joints, colors.borderStrong)
    drawPath(art.shade, colors.textPrimary.copy(alpha = 0.08f))
    drawPath(art.twigs, colors.borderStrong, style = Stroke(planted.dp(0.7f), cap = StrokeCap.Round))
}

private fun DrawScope.canopy(art: TreeArt, colors: ThemeColors) {
    // Fruit under the leaves, buds and blossom on top.
    drawPath(art.stalks, colors.borderStrong, style = Stroke(art.stalkWidth, cap = StrokeCap.Round))
    drawPath(art.cherries, colors.fruit)
    val tones = listOf(colors.das.copy(alpha = 0.92f), colors.success,
        colors.success.copy(alpha = 0.84f), colors.success.copy(alpha = 0.68f))
    art.tones.forEachIndexed { index, path -> drawPath(path, tones[index]) }
    // Ochre, not green: a bud is a scale of wood, the word has not leafed out yet.
    drawPath(art.buds, colors.amber.copy(alpha = 0.8f))
    drawPath(art.petals, colors.blossom)
    drawPath(art.eyes, colors.amber.copy(alpha = 0.5f))
}

/** Words that lapsed lie on the ground beside the trunk; the tree never shrinks for them. */
private fun DrawScope.fallen(planted: PlantedTree, colors: ThemeColors) {
    if (planted.tree.lapsed == 0) return
    val clear = max(planted.dp(7f), planted.height * 0.2f)
    val size = max(planted.dp(3f), planted.height * 0.055f)
    val leaves = Path()
    for (index in 0 until min(planted.tree.lapsed, 3)) {
        val side = if (index % 2 == 0) -1f else 1f
        val spread = clear + noise(planted.tree.area, 13 + index) * clear * 0.5f
        leaf(leaves, Offset(planted.foot.x + side * spread, planted.foot.y + planted.dp(0.5f)), size,
            if (side > 0) 0.2f else PI_F - 0.2f)
    }
    drawPath(leaves, colors.amber.copy(alpha = 0.85f))
}

