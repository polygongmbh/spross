package net.spross.kern.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowPanesTests {

    @Test
    fun aPhonePassesThroughAsOneFullWidthColumn() {
        val panes = WindowPanes(windowWidth = 402.0, leadingInset = 0.0, trailingInset = 0.0)
        assertFalse(panes.split)
        assertEquals(WindowPanes.Span(0.0, 402.0), panes.panes.single().column)
    }

    @Test
    fun aMidWidthWindowCentersOneCappedColumn() {
        val column = WindowPanes(windowWidth = 680.0, leadingInset = 0.0, trailingInset = 0.0).panes.single().column
        assertEquals(WindowPanes.READABLE_WIDTH, column.width)
        assertEquals(680.0 - column.end, column.start)
    }

    @Test
    fun aWideWindowSplitsItsSafeAreaWithTheCenterBetweenThePanes() {
        val panes = WindowPanes(windowWidth = 951.0, leadingInset = 0.0, trailingInset = 84.0)
        assertTrue(panes.split)
        val (leading, trailing) = panes.panes
        val center = (951.0 - 84.0) / 2
        assertTrue(leading.frame.end < center && trailing.frame.start > center)
        assertEquals(951.0 - 84.0, trailing.frame.end)
        assertTrue(leading.column.width <= WindowPanes.READABLE_WIDTH)
    }

    @Test
    fun aSinglePaneScreenStandsInTheTrailingHalfOnceSplit() {
        val panes = WindowPanes(windowWidth = 951.0, leadingInset = 0.0, trailingInset = 84.0)
        assertEquals(panes.panes.last(), panes.single)
    }
}
