package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** Which pane a child of [PaneColumns] stands in where the window splits. */
enum class PaneSide { Leading, Trailing }

/** The pane this child stands in where [PaneColumns] splits; one column ignores it. */
fun Modifier.pane(side: PaneSide): Modifier = layoutId(side)

/**
 * One list of children, stacked in a single column or dealt into kern's two panes by their
 * [pane], in the order given — trailing where none is named. The children stay the same
 * children whichever it is, so their state survives a fold or a rotation.
 *
 * Each column is a pane's readable column inset by [margin], the trailing pane ending at the
 * edge; where the window stays one column, the whole width inset. A child laid out at no
 * height (a card not offered today) takes no [spacing].
 */
@Composable
fun PaneColumns(
    margin: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val panes = LocalWindowPanes.current
    Layout(content, modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val inset = margin.roundToPx()
        val gap = spacing.roundToPx()

        class Column(val x: Int, val width: Int, val side: PaneSide?)

        val columns = if (panes == null || !panes.split) {
            listOf(Column(inset, max(0, width - 2 * inset), null))
        } else {
            val leading = panes.panes.first()
            val trailing = panes.panes.last()
            val trailingStart = width - trailing.frame.width.dp.roundToPx()
            val gutter = (trailing.frame.start - leading.frame.end).dp.roundToPx()
            val leadingStart = trailingStart - gutter - leading.frame.width.dp.roundToPx()
            listOf(Triple(PaneSide.Leading, leading, leadingStart), Triple(PaneSide.Trailing, trailing, trailingStart))
                .map { (side, pane, start) ->
                    val paneWidth = pane.frame.width.dp.roundToPx()
                    val column = min(pane.column.width.dp.roundToPx(), paneWidth)
                    Column(start + (paneWidth - column) / 2 + inset, max(0, column - 2 * inset), side)
                }
        }
        val placed = columns.map { column ->
            val members = measurables.filter { column.side == null || (it.layoutId ?: PaneSide.Trailing) == column.side }
            column to members.map { it.measure(Constraints(maxWidth = column.width)) }
        }
        val height = placed.maxOfOrNull { (_, placeables) ->
            val shown = placeables.filter { it.height > 0 }
            shown.sumOf { it.height } + gap * max(0, shown.size - 1)
        } ?: 0
        layout(width, height) {
            placed.forEach { (column, placeables) ->
                var y = 0
                placeables.forEach {
                    it.placeRelative(column.x, y)
                    if (it.height > 0) y += it.height + gap
                }
            }
        }
    }
}


private val Double.dp: Dp get() = this.toFloat().dp
