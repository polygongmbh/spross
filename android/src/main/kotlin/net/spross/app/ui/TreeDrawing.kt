package net.spross.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import net.spross.kern.box.AreaGrowth
import net.spross.kern.design.AreaTree
import net.spross.kern.design.TreePicture

// One area's tree, as the Trees picture and the round summary both draw it:
// kern grows and draws it ([TreePicture]) in dp, from the tree's seed and its counts alone,
// so the same area stands as the same tree on Home and on the summary.

/** One tree standing somewhere: its foot in px, how tall in px, and its picture ready to draw. */
internal class PlantedTree(val tree: AreaGrowth, garden: String, val foot: Offset, height: Float, density: Float) {
    val art = TreeArt(
        TreePicture.of(tree, AreaTree.seed(garden, tree.area),
            foot.x / density.toDouble(), foot.y / density.toDouble(), height / density.toDouble()),
        density,
    )
}

/** Draws [planted]; [scale] sizes the marks at the ranks it holds while the summary's marks arrive. */
internal fun DrawScope.drawTree(planted: PlantedTree, colors: ThemeColors, scale: Map<Int, Float> = emptyMap()) =
    planted.art.draw(this, colors, scale)
