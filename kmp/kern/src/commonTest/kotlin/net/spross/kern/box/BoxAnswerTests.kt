package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating

/** Answering: FSRS-6 scheduling, drain feed, leeches, stale units, budget drops. */
class BoxAnswerTests {
    private val now = Box.day1

    @Test
    fun goodOnNewSchedulesTenMinuteStep() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, Box.produce("w01"), Rating.Good, now)

        val sched = state.scheduling.getValue(Box.produce("w01"))
        assertEquals(CardPhase.Learning, sched.phase)
        assertEquals(1, sched.stepIndex)
        assertEquals(Box.instant(now) + 600.seconds, sched.due)

        assertTrue(BoxEngine.dueNow(state, now).isEmpty())
        assertTrue(BoxEngine.dueNow(state, Box.plusSeconds(now, 599)).isEmpty())
        assertEquals(listOf(Box.produce("w01")), BoxEngine.dueNow(state, Box.plusSeconds(now, 600)))
    }

    @Test
    fun againOnNewSchedulesOneMinuteStep() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, Box.produce("w01"), Rating.Again, now)
        val sched = state.scheduling.getValue(Box.produce("w01"))
        assertEquals(CardPhase.Learning, sched.phase)
        assertEquals(0, sched.stepIndex)
        assertEquals(Box.instant(now) + 60.seconds, sched.due)
        assertEquals(0, sched.lapses) // lapses only count for review-phase units
    }

    // FSRS-6 adaptation: Again resets to step 0, so a following Good lands on the
    // 10-minute step instead of graduating (one step later than v1's hand-rolled steps).
    @Test
    fun againThenGoodDoesNotGraduate() {
        var state = Box.state(listOf(Box.word(1)))
        val key = Box.produce("w01")
        state = Box.answered(state, key, Rating.Again, now)
        state = Box.answered(state, key, Rating.Good, Box.plusSeconds(now, 60))
        val sched = state.scheduling.getValue(key)
        assertEquals(CardPhase.Learning, sched.phase)
        assertEquals(1, sched.stepIndex)
        assertEquals(Box.instant(Box.plusSeconds(now, 60)) + 600.seconds, sched.due)

        val graduated = Box.answered(state, key, Rating.Good, Box.plusSeconds(now, 660))
        assertEquals(CardPhase.Review, graduated.scheduling.getValue(key).phase)
    }

    @Test
    fun goodOnLastLearningStepGraduatesToReview() {
        var state = Box.state(listOf(Box.word(1)))
        val key = Box.produce("w01")
        state = Box.answered(state, key, Rating.Good, now)
        val later = Box.plusSeconds(now, 600)
        state = Box.answered(state, key, Rating.Good, later)

        val sched = state.scheduling.getValue(key)
        assertEquals(CardPhase.Review, sched.phase)
        assertTrue(sched.due!! >= Box.instant(later) + 1.days)
        assertEquals(2, sched.log.size)
        assertTrue(sched.log.last().elapsedDays > 0)
    }

    @Test
    fun easyOnNewGraduatesImmediately() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, Box.produce("w01"), Rating.Easy, now)
        val sched = state.scheduling.getValue(Box.produce("w01"))
        assertEquals(CardPhase.Review, sched.phase)
        assertTrue(sched.due!! >= Box.instant(now) + 1.days)
        assertTrue(sched.memory!!.stability >= 3) // FSRS-6 S0(Easy) = 8.2956
    }

    @Test
    fun againOnReviewLapsesToRelearningWithOneMinuteStep() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = now - 3_600_000, lastReviewMillis = Box.plusDays(now, -10.0)),
        )
        state = Box.answered(state, Box.produce("w01"), Rating.Again, now)
        val sched = state.scheduling.getValue(Box.produce("w01"))
        assertEquals(CardPhase.Relearning, sched.phase)
        assertEquals(1, sched.lapses)
        assertFalse(sched.suspended)
        // Product relearning steps [1m] preserve v1's in-session retry.
        assertEquals(Box.instant(now) + 60.seconds, sched.due)
    }

    @Test
    fun leechEighthLapseAutoSuspends() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = now - 3_600_000, lastReviewMillis = Box.plusDays(now, -5.0), lapses = 7),
        )
        state = Box.inject(
            state,
            Box.sched("w02", dueMillis = now - 3_600_000, lastReviewMillis = Box.plusDays(now, -5.0)),
        )

        state = Box.answered(state, Box.produce("w01"), Rating.Again, now)
        val sched = state.scheduling.getValue(Box.produce("w01"))
        assertEquals(8, sched.lapses)
        assertTrue(sched.suspended)

        assertEquals(listOf(Box.produce("w02")), BoxEngine.dueNow(state, now))
        val stats = BoxEngine.statistics(state, now, Box.TZ)
        assertEquals(1, stats.activeCount)
        assertEquals(1, stats.suspendedCount)
    }

    @Test
    fun elapsedComesFromLastLogEntryNeverFromDue() {
        var state = Box.state(listOf(Box.word(1)))
        // due far in the past on purpose — must not affect elapsed
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = Box.plusDays(now, -9.0), lastReviewMillis = Box.plusDays(now, -2.0)),
        )
        state = Box.answered(state, Box.produce("w01"), Rating.Good, now)
        val entry = state.scheduling.getValue(Box.produce("w01")).log.last()
        assertTrue(kotlin.math.abs(entry.elapsedDays - 2.0) < 0.001)
    }

    @Test
    fun everyAnswerAppendsLogIncludingSameDayRetries() {
        var state = Box.state(listOf(Box.word(1)))
        var t = now
        val ratings = listOf(Rating.Good, Rating.Again, Rating.Again, Rating.Good)
        for (rating in ratings) {
            state = Box.answered(state, Box.produce("w01"), rating, t)
            t = Box.plusSeconds(t, 120)
        }
        val sched = state.scheduling.getValue(Box.produce("w01"))
        assertEquals(4, sched.log.size)
        assertEquals(ratings, sched.log.map { it.rating })
    }

    @Test
    fun unknownAndMalformedKeysAreStaleNoops() {
        val state = Box.state(listOf(Box.word(1), Box.phrase("p1", components = emptyList())))
        val staleKeys = listOf(
            Box.produce("nope"),          // unknown card
            "w01",                        // malformed: no role segment
            "w01|recognize",              // malformed: recognize without form
            Box.recognize("w01", "zzz"),  // form not among the card's target forms
            Box.recognize("p1", "p1"),    // phrases never have recognize units
        )
        for (key in staleKeys) {
            val outcome = BoxEngine.answer(state, key, Rating.Good, now, Box.TZ)
            assertEquals(AnswerStatus.StaleUnit, outcome.status, "key: $key")
            assertEquals(state, outcome.state, "key: $key")
        }
    }

    @Test
    fun introductionDropsWhenPoolFullAndAppliesAfterGraduation() {
        var state = Box.state((1..5).map { Box.word(it) }, Box.config(maxLearning = 2))
        state = Box.answered(state, Box.produce("w01"), Rating.Good, now)
        state = Box.answered(state, Box.produce("w02"), Rating.Good, now)

        val blocked = BoxEngine.answer(state, Box.produce("w03"), Rating.Good, now, Box.TZ)
        assertEquals(AnswerStatus.DroppedPoolFull, blocked.status)
        assertEquals(state, blocked.state)
        assertEquals(2, blocked.state.scheduling.size)

        // Graduate w01 → a concept slot frees → the same answer now succeeds.
        val step = Box.plusSeconds(now, 700)
        var freed = Box.answered(state, Box.produce("w01"), Rating.Good, step)
        freed = Box.answered(freed, Box.produce("w03"), Rating.Good, step)
        assertEquals(3, freed.scheduling.size)
    }

    @Test
    fun unitsOfInFlightConceptsRideFreeOnFullPool() {
        var state = Box.state(
            listOf(Box.word(1, synonyms = listOf("s1")), Box.word(2)),
            Box.config(maxLearning = 1),
        )
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = Box.plusDays(now, 5.0), lastReviewMillis = Box.plusDays(now, -1.0)),
        )
        // Introducing the first recognize unit fills the 1-concept pool.
        state = Box.answered(state, Box.recognize("w01", "t1"), Rating.Good, now)
        // A second unit of the same concept does not grow the pool → applies.
        state = Box.answered(state, Box.recognize("w01", "s1"), Rating.Good, now)
        // A fresh concept would grow it → dropped.
        val dropped = BoxEngine.answer(state, Box.produce("w02"), Rating.Good, now, Box.TZ)
        assertEquals(AnswerStatus.DroppedPoolFull, dropped.status)
    }

    // Eligibility lag is re-checked at answer time: plans outlive phase changes
    // (they recompose only on joinStamp staleness), so composition-only enforcement
    // is insufficient.
    @Test
    fun recognizeIntroductionRefusedWhileProduceStillLearning() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, Box.produce("w01"), Rating.Good, now) // Learning
        val blocked = BoxEngine.answer(state, Box.recognize("w01", "t1"), Rating.Good, now, Box.TZ)
        assertEquals(AnswerStatus.DroppedIneligible, blocked.status)
        assertEquals(state, blocked.state)

        // Graduation lifts the lag: the very same answer then applies.
        val step = Box.plusSeconds(now, 700)
        var graduated = Box.answered(state, Box.produce("w01"), Rating.Good, step)
        graduated = Box.answered(graduated, Box.recognize("w01", "t1"), Rating.Good, step)
        assertEquals(2, graduated.scheduling.size)
    }

    @Test
    fun recognizeIntroductionRefusedWhenProduceMissingOrSuspendedLeech() {
        var state = Box.state(listOf(Box.word(1)))
        val missing = BoxEngine.answer(state, Box.recognize("w01", "t1"), Rating.Good, now, Box.TZ)
        assertEquals(AnswerStatus.DroppedIneligible, missing.status)
        assertEquals(state, missing.state)

        // A suspended Review leech blocks recognize at answer time too — the
        // composition-level block must not be defeatable by a stale plan.
        state = Box.inject(
            state,
            Box.sched(
                "w01",
                dueMillis = Box.plusDays(now, 5.0),
                lastReviewMillis = Box.plusDays(now, -1.0),
                lapses = 8,
                suspended = true,
            ),
        )
        val blocked = BoxEngine.answer(state, Box.recognize("w01", "t1"), Rating.Good, now, Box.TZ)
        assertEquals(AnswerStatus.DroppedIneligible, blocked.status)
        assertEquals(state, blocked.state)
    }

    @Test
    fun lockedPhraseIntroductionRefusedAtAnswerTime() {
        val state = Box.state(
            listOf(Box.word(1), Box.word(2), Box.phrase("p1", components = listOf("w01", "w02"))),
        )
        val blocked = BoxEngine.answer(state, Box.produce("p1"), Rating.Good, now, Box.TZ)
        assertEquals(AnswerStatus.DroppedIneligible, blocked.status)
        assertEquals(state, blocked.state)
    }

    @Test
    fun onlyProduceIntroductionCountsConceptAndDequeues() {
        var state = Box.state(listOf(Box.word(1)))
        state = BoxEngine.enqueue(state, listOf("w01"))
        state = Box.answered(state, Box.produce("w01"), Rating.Good, now)
        assertEquals(1, state.newIntroduced["2026-07-01"])
        assertTrue(state.enqueued.isEmpty())

        // Graduate produce, then introduce the recognize unit: no concept counted.
        val step = Box.plusSeconds(now, 700)
        state = Box.answered(state, Box.produce("w01"), Rating.Good, step)
        state = Box.answered(state, Box.recognize("w01", "t1"), Rating.Good, step)
        assertEquals(1, state.newIntroduced["2026-07-01"])
        assertEquals(2, state.scheduling.size)
    }
}
