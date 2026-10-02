package net.spross.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import net.spross.app.AppModel
import net.spross.app.areaEmoji
import net.spross.app.areaTitle
import net.spross.app.countLine
import net.spross.app.composedAreaGrowth
import net.spross.app.openBox
import net.spross.kern.box.AreaGrowth
import net.spross.kern.design.TreesLayout

/**
 * The bottom of Home: the Trees picture, and the standing split in words under it.
 * A picture of the box, not a way around it — a tree opens the box at its own area.
 */
@Composable
internal fun HomeTrees(model: AppModel) {
    val stats = model.stats
    val trees = remember(model.box, stats, model.catalog) { model.composedAreaGrowth() }
    if (trees.isEmpty()) return
    val chrome = model.chrome
    val areas = remember(stats) { stats?.areas?.associateBy { it.name }.orEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        Trees(
            trees = trees,
            emoji = model::areaEmoji,
            describe = { tree ->
                val area = areas[tree.area]
                listOf(
                    model.areaTitle(tree.area),
                    countLine(chrome.progressConsolidatedCountOne, chrome.progressConsolidatedCount, area?.consolidated ?: 0),
                    countLine(chrome.progressLearningCountOne, chrome.progressLearningCount, area?.learning ?: 0),
                ).joinToString(", ")
            },
            open = { model.openBox(it) },
        )
        Text(
            listOf(
                countLine(chrome.progressConsolidatedCountOne, chrome.progressConsolidatedCount, stats?.consolidatedCount ?: 0),
                countLine(chrome.progressLearningCountOne, chrome.progressLearningCount, stats?.learningCount ?: 0),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = Theme.colors.textSecondary,
        )
    }
}

/**
 * The box as one picture: a tree per area, standing in rows on shared ground.
 *
 * [trees] come in the order they stand in, from kern ([AreaGrowth] per area), and kern
 * places them ([TreesLayout]); this only scales and draws them. One Canvas for every tree,
 * grown once per layout, and nothing moves: a box grows over weeks, and motion would claim
 * a change the picture is not showing.
 *
 * The canvas says nothing to TalkBack; each tree carries a button on its own cell,
 * spoken as [describe] words it.
 */
@Composable
internal fun Trees(
    trees: List<AreaGrowth>,
    emoji: (String) -> String,
    describe: (AreaGrowth) -> String,
    open: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val spots = remember(trees, width, density.density) { place(trees, width, density.density) }
        val colors = Theme.colors
        val measurer = rememberTextMeasurer()
        val labels = remember(trees, measurer) {
            trees.associate { it.area to measurer.measure(emoji(it.area), TextStyle(fontSize = 13.sp)) } // card-parity: a mark under a 58 dp cell, below every type role
        }
        val height = spots.maxOfOrNull { it.cell.bottom } ?: 0f
        Box(Modifier.fillMaxWidth().height(with(density) { height.toDp() })) {
            Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
                // why: each label is drawn WITH its own tree, in the one back-to-front order,
                // so a tree standing in front of an area is never labeled through.
                for (spot in spots) {
                    drawTree(spot.planted, colors)
                    val label = labels[spot.planted.tree.area] ?: continue
                    val center = Offset(spot.planted.foot.x, spot.planted.foot.y + TreesLayout.LABEL_HEIGHT.toFloat() * density.density / 2)
                    drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y - label.size.height / 2f),
                        alpha = if (spot.planted.tree.isBare) 0.4f else 1f)
                }
            }
            for (spot in spots) {
                val tree = spot.planted.tree
                Box(
                    Modifier
                        .offset { IntOffset(spot.cell.left.roundToInt(), spot.cell.top.roundToInt()) }
                        .size(with(density) { spot.cell.width.toDp() }, with(density) { spot.cell.height.toDp() })
                        .clickable(role = Role.Button) { open(tree.area) }
                        .semantics { contentDescription = describe(tree) },
                )
            }
        }
    }
}

/** One tree placed among the others, and the cell its label and tap target fill. */
private class TreeCell(val planted: PlantedTree, val cell: Rect)

/** Kern's placement ([TreesLayout.place]) in dp, scaled to pixels. */
private fun place(trees: List<AreaGrowth>, width: Float, density: Float): List<TreeCell> =
    TreesLayout.place(trees, (width / density).toDouble()).spots.map {
        val d = density.toDouble()
        val cell = Rect((it.cellX * d).toFloat(), (it.cellY * d).toFloat(),
            ((it.cellX + it.cellWidth) * d).toFloat(), ((it.cellY + it.cellHeight) * d).toFloat())
        val foot = Offset((it.footX * d).toFloat(), (it.baseline * d).toFloat())
        TreeCell(PlantedTree(trees[it.index], foot, (it.height * d).toFloat(), density), cell)
    }
