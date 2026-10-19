package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import net.spross.kern.fsrs.FsrsScheduler
import net.spross.kern.fsrs.SchedulerState
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating

/** Answering: FSRS-6 scheduling, drain feed, unknown ids, budget drops. */
class BoxAnswerTests {
    private val now = Box.day1

    // Again is the only rating that stays on the ladder: Hard, Good and Easy all skip the
    // step and go straight to day scale, and a graduated interval floors at one day.
    @Test
    fun everyPassOnNewGraduatesStraightToDayScale() {
        for (rating in listOf(Rating.Hard, Rating.Good, Rating.Easy)) {
            val state = Box.answered(Box.state(listOf(Box.word(1))), "w01", rating, now)
            val sched = state.scheduling.getValue("w01")
            assertEquals(CardPhase.Review, sched.phase, "$rating")
            assertNull(sched.stepIndex)
            assertTrue(sched.due!! >= Box.instant(now) + 1.days, "$rating")
        }
    }

    // A word you missed comes back at the ladder's first entry — past the end of a
    // short sitting, so the retry is a fresh recall and not the tail of the same run.
    @Test
    fun againOnNewSchedulesTheLaddersFirstTenMinuteStep() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, "w01", Rating.Again, now)
        val sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Learning, sched.phase)
        assertEquals(0, sched.stepIndex)
        assertEquals(Box.instant(now) + Box.steps[0].seconds, sched.due)

        assertTrue(Box.dueIds(state, Box.plusSeconds(now, Box.steps[0] - 1)).isEmpty())
        assertEquals(listOf("w01"), Box.dueIds(state, Box.plusSeconds(now, Box.steps[0])))
    }

    // A second consecutive Again climbs the ladder instead of repeating its first
    // entry — the word gets more room before its next try, not the same short wait.
    @Test
    fun consecutiveAgainOnTheStepClimbsIt() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, "w01", Rating.Again, now)
        val retry = Box.plusSeconds(now, Box.steps[0])
        state = Box.answered(state, "w01", Rating.Again, retry)

        val sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Learning, sched.phase)
        assertEquals(1, sched.stepIndex)
        assertEquals(Box.instant(retry) + Box.steps[1].seconds, sched.due)
        assertFalse(sched.suspended, "repeated misses never suspend; only setSuspended does")
    }

    // Relearning steps = FSRS-6 reference default [10m]: a lapse returns in 10
    // minutes; there is NO in-session retry (breadth ruling 2026-07-22).
    @Test
    fun againOnReviewLapsesToRelearningWithTenMinuteStep() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = now - 3_600_000, lastReviewMillis = Box.plusDays(now, -10.0)),
        )
        state = Box.answered(state, "w01", Rating.Again, now)
        val sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Relearning, sched.phase)
        assertFalse(sched.suspended)
        assertEquals(Box.instant(now) + Box.steps[0].seconds, sched.due)
    }

    // Repeated fails widen the gap instead of repeating the same short wait: relearning
    // steps grow with each consecutive Again (product ruling 2026-09-01: a lapse never
    // auto-suspends).
    @Test
    fun consecutiveLapsesGrowTheRelearningWaitThenGoodGraduatesImmediately() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(
            state,
            Box.sched(
                "w01", phase = CardPhase.Relearning,
                dueMillis = now - 60_000, lastReviewMillis = Box.plusDays(now, -1.0),
            ),
        )

        state = Box.answered(state, "w01", Rating.Again, now) // 2nd consecutive Again: 10m -> 1d
        var sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Relearning, sched.phase)
        assertEquals(1, sched.stepIndex)
        assertEquals(Box.instant(now) + Box.steps[1].seconds, sched.due)
        assertFalse(sched.suspended)

        val retry = Box.plusSeconds(now, Box.steps[1])
        state = Box.answered(state, "w01", Rating.Again, retry) // 3rd consecutive Again: 1d -> 3d
        sched = state.scheduling.getValue("w01")
        assertEquals(2, sched.stepIndex)
        assertEquals(Box.instant(retry) + Box.steps[2].seconds, sched.due)

        val recall = Box.plusSeconds(retry, Box.steps[2])
        state = Box.answered(state, "w01", Rating.Good, recall) // graduates from step 2, immediately
        sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Review, sched.phase)
        assertNull(sched.stepIndex)
        assertFalse(sched.suspended)
    }

    @Test
    fun elapsedComesFromLastLogEntryNeverFromDue() {
        var state = Box.state(listOf(Box.word(1)))
        // due far in the past on purpose — must not affect elapsed
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = Box.plusDays(now, -9.0), lastReviewMillis = Box.plusDays(now, -2.0)),
        )
        val before = state.scheduling.getValue("w01")
        state = Box.answered(state, "w01", Rating.Good, now)

        // The schedule FSRS gives for the two days since the last ANSWER, not the nine
        // since the due date it was overdue by.
        val expected = FsrsScheduler(BoxConfig().fsrsParameters())
            .review(SchedulerState(before.phase, before.stepIndex, before.memory), 2.0, Rating.Good)
        assertEquals(Box.instant(now) + expected.intervalSeconds.seconds, state.scheduling.getValue("w01").due)
    }

    @Test
    fun everyAnswerAppendsLogIncludingSameDayRetries() {
        var state = Box.state(listOf(Box.word(1)))
        var t = now
        val ratings = listOf(Rating.Good, Rating.Again, Rating.Again, Rating.Good)
        for (rating in ratings) {
            state = Box.answered(state, "w01", rating, t)
            t = Box.plusSeconds(t, 120)
        }
        val sched = state.scheduling.getValue("w01")
        assertEquals(4, sched.log.size)
        assertEquals(ratings, sched.log.map { it.rating })
    }

    @Test
    fun unknownIdLeavesTheStateUntouched() {
        val state = Box.state(listOf(Box.word(1)))
        assertEquals(state, BoxEngine.answer(state, "nope", Rating.Good, now))
    }

    @Test
    fun introductionCountsTheCardAndUnqueues() {
        var state = Box.state(listOf(Box.word(1)))
        state = BoxEngine.queue(state, listOf("w01"))
        state = Box.answered(state, "w01", Rating.Good, now)
        assertEquals(1, state.scheduling.getValue("w01").log.size) // the answer IS the introduction
        assertTrue(state.queued.isEmpty())

        // Later answers are reviews, never a second introduction.
        state = Box.answered(state, "w01", Rating.Good, Box.plusSeconds(now, 700))
        assertEquals(2, state.scheduling.getValue("w01").log.size)
        assertEquals(1, state.scheduling.size)
    }
}
