package net.spross.app.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import net.spross.kern.design.TreeInk
import net.spross.kern.design.TreePath
import net.spross.kern.design.TreePicture
import net.spross.kern.design.TreeShape

// Kern draws the tree ([TreePicture]): what hangs where, every outline, each layer's ink and opacity.
// This turns its outlines into paths, [unit] px to the dp, and inks them in the theme's colors.

internal class TreeArt(picture: TreePicture, private val unit: Float) {
    private val layers = picture.layers
    /** Every layer whole and settled, built once. */
    private val settled = layers.map { layer -> Path().also { path -> layer.shapes.forEach { path.add(it, 1f) } } }

    /** Draws every layer; [scale] sizes the marks at the ranks it holds against their settled size. */
    fun draw(scope: DrawScope, colors: ThemeColors, scale: Map<Int, Float> = emptyMap()) {
        for ((index, layer) in layers.withIndex()) {
            val path = if (scale.isEmpty() || layer.shapes.none { it.rank in scale }) settled[index]
            else Path().also { path -> layer.shapes.forEach { path.add(it, scale[it.rank] ?: 1f) } }
            val color = ink(layer.ink, colors).let { it.copy(alpha = it.alpha * layer.opacity.toFloat()) }
            if (layer.stroke > 0) scope.drawPath(path, color, style = Stroke(layer.stroke.toFloat() * unit, cap = StrokeCap.Round))
            else scope.drawPath(path, color)
        }
    }

    /** [shape]'s outline commands ([TreePath]), [scale]d about its pivot. */
    private fun Path.add(shape: TreeShape, scale: Float) {
        val data = shape.path
        fun x(i: Int) = ((shape.pivotX + (data[i] - shape.pivotX) * scale) * unit).toFloat()
        fun y(i: Int) = ((shape.pivotY + (data[i] - shape.pivotY) * scale) * unit).toFloat()
        var i = 0
        while (i < data.size) when (data[i].toInt()) {
            TreePath.MOVE -> { moveTo(x(i + 1), y(i + 2)); i += 3 }
            TreePath.LINE -> { lineTo(x(i + 1), y(i + 2)); i += 3 }
            TreePath.QUAD -> { quadraticTo(x(i + 1), y(i + 2), x(i + 3), y(i + 4)); i += 5 }
            TreePath.OVAL -> {
                val left = x(i + 1); val top = y(i + 2)
                addOval(Rect(left, top, left + (data[i + 3] * scale * unit).toFloat(), top + (data[i + 4] * scale * unit).toFloat()))
                i += 5
            }
            else -> { close(); i += 1 }
        }
    }

    private fun ink(ink: TreeInk, colors: ThemeColors): Color = when (ink) {
        TreeInk.GROUND -> colors.ground
        TreeInk.WOOD -> colors.wood
        TreeInk.WOOD_SHADE -> colors.woodShade
        TreeInk.LEAF -> colors.leaf
        TreeInk.LEAF_DEEP -> colors.leafDeep
        TreeInk.BUD -> colors.bud
        TreeInk.FRUIT -> colors.fruit
        TreeInk.BLOSSOM -> colors.blossom
        TreeInk.FALLEN -> colors.fallen
        TreeInk.ACCENT -> colors.accent
    }
}
