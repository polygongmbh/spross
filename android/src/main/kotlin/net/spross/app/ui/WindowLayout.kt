package net.spross.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import net.spross.kern.design.WindowPanes

// The window's panes.
//
// Whether a screen stands in one column or two panes is kern's (`WindowPanes`), from the width
// the screens are given. The root measures it once and hands it down; the screens read it.

/** The screens' layout as kern decides it, from the width they stand in; null until measured. */
val LocalWindowPanes = compositionLocalOf<WindowPanes?> { null }

/** Kern's panes for the whole window, safe area and all — what the navigation and the sheets go by. */
@Composable
fun windowPanes(): WindowPanes {
    val density = LocalDensity.current
    val size = LocalWindowInfo.current.containerSize
    val insets = WindowInsets.safeDrawing
    val left = insets.getLeft(density, LayoutDirection.Ltr) / density.density
    val right = insets.getRight(density, LayoutDirection.Ltr) / density.density
    return remember(size, left, right, density.density) {
        WindowPanes((size.width / density.density).toDouble(), left.toDouble(), right.toDouble())
    }
}

/** Measures the width the screens stand in and hands kern's panes to everything under it. */
@Composable
fun MeasuresWindowPanes(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val width = maxWidth.value.toDouble()
        val panes = remember(width) { WindowPanes(width, 0.0, 0.0) }
        CompositionLocalProvider(LocalWindowPanes provides panes, content = content)
    }
}

/** Whether the screens stand in two panes. */
@Composable
fun splitWindow(): Boolean = LocalWindowPanes.current?.split == true

/**
 * A screen with no two-pane design of its own: one readable column in kern's `single` pane —
 * centered where the window stays one pane, in the trailing half where it splits, kept as far
 * from its own trailing edge as the pane's column is from the window's, so a rail on the
 * leading side moves nothing. A phone passes through untouched.
 */
@Composable
fun Modifier.singlePane(): Modifier {
    val panes = LocalWindowPanes.current ?: return this
    val pane = panes.single
    if (!panes.split && pane.column.width >= pane.frame.width) return this
    val column = pane.column.width.dp
    val gap = (pane.frame.end - pane.column.end).dp
    val split = panes.split
    return layout { measurable, constraints ->
        val local = constraints.maxWidth
        val width = min(column.roundToPx(), local)
        val placeable = measurable.measure(constraints.copy(minWidth = min(constraints.minWidth, width), maxWidth = width))
        val x = if (split) max(0, local - gap.roundToPx() - width) else (local - width) / 2
        layout(local, placeable.height) { placeable.placeRelative(x, 0) }
    }
}

/**
 * The two panes side by side, each holding its readable column — only for a split window;
 * a screen keeps its phone layout where [WindowPanes.split] is false.
 * The trailing pane ends at the edge, the leading one keeps kern's gutter from it.
 */
@Composable
fun PaneSplit(
    panes: WindowPanes,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Layout(listOf(leading, trailing), modifier) { (lead, trail), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val first = panes.panes.first()
        val last = panes.single
        val trailingStart = width - last.frame.width.dp.roundToPx()
        val gutter = (last.frame.start - first.frame.end).dp.roundToPx()
        val leadingStart = trailingStart - gutter - first.frame.width.dp.roundToPx()
        fun place(pane: WindowPanes.Pane, start: Int, child: Measurable): Pair<Int, Placeable> {
            val paneWidth = pane.frame.width.dp.roundToPx()
            val column = min(pane.column.width.dp.roundToPx(), paneWidth)
            return start + (paneWidth - column) / 2 to child.measure(Constraints.fixed(max(0, column), height))
        }
        val placed = listOf(place(first, leadingStart, lead.first()), place(last, trailingStart, trail.first()))
        layout(width, height) {
            placed.forEach { (x, placeable) -> placeable.placeRelative(x, 0) }
        }
    }
}

/**
 * Where a bottom sheet stands: as wide as the system's sheet on one pane, and on a split
 * window a readable column in the trailing half, offset from the center the system puts it on.
 */
class SheetPlacement(val maxWidth: Dp, val offset: Dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun sheetPlacement(): SheetPlacement {
    val panes = windowPanes()
    if (!panes.split) return SheetPlacement(BottomSheetDefaults.SheetMaxWidth, 0.dp)
    val column = panes.single.column
    val window = LocalWindowInfo.current.containerSize.width / LocalDensity.current.density
    return SheetPlacement(column.width.dp, (column.start + column.width / 2 - window / 2).dp)
}

/** [Dp] arithmetic the layout above reads kern's figures through. */
private val Double.dp: Dp get() = this.toFloat().dp
