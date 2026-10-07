package net.spross.kern.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AreaBarTests {

    @Test
    fun anAreaWithNothingInItLeavesTheTrackBare() {
        val bar = AreaBar(0.0, 0.0, 0.0, progressTotal = 40)

        assertEquals(0.0, bar.fill)
        assertTrue(bar.stops.isEmpty())
    }

    @Test
    fun theUntouchedRestStaysTrackAndEmptyStretchesDrawNothing() {
        val bar = AreaBar(settled = 5.0, growing = 0.0, queued = 5.0, progressTotal = 40)

        assertEquals(0.25, bar.fill)
        assertEquals(
            listOf(AreaStretch.Settled, AreaStretch.Settled, AreaStretch.Queued, AreaStretch.Queued),
            bar.stops.map { it.stretch },
        )
    }
}
