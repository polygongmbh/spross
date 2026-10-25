package net.spross.kern.design

import kotlin.math.min

/**
 * How a window lays its screens out: one column, or two panes side by side.
 * The decision is width alone, so a tablet, a foldable unfolded and any wide window answer to the same rule.
 *
 * A safe area narrower than [SPLIT_MIN_WIDTH] is one pane — every phone held upright passes through untouched.
 * From there it splits into two halves of the safe area, [GUTTER] apart around its center,
 * which keeps a foldable's hinge, at the window's center, between the panes rather than across a line of text.
 * Inside any pane, text stands in a [column] no wider than [READABLE_WIDTH], centered in the pane.
 *
 * Every figure is points (dp) from the window's leading edge; the insets are the safe area's.
 */
class WindowPanes(windowWidth: Double, leadingInset: Double, trailingInset: Double) {

    /** One pane's share of the window: its [frame] and the readable [column] centered inside it. */
    data class Pane(val frame: Span, val column: Span)

    /** A horizontal stretch: from [start], [width] wide. */
    data class Span(val start: Double, val width: Double) {
        val end: Double get() = start + width
    }

    /** One pane for a single column, two for the split, leading first. */
    val panes: List<Pane>

    val split: Boolean get() = panes.size == 2

    init {
        val safeWidth = (windowWidth - leadingInset - trailingInset).coerceAtLeast(0.0)
        panes = if (safeWidth < SPLIT_MIN_WIDTH) {
            listOf(pane(Span(leadingInset, safeWidth)))
        } else {
            val half = (safeWidth - GUTTER) / 2
            listOf(
                pane(Span(leadingInset, half)),
                pane(Span(leadingInset + half + GUTTER, half)),
            )
        }
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
