package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** When a closed run's figures are worth reporting unasked, when its close is celebrated, and what it highlights. */
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

    @Test
    fun aSprosseFirstClearedOutranksTheStreak() {
        val run = DrillRunSummary(done = 20, bestAnswerStreak = 9, newRecord = false, peak = 4, newSprosse = true)
        assertEquals(DrillHighlight(DrillHighlight.Kind.Sprosse, 4), run.highlight)
    }

    @Test
    fun aLongStreakOutranksAClimbTheStoreKnew() {
        val run = DrillRunSummary(done = 20, bestAnswerStreak = 9, newRecord = false, peak = 2)
        assertEquals(DrillHighlight(DrillHighlight.Kind.Streak, 9), run.highlight)
    }

    @Test
    fun aShortStreakWithoutAClimbNamesNothing() {
        assertNull(DrillRunSummary(done = 20, bestAnswerStreak = 2, newRecord = false).highlight)
    }
}
