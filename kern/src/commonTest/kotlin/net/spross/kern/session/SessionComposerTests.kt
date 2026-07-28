package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.model.Rating

/** Session composition: caps, ordering, growth reserve, determinism. */
class SessionComposerTests {
    private val now = Box.day1

    /** 40 review-phase cards with staggered past dues + 10 untouched words. */
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
    fun slotReservationForGrowth() {
        // 40 due cards, sessionCap 30 → 25 reviews + 5 reserved growth slots.
        val plan = SessionComposer.composeSession(backloggedState(), now)
        assertEquals(25, plan.reviews.size)
        assertEquals(5, plan.unlockedPhrases.size + plan.newCards.size)
        assertEquals((41..45).map { "w$it" }, plan.newCards)
    }

    /**
     * Whole overdue DAYS are drained in order; the shuffle inside a day is
     * [net.spross.kern.box.DueOrderTests]' subject, so this pins the buckets.
     */
    @Test
    fun reviewsDrainTheOldestDueDayFirst() {
        // 40 dues span three days back from noon: w37..w40, then w13..w36, then w01..w12.
        val plan = SessionComposer.composeSession(backloggedState(), now)
        assertEquals(setOf("w37", "w38", "w39", "w40"), plan.reviews.take(4).toSet())
        val secondDay = (13..36).map { "w$it" }.toSet()
        assertTrue(plan.reviews.drop(4).all { it in secondDay })
        assertEquals(25, plan.reviews.size)
    }

    @Test
    fun noReservationWithoutNewWork() {
        // maxLearning 0 → no growth budget and nothing enqueued → reviews fill the cap.
        val plan = SessionComposer.composeSession(backloggedState(maxLearning = 0), now)
        assertEquals(30, plan.reviews.size)
        assertTrue(plan.newCards.isEmpty())
        assertTrue(plan.unlockedPhrases.isEmpty())
    }

    @Test
    fun newCardsNeverExceedRemainingSessionCapacity() {
        var state = Box.state((1..40).map { Box.word(it) }, Box.config(maxLearning = 20))
        for (n in 1..28) {
            val id = "w" + n.toString().padStart(2, '0')
            state = Box.inject(
                state,
                Box.sched(id, dueMillis = now - n * 60_000L, lastReviewMillis = Box.plusDays(now, -5.0)),
            )
        }
        val plan = SessionComposer.composeSession(state, now)
        // reviewCap = 30 − min(20, 5) = 25 → 25 reviews; 5 card slots remain for new.
        assertEquals(25, plan.reviews.size)
        assertEquals(5, plan.newCards.size)
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
    fun drainLoopScenarioFailedCardsCycleUntilNothingDue() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)), Box.config(maxLearning = 2))

        // Introduce both; w01 fails, w02 passes (learning steps [1m, 10m]).
        state = Box.answered(state, "w01", Rating.Again, now)
        state = Box.answered(state, "w02", Rating.Good, now)

        // 1 min later only w01's again-step is due; failing again re-queues it.
        var t = Box.plusSeconds(now, 60)
        assertEquals(listOf("w01"), BoxEngine.dueNow(state, t))
        state = Box.answered(state, "w01", Rating.Again, t)

        // Its next 1-min step comes back at t+120; Good advances it to the 10-min step.
        t = Box.plusSeconds(now, 120)
        assertEquals(listOf("w01"), BoxEngine.dueNow(state, t))
        state = Box.answered(state, "w01", Rating.Good, t)

        // 10 min after intro w02's step is due; Good graduates it out of the drain.
        t = Box.plusSeconds(now, 600)
        assertEquals(listOf("w02"), BoxEngine.dueNow(state, t))
        state = Box.answered(state, "w02", Rating.Good, t)
        assertTrue(BoxEngine.dueNow(state, Box.plusSeconds(now, 660)).isEmpty())

        // w01's 10-min step lands at intro+720; graduating it empties the drain for good.
        t = Box.plusSeconds(now, 720)
        assertEquals(listOf("w01"), BoxEngine.dueNow(state, t))
        state = Box.answered(state, "w01", Rating.Good, t)
        assertTrue(BoxEngine.dueNow(state, Box.plusSeconds(now, 780)).isEmpty())
    }

    @Test
    fun isEmptyReflectsAnAllEmptyPlan() {
        val plan = SessionComposer.composeSession(Box.state(emptyList()), now)
        assertTrue(plan.isEmpty)
        assertEquals(Box.stamp, plan.joinStamp)
    }
}
