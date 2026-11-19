package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DayMarkTests {

    @Test
    fun noRunIsNeverCounted() {
        assertFalse(DayMark.offer(streak = 0).counted)
        assertFalse(DayMark.done(worked = true, streak = 0).counted)
        assertFalse(DayMark.lead(streak = 0).counted)
    }

    @Test
    fun aDayNotWorkedIsNeverCelebrated() {
        assertNotEquals(DayMark.done(worked = true, streak = 3).emoji, DayMark.done(worked = false, streak = 3).emoji)
    }

    @Test
    fun withARunTheRoundCardWearsTheFlame() {
        assertEquals(DayMark(null, true), DayMark.offer(streak = 4))
    }

    @Test
    fun aLongRunLetsTheCountSpeakForItself() {
        assertTrue(DayMark.showsUnit(streak = 3))
        assertFalse(DayMark.showsUnit(streak = 106))
    }
}
