package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When a closed run's figures are worth reporting unasked, and when its close is celebrated. */
class DrillRunSummaryTest {

    @Test
    fun aPeekIsNotReported() {
        assertFalse(DrillRunSummary(done = 2, bestAnswerStreak = 2, newRecord = false).worthReporting)
    }

    @Test
    fun aRunIsReported() {
        assertTrue(DrillRunSummary(done = 20, bestAnswerStreak = 4, newRecord = false).worthReporting)
    }

    @Test
    fun aRecordIsCelebratedHoweverShort() {
        assertTrue(DrillRunSummary(done = 2, bestAnswerStreak = 2, newRecord = true).celebrated)
    }

    @Test
    fun aPeekWithoutARecordIsNotCelebrated() {
        assertFalse(DrillRunSummary(done = 2, bestAnswerStreak = 2, newRecord = false).celebrated)
    }

    @Test
    fun onlyTheStrugglingPauseGoesUncelebrated() {
        assertFalse(DrillPauseReason.Struggling.celebrated)
        assertTrue(DrillPauseReason.Count.celebrated)
    }
}
