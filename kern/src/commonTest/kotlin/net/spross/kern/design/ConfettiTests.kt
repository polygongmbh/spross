package net.spross.kern.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfettiTests {

    @Test
    fun aPieceFallsOverTime() {
        val frame = ConfettiFrame()
        frame.fill(0, 4.5, 400.0, 10_000.0)
        val early = frame.values[ConfettiFrame.Y]
        frame.fill(0, 5.0, 400.0, 10_000.0)
        assertTrue(frame.values[ConfettiFrame.Y] > early)
    }

    @Test
    fun nothingIsLeftOnScreenOnceAWaveRetires() {
        val frame = ConfettiFrame()
        assertTrue(frame.fill(0, 2.0, 400.0, 800.0) > 0)
        assertEquals(0, frame.fill(0, ConfettiFrame.LIFE, 400.0, 800.0))
        assertTrue(ConfettiFrame.retired(ConfettiFrame.LIFE))
    }
}
