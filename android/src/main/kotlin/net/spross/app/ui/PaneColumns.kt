package net.spross.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.max

/** Which pane a child of [PaneColumns] stands in where the window splits. */
enum class PaneSide { Leading, Trailing }

/** The children of [PaneColumns], in the one column's order, each naming its pane. */
class PaneScope internal constructor() {
    internal class Item(val side: PaneSide, val content: @Composable () -> Unit)

    internal val items = mutableListOf<Item>()

    /** A child standing in [side] where the window splits. */
    fun item(side: PaneSide = PaneSide.Trailing, content: @Composable () -> Unit) {
        items += Item(side, content)
    }
}

/**
 * One list of children, stacked in a single scrolling column or dealt into kern's two panes
 * ([PaneSplit]) by their [PaneScope.item] side, in the order given; each pane scrolls on its own.
 * The children stay the same children whichever it is, so their state survives a fold or a resize.
 *
 * Each column is inset by [margin] and ends [bottom] under its last child.
 * A child laid out at no height (a card not offered today) takes no [spacing].
 */
@Composable
fun PaneColumns(
    margin: Dp,
    spacing: Dp,
    bottom: Dp,
    modifier: Modifier = Modifier,
    content: PaneScope.() -> Unit,
) {
    val panes = LocalWindowPanes.current
    val items = PaneScope().apply(content).items
    // why: a child is movable content, so a fold or a resize carries it between the one column
    // and a pane with its remembered state, rather than composing it afresh.
    val movables = remember { mutableListOf<@Composable (@Composable () -> Unit) -> Unit>() }
    while (movables.size < items.size) movables += movableContentOf { child: @Composable () -> Unit -> child() }
    val leadingScroll = rememberScrollState()
    val trailingScroll = rememberScrollState()
    if (panes != null && panes.split) {
        PaneSplit(
            panes,
            modifier,
            leading = { PaneStack(items, movables, PaneSide.Leading, leadingScroll, margin, spacing, bottom) },
            trailing = { PaneStack(items, movables, PaneSide.Trailing, trailingScroll, margin, spacing, bottom) },
        )
    } else {
        PaneStack(items, movables, null, leadingScroll, margin, spacing, bottom, modifier)
    }
}

/** One scrolling column of the children standing in [side], or of all of them where it is null. */
@Composable
private fun PaneStack(
    items: List<PaneScope.Item>,
    movables: List<@Composable (@Composable () -> Unit) -> Unit>,
    side: PaneSide?,
    scroll: ScrollState,
    margin: Dp,
    spacing: Dp,
    bottom: Dp,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            items.forEachIndexed { index, item ->
                if (side == null || item.side == side) movables[index](item.content)
            }
        },
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = margin, end = margin, bottom = bottom),
    ) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val shown = placeables.filter { it.height > 0 }
        val height = shown.sumOf { it.height } + gap * max(0, shown.size - 1)
        layout(constraints.maxWidth, height) {
            var y = 0
            placeables.forEach {
                it.placeRelative(0, y)
                if (it.height > 0) y += it.height + gap
            }
        }
    }
}
