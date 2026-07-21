package net.spross.kern.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ModelDefaultsTest {
    @Test
    fun boxConfigProductDefaults() {
        val config = BoxConfig()
        assertEquals(8, config.maxLearning)
        assertEquals(30, config.sessionCap)
        assertEquals(60, config.dueSoftCap)
        assertEquals(0.8, config.desiredRetention)
        assertEquals(365, config.maximumIntervalDays)
        assertEquals(2.0, config.phraseUnlockStability)
        assertEquals(listOf(60L, 600L), config.learningStepsSeconds)
        assertEquals(listOf(60L), config.relearningStepsSeconds)
    }

    @Test
    fun dayStatsDefaultsToZero() {
        assertEquals(DayStats(0, 0, 0), DayStats())
    }

    @Test
    fun ratingValuesMatchFsrs() {
        assertEquals(listOf(1, 2, 3, 4), Rating.entries.map { it.value })
    }

    @Test
    fun roleRanksPinProduceFirst() {
        assertEquals(0, Role.Produce.rank)
        assertEquals(1, Role.Recognize.rank)
        assertEquals("produce", Role.Produce.keySegment)
        assertEquals("recognize", Role.Recognize.keySegment)
    }

    @Test
    fun newSchedulingDefaultsHoldInvariant() {
        val s = UnitScheduling(
            cardId = "a/b", role = Role.Produce, addedAt = Instant.fromEpochMilliseconds(0),
        )
        assertEquals(CardPhase.New, s.phase)
        assertNull(s.memory)
        assertNull(s.due)
        assertEquals(0, s.lapses)
        assertFalse(s.suspended)
        assertTrue(s.log.isEmpty())
    }

    @Test
    fun sessionPlanIsEmpty() {
        val stamp = JoinStamp("de", "sw", "0")
        assertTrue(SessionPlan(emptyList(), emptyList(), emptyList(), stamp).isEmpty)
        assertFalse(SessionPlan(listOf("a|produce"), emptyList(), emptyList(), stamp).isEmpty)
        assertFalse(SessionPlan(emptyList(), listOf("a|produce"), emptyList(), stamp).isEmpty)
        assertFalse(SessionPlan(emptyList(), emptyList(), listOf("a|produce"), stamp).isEmpty)
    }
}
