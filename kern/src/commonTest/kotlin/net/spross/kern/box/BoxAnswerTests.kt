package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating

/** Answering: FSRS-6 scheduling, drain feed, lapses, unknown ids, budget drops. */
class BoxAnswerTests {
    private val now = Box.day1

    // A word you already knew skips the step entirely: FSRS takes it straight to
    // day scale (S0(Good) = 2.3065 → ~7.6 d at retention 0.8).
    @Test
    fun goodOnNewGraduatesStraightToDayScale() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, "w01", Rating.Good, now)

        val sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Review, sched.phase)
        assertNull(sched.stepIndex)
        assertTrue(sched.due!! >= Box.instant(now) + 1.days)
        assertTrue(BoxEngine.dueNow(state, Box.plusSeconds(now, 600)).isEmpty())
    }

    // A word you missed comes back after the one step — past the end of a short
    // sitting, so the retry is a fresh recall and not the tail of the same run.
    @Test
    fun againOnNewSchedulesTheSingleTwoMinuteStep() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, "w01", Rating.Again, now)
        val sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Learning, sched.phase)
        assertEquals(0, sched.stepIndex)
        assertEquals(Box.instant(now) + 120.seconds, sched.due)
        assertEquals(0, sched.lapses) // lapses never count introduction, only tries after it

        assertTrue(BoxEngine.dueNow(state, Box.plusSeconds(now, 119)).isEmpty())
        assertEquals(listOf("w01"), BoxEngine.dueNow(state, Box.plusSeconds(now, 120)))
    }


    // With a single step, the retry either graduates the word or repeats the step —
    // there is no second minute-scale rung to climb.
    @Test
    fun againThenGoodGraduatesOffTheStep() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, "w01", Rating.Again, now)
        val retry = Box.plusSeconds(now, 120)
        state = Box.answered(state, "w01", Rating.Good, retry)

        val sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Review, sched.phase)
        assertNull(sched.stepIndex)
        assertTrue(sched.due!! >= Box.instant(retry) + 1.days)
        assertEquals(2, sched.log.size)
        assertTrue(sched.log.last().elapsedDays > 0)
    }

    // Hard holds on the step too, at the whole-minute blend ts-fsrs pins: a single
    // step is stretched x1.5, so 2 min rounds to 3. It does NOT graduate — only
    // Good and Easy leave the step on a first answer.
    @Test
    fun hardOnNewHoldsTheStepAtThreeMinutes() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, "w01", Rating.Hard, now)
        val sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Learning, sched.phase)
        assertEquals(0, sched.stepIndex)
        assertEquals(Box.instant(now) + 180.seconds, sched.due)
    }

    @Test
    fun againOnTheStepRepeatsIt() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, "w01", Rating.Again, now)
        val retry = Box.plusSeconds(now, 120)
        state = Box.answered(state, "w01", Rating.Again, retry)

        val sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Learning, sched.phase)
        assertEquals(0, sched.stepIndex)
        assertEquals(Box.instant(retry) + 120.seconds, sched.due)
    }

    @Test
    fun easyOnNewGraduatesImmediately() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, "w01", Rating.Easy, now)
        val sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Review, sched.phase)
        assertTrue(sched.due!! >= Box.instant(now) + 1.days)
        assertTrue(sched.memory!!.stability >= 3) // FSRS-6 S0(Easy) = 8.2956
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
        assertEquals(1, sched.lapses)
        assertFalse(sched.suspended)
        assertEquals(Box.instant(now) + 600.seconds, sched.due)
    }

    @Test
    fun lapsedReviewCardIsNotRetriedInSessionDrain() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = now - 3_600_000, lastReviewMillis = Box.plusDays(now, -10.0)),
        )
        state = Box.answered(state, "w01", Rating.Again, now)
        // The drain loop stays empty for the rest of the session window …
        assertTrue(BoxEngine.dueNow(state, Box.plusSeconds(now, 60)).isEmpty())
        assertTrue(BoxEngine.dueNow(state, Box.plusSeconds(now, 599)).isEmpty())
        // … the lapsed card only returns at its 10-minute relearning step.
        assertEquals(listOf("w01"), BoxEngine.dueNow(state, Box.plusSeconds(now, 600)))
    }

    // Repeated fails widen the gap instead of repeating the same short wait: relearning
    // steps grow with each consecutive Again (product ruling 2026-09-01, supersedes the
    // leech ruling — a lapse no longer auto-suspends).
    @Test
    fun consecutiveLapsesGrowTheRelearningWaitThenGoodGraduatesImmediately() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(
            state,
            Box.sched(
                "w01", phase = CardPhase.Relearning,
                dueMillis = now - 60_000, lastReviewMillis = Box.plusDays(now, -1.0), lapses = 1,
            ),
        )

        state = Box.answered(state, "w01", Rating.Again, now) // 2nd consecutive Again: 10m -> 1d
        var sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Relearning, sched.phase)
        assertEquals(1, sched.stepIndex)
        assertEquals(Box.instant(now) + 86_400.seconds, sched.due)
        assertFalse(sched.suspended)

        val retry = Box.plusSeconds(now, 86_400)
        state = Box.answered(state, "w01", Rating.Again, retry) // 3rd consecutive Again: 1d -> 3d
        sched = state.scheduling.getValue("w01")
        assertEquals(2, sched.stepIndex)
        assertEquals(Box.instant(retry) + (3 * 86_400).seconds, sched.due)

        val recall = Box.plusSeconds(retry, 3 * 86_400)
        state = Box.answered(state, "w01", Rating.Good, recall) // graduates from step 2, immediately
        sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Review, sched.phase)
        assertNull(sched.stepIndex)
        assertFalse(sched.suspended)
    }

    // A word still struggling through its learning steps counts lapses too — lapses are
    // not gated to review phase — but counting alone no longer suspends the card (leech
    // ruling overturned 2026-09-01); only setSuspended does that.
    @Test
    fun againOnTheStepCountsLapsesWithoutSuspending() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, "w01", Rating.Again, now) // introduction: exempt
        val retry1 = Box.plusSeconds(now, 120)
        state = Box.answered(state, "w01", Rating.Again, retry1) // 1st lapse, still Learning
        var sched = state.scheduling.getValue("w01")
        assertEquals(CardPhase.Learning, sched.phase)
        assertEquals(1, sched.lapses)
        assertFalse(sched.suspended)

        val retry2 = Box.plusSeconds(retry1, 120)
        state = Box.answered(state, "w01", Rating.Again, retry2) // 2nd lapse
        sched = state.scheduling.getValue("w01")
        assertEquals(2, sched.lapses)
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
        state = Box.answered(state, "w01", Rating.Good, now)
        val entry = state.scheduling.getValue("w01").log.last()
        assertTrue(kotlin.math.abs(entry.elapsedDays - 2.0) < 0.001)
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
        assertEquals(state, BoxEngine.answer(state, "nope", Rating.Good, now, Box.TZ))
    }

    @Test
    fun introductionCountsTheCardAndDequeues() {
        var state = Box.state(listOf(Box.word(1)))
        state = BoxEngine.enqueue(state, listOf("w01"))
        state = Box.answered(state, "w01", Rating.Good, now)
        assertEquals(1, state.newIntroduced["2026-07-01"])
        assertTrue(state.enqueued.isEmpty())

        // Later answers are reviews, never a second introduction.
        state = Box.answered(state, "w01", Rating.Good, Box.plusSeconds(now, 700))
        assertEquals(1, state.newIntroduced["2026-07-01"])
        assertEquals(1, state.scheduling.size)
    }
}
