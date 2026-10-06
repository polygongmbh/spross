package net.spross.kern.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetRotationTests {
    private val step = 15 * 60 * 1000L

    @Test
    fun theRotationReadsTheClockSoAReloadPicksItUpWhereItStands() {
        val moment = 1_790_000_000_000L
        // A reload mid-step and one at the step's start see the same head;
        // the next step hands the head to the next row.
        val atStepStart = moment.floorDiv(step) * step
        assertEquals(
            WidgetRotation.window(10, atStepStart, 3, step),
            WidgetRotation.window(10, atStepStart + step / 2, 3, step),
        )
        val now = WidgetRotation.window(10, atStepStart, 3, step)
        val next = WidgetRotation.window(10, atStepStart + step, 3, step)
        assertEquals((now.first() + 1) % 10, next.first())
    }

    @Test
    fun aShortBoxNeverShowsAWordTwiceInOneTile() {
        val window = WidgetRotation.window(4, 1_790_000_000_000L, 16, step)
        assertEquals(4, window.size)
        assertEquals(4, window.toSet().size)
    }
}
