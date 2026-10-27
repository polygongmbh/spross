package net.spross.kern.design

import kotlin.math.min

/**
 * How a window lays its screens out: one column, or two panes side by side.
 * The decision is width alone, so a tablet, a foldable unfolded and any wide window answer to the same rule.
 *
 * A safe area narrower than [SPLIT_MIN_WIDTH] is one pane — every phone held upright passes through untouched.
 * From there it splits into two halves of the safe area, [GUTTER] apart around its center,
 * which keeps a foldable's hinge, at the window's center, between the panes rather than across a line of text.
 * Split, the leading pane stands half a gutter in from the safe area's edge,
 * as far as the trailing pane stands from the center, so neither half runs up against an edge.
 * Inside any pane, text stands in a [column] no wider than [READABLE_WIDTH], centered in the pane.
 * A screen without a two-pane design stands in the [single] pane, which halves only where the hinge does:
 * on a foldable [halfOpen] like a book, the trailing half, beside the edge where the system keeps its controls;
 * on any window lying flat, the whole safe area, its column centered.
 *
 * Every figure is points (dp) from the window's leading edge; the insets are the safe area's.
 * [halfOpen] is the platform's reading of its hinge — partly open, as a book, rather than flat or closed.
 */
class WindowPanes(windowWidth: Double, leadingInset: Double, trailingInset: Double, halfOpen: Boolean = false) {

    /** One pane's share of the window: its [frame] and the readable [column] centered inside it. */
    data class Pane(val frame: Span, val column: Span)

    /** A horizontal stretch: from [start], [width] wide. */
    data class Span(val start: Double, val width: Double) {
        val end: Double get() = start + width
    }

    /** One pane for a single column, two for the split, leading first. */
    val panes: List<Pane>

    val split: Boolean get() = panes.size == 2

    /** Where a screen without a two-pane design stands: the trailing half of a split book, else the whole safe area. */
    val single: Pane

    init {
        val safeWidth = (windowWidth - leadingInset - trailingInset).coerceAtLeast(0.0)
        val whole = pane(Span(leadingInset, safeWidth))
        panes = if (safeWidth < SPLIT_MIN_WIDTH) {
            listOf(whole)
        } else {
            val start = leadingInset + GUTTER / 2
            val half = (safeWidth - GUTTER / 2 - GUTTER) / 2
            listOf(
                pane(Span(start, half)),
                pane(Span(start + half + GUTTER, half)),
            )
        }
        single = if (split && halfOpen) panes.last() else whole
    }

    private fun pane(frame: Span): Pane {
        val width = min(frame.width, READABLE_WIDTH)
        return Pane(frame, Span(frame.start + (frame.width - width) / 2, width))
    }

    companion object {
        /** The narrowest safe area that splits: two panes of about a phone's width each. */
        const val SPLIT_MIN_WIDTH: Double = 700.0

        /** The widest a column of text runs before a line gets too long to read at a glance. */
        const val READABLE_WIDTH: Double = 520.0

        /** The space between two panes, around the window's center. */
        const val GUTTER: Double = 40.0
    }
}
