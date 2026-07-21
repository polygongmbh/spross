package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating
import net.spross.kern.model.Role

/** Session composition: caps, unit ordering, one-per-card, determinism. */
class SessionComposerTests {
    private val now = Box.day1

    /** 40 review-phase produce units with staggered past dues + 10 untouched words. */
    private fun backloggedState(maxLearning: Int = 8): BoxState {
        var state = Box.state((1..50).map { Box.word(it) }, Box.config(maxLearning = maxLearning))
        for (n in 1..40) {
            val id = "w" + n.toString().padStart(2, '0')
            state = Box.inject(
                state,
                Box.sched(
                    id,
                    dueMillis = now - n * 3_600_000L,
                    lastReviewMillis = Box.plusDays(now, -10.0),
                ),
            )
        }
        return state
    }

    @Test
    fun slotReservationInUnits() {
        // 40 due units, sessionCap 30 → 25 reviews + 5 reserved growth slots.
        val plan = SessionComposer.composeSession(backloggedState(), now)
        assertEquals(25, plan.reviews.size)
        assertEquals(5, plan.unlockedPhrases.size + plan.newUnits.size)
        // Recognize backfill of the graduated (review-phase) material leads new
        // produce via its lower seed index.
        assertEquals((1..5).map { Box.recognize("w0$it", "t$it") }, plan.newUnits)
    }

    @Test
    fun reviewsAreOldestDueFirstTieByKey() {
        val plan = SessionComposer.composeSession(backloggedState(), now)
        assertEquals(
            (0 until 25).map { Box.produce("w" + (40 - it).toString().padStart(2, '0')) },
            plan.reviews,
        )

        var tied = Box.state(listOf(Box.word(1), Box.word(2)))
        val due = now - 3_600_000L
        val past = Box.plusDays(now, -1.0)
        tied = Box.inject(tied, Box.sched("w02", dueMillis = due, lastReviewMillis = past))
        tied = Box.inject(tied, Box.sched("w01", dueMillis = due, lastReviewMillis = past))
        assertEquals(listOf(Box.produce("w01"), Box.produce("w02")), BoxEngine.dueNow(tied, now))
    }

    @Test
    fun noReservationWithoutNewWork() {
        // maxLearning 0 → no growth budget and nothing enqueued → reviews fill the cap.
        val plan = SessionComposer.composeSession(backloggedState(maxLearning = 0), now)
        assertEquals(30, plan.reviews.size)
        assertTrue(plan.newUnits.isEmpty())
        assertTrue(plan.unlockedPhrases.isEmpty())
    }

    @Test
    fun newUnitsNeverExceedRemainingSessionCapacity() {
        var state = Box.state((1..40).map { Box.word(it) }, Box.config(maxLearning = 20))
        for (n in 1..28) {
            val id = "w" + n.toString().padStart(2, '0')
            state = Box.inject(
                state,
                Box.sched(id, dueMillis = now - n * 60_000L, lastReviewMillis = Box.plusDays(now, -5.0)),
            )
        }
        val plan = SessionComposer.composeSession(state, now)
        // reviewCap = 30 − min(20, 5) = 25 → 25 reviews; 5 unit slots remain for new.
        assertEquals(25, plan.reviews.size)
        assertEquals(5, plan.newUnits.size)
    }

    @Test
    fun atMostOneUnitPerCardPerPlanSiblingDefers() {
        var state = Box.state((1..3).map { Box.word(it) })
        val past = Box.plusDays(now, -1.0)
        state = Box.inject(state, Box.sched("w02", dueMillis = now - 3 * 3_600_000L, lastReviewMillis = past))
        state = Box.inject(state, Box.sched("w01", dueMillis = now - 2 * 3_600_000L, lastReviewMillis = past))
        state = Box.inject(
            state,
            Box.sched("w01", role = Role.Recognize, form = "t1", dueMillis = now - 3_600_000L, lastReviewMillis = past),
        )
        val plan = SessionComposer.composeSession(state, now)
        // w01's recognize sibling (younger due) defers to the next composition …
        assertEquals(listOf(Box.produce("w02"), Box.produce("w01")), plan.reviews)
        // … and w02's introducible recognize unit is excluded (its card holds a review
        // slot); the freed capacity backfills with the next seed-order candidate.
        assertEquals(listOf(Box.produce("w03")), plan.newUnits)
    }

    @Test
    fun drainIsExemptSiblingsMayInterleave() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(
            state,
            Box.sched("w01", phase = CardPhase.Relearning, dueMillis = now - 60_000, lastReviewMillis = now - 120_000),
        )
        state = Box.inject(
            state,
            Box.sched(
                "w01", role = Role.Recognize, form = "t1",
                phase = CardPhase.Learning, dueMillis = now - 30_000, lastReviewMillis = now - 120_000,
            ),
        )
        assertEquals(listOf(Box.produce("w01"), Box.recognize("w01", "t1")), BoxEngine.dueNow(state, now))
    }

    @Test
    fun determinismRegardlessOfInputOrder() {
        val cards = (1..30).map { Box.word(it, area = if (it % 2 == 0) "b" else "a") }
        val plan1 = SessionComposer.composeSession(Box.state(cards), now)
        val plan2 = SessionComposer.composeSession(Box.state(cards), now)
        val plan3 = SessionComposer.composeSession(Box.state(cards.shuffled()), now)
        assertEquals(plan1, plan2)
        assertEquals(plan1, plan3)
        assertEquals(Box.stamp, plan1.joinStamp)
    }

    @Test
    fun drainLoopScenarioFailedUnitsCycleUntilNothingDue() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)), Box.config(maxLearning = 2))

        // Introduce both; w01 fails, w02 passes (learning steps [1m, 10m]).
        state = Box.answered(state, Box.produce("w01"), Rating.Again, now)
        state = Box.answered(state, Box.produce("w02"), Rating.Good, now)

        // 1 min later only w01's again-step is due; failing again re-queues it.
        var t = Box.plusSeconds(now, 60)
        assertEquals(listOf(Box.produce("w01")), BoxEngine.dueNow(state, t))
        state = Box.answered(state, Box.produce("w01"), Rating.Again, t)

        // Its next 1-min step comes back at t+120; Good advances it to the 10-min step.
        t = Box.plusSeconds(now, 120)
        assertEquals(listOf(Box.produce("w01")), BoxEngine.dueNow(state, t))
        state = Box.answered(state, Box.produce("w01"), Rating.Good, t)

        // 10 min after intro w02's step is due; Good graduates it out of the drain.
        t = Box.plusSeconds(now, 600)
        assertEquals(listOf(Box.produce("w02")), BoxEngine.dueNow(state, t))
        state = Box.answered(state, Box.produce("w02"), Rating.Good, t)
        assertTrue(BoxEngine.dueNow(state, Box.plusSeconds(now, 660)).isEmpty())

        // w01's 10-min step lands at intro+720; graduating it empties the drain for good.
        t = Box.plusSeconds(now, 720)
        assertEquals(listOf(Box.produce("w01")), BoxEngine.dueNow(state, t))
        state = Box.answered(state, Box.produce("w01"), Rating.Good, t)
        assertTrue(BoxEngine.dueNow(state, Box.plusSeconds(now, 780)).isEmpty())
    }

    @Test
    fun isEmptyReflectsAnAllEmptyPlan() {
        val plan = SessionComposer.composeSession(Box.state(emptyList()), now)
        assertTrue(plan.isEmpty)
        assertEquals(Box.stamp, plan.joinStamp)
    }
}
