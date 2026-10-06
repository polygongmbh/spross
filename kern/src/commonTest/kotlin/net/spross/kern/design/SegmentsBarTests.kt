package net.spross.kern.design

import kotlin.test.Test
import kotlin.test.assertEquals

class SegmentsBarTests {

    @Test
    fun aLongRunDrawsOnlyItsLatestAnswers() {
        val bar = SegmentsBar(answered = 100, remaining = 1)
        assertEquals(SegmentsBar.WINDOW, bar.shown)
        assertEquals(100 - SegmentsBar.WINDOW, bar.firstShown)
    }

    @Test
    fun aShortRoundDrawsEveryAnswer() {
        val bar = SegmentsBar(answered = 5, remaining = 7)
        assertEquals(5, bar.shown)
        assertEquals(0, bar.firstShown)
    }
}
