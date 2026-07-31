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
    private fun backloggedState(maxUnsettled: Int = 8): BoxState {
        var state = Box.state((1..50).map { Box.word(it) }, Box.config(maxUnsettled = maxUnsettled))
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
        val plan = SessionComposer.composeSession(backloggedState(), now, Box.TZ)
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
        val plan = SessionComposer.composeSession(backloggedState(), now, Box.TZ)
        assertEquals(setOf("w37", "w38", "w39", "w40"), plan.reviews.take(4).toSet())
        val secondDay = (13..36).map { "w$it" }.toSet()
        assertTrue(plan.reviews.drop(4).all { it in secondDay })
        assertEquals(25, plan.reviews.size)
    }

    @Test
    fun noReservationWithoutNewWork() {
        // cap 0 → growth switched off entirely and nothing enqueued → reviews fill the cap.
        val plan = SessionComposer.composeSession(backloggedState(maxUnsettled = 0), now, Box.TZ)
        assertEquals(30, plan.reviews.size)
        assertTrue(plan.newCards.isEmpty())
        assertTrue(plan.unlockedPhrases.isEmpty())
    }

    @Test
    fun newCardsNeverExceedRemainingSessionCapacity() {
        var state = Box.state((1..40).map { Box.word(it) }, Box.config(maxUnsettled = 20))
        for (n in 1..28) {
            val id = "w" + n.toString().padStart(2, '0')
            state = Box.inject(
                state,
                Box.sched(id, dueMillis = now - n * 60_000L, lastReviewMillis = Box.plusDays(now, -5.0)),
            )
        }
        val plan = SessionComposer.composeSession(state, now, Box.TZ)
        // reviewCap = 30 − min(20, 5) = 25 → 25 reviews; 5 card slots remain for new.
        assertEquals(25, plan.reviews.size)
        assertEquals(5, plan.newCards.size)
    }

    @Test
    fun determinismRegardlessOfInputOrder() {
        val cards = (1..30).map { Box.word(it, area = if (it % 2 == 0) "b" else "a") }
        val plan1 = SessionComposer.composeSession(Box.state(cards), now, Box.TZ)
        val plan2 = SessionComposer.composeSession(Box.state(cards), now, Box.TZ)
        val plan3 = SessionComposer.composeSession(Box.state(cards.shuffled()), now, Box.TZ)
        assertEquals(plan1, plan2)
        assertEquals(plan1, plan3)
        assertEquals(Box.stamp, plan1.joinStamp)
    }

    @Test
    fun drainLoopScenarioAMissedWordReturnsOnceThenLeaves() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)), Box.config(maxUnsettled = 2))

        // Introduce both; w01 is missed, w02 is known on sight.
        state = Box.answered(state, "w01", Rating.Again, now)
        state = Box.answered(state, "w02", Rating.Good, now)

        // w01 comes back once the step matures — past a short sitting; w02 went
        // straight to day scale and never re-enters the drain.
        assertTrue(BoxEngine.dueNow(state, Box.plusSeconds(now, 119)).isEmpty())
        var t = Box.plusSeconds(now, 120)
        assertEquals(listOf("w01"), BoxEngine.dueNow(state, t))
        assertEquals(listOf("w01"), BoxEngine.dueNow(state, Box.plusSeconds(now, 600)))

        // Missing it again repeats the same step; it does not shorten.
        state = Box.answered(state, "w01", Rating.Again, t)
        assertTrue(BoxEngine.dueNow(state, Box.plusSeconds(now, 239)).isEmpty())

        t = Box.plusSeconds(now, 240)
        assertEquals(listOf("w01"), BoxEngine.dueNow(state, t))

        // A Good takes it off the step and out of the drain for the day.
        state = Box.answered(state, "w01", Rating.Good, t)
        assertTrue(BoxEngine.dueNow(state, Box.plusSeconds(now, 86_400)).isEmpty())
    }

    @Test
    fun isEmptyReflectsAnAllEmptyPlan() {
        val plan = SessionComposer.composeSession(Box.state(emptyList()), now, Box.TZ)
        assertTrue(plan.isEmpty)
        assertEquals(Box.stamp, plan.joinStamp)
    }

    /** A loaded box with nothing due: growth is down to the trickle, so is the plan. */
    private fun loadedNothingDueState(actives: Int = 10, maxUnsettled: Int = 8): BoxState {
        var state = Box.state((1..30).map { Box.word(it) }, Box.config(maxUnsettled = maxUnsettled))
        for (n in 1..actives) {
            val id = "w" + n.toString().padStart(2, '0')
            state = Box.inject(
                state,
                Box.sched(
                    id,
                    stability = 0.5, // below settledStability → still unsettled
                    dueMillis = Box.plusDays(now, n.toDouble()),
                    lastReviewMillis = Box.plusDays(now, -1.0),
                ),
            )
        }
        return state
    }

    @Test
    fun aShortRoundIsFilledOutWithReviewsPulledForward() {
        // Nothing due, growth throttled to the trickle → 2 new words is not a round.
        val plan = SessionComposer.composeSession(loadedNothingDueState(), now, Box.TZ)
        assertTrue(plan.reviews.isEmpty())
        assertEquals(2, plan.freshCount)
        assertEquals(SessionComposer.SESSION_FLOOR_CARDS, plan.cardCount)
        // Soonest due first, and the run leads with them.
        assertEquals(listOf("w01", "w02", "w03", "w04"), plan.ahead)
        assertEquals(plan.ahead + plan.unlockedPhrases + plan.newCards, plan.queue)
    }

    @Test
    fun theFloorTakesWhatTheBoxHasAndNeverInventsWork() {
        // A full box with only 3 active cards to pull forward: 2 new + 3 ahead is
        // all there is, and the round stays short rather than reaching for more.
        val plan = SessionComposer.composeSession(
            loadedNothingDueState(actives = 3, maxUnsettled = 2),
            now,
            Box.TZ,
        )
        assertEquals(2, plan.freshCount)
        assertEquals(3, plan.ahead.size)
        assertEquals(5, plan.cardCount)
        // An empty box stays empty: "come back later" is a real answer.
        val nothing = SessionComposer.composeSession(Box.state(emptyList()), now, Box.TZ)
        assertTrue(nothing.isEmpty)
        assertTrue(nothing.ahead.isEmpty())
    }

    /**
     * A day with no reps in it is the one thing the box does not let pass quietly:
     * nothing due and nothing done means a round pulled forward, not a closed box.
     */
    @Test
    fun aDayWithNoRepsYetAlwaysGetsARound() {
        // maxUnsettled 0 → growth off, so an untouched day has literally nothing to offer.
        val state = loadedNothingDueState(maxUnsettled = 0)
        val plan = SessionComposer.composeSession(state, now, Box.TZ)
        assertTrue(plan.reviews.isEmpty())
        assertEquals(0, plan.freshCount)
        assertEquals(SessionComposer.SESSION_FLOOR_CARDS, plan.ahead.size)
    }

    @Test
    fun onceTheDayHasBeenWorkedAnEmptyPlanStaysEmpty() {
        val state = loadedNothingDueState(maxUnsettled = 0)
        val worked = BoxEngine.endSession(state, reviewsDone = 4, nowEpochMillis = now, tzId = Box.TZ)
        assertTrue(SessionComposer.composeSession(worked, now, Box.TZ).isEmpty)
        // …and the next day opens with a round again.
        val tomorrow = Box.plusDays(now, 1.0)
        assertTrue(SessionComposer.composeSession(worked, tomorrow, Box.TZ).ahead.isNotEmpty())
    }

    @Test
    fun aRoundThatAlreadyClearsTheFloorPullsNothingForward() {
        assertTrue(SessionComposer.composeSession(backloggedState(), now, Box.TZ).ahead.isEmpty())
        // Fresh box: nothing due, but the full budget of new words is a round in itself.
        val fresh = SessionComposer.composeSession(Box.state((1..30).map { Box.word(it) }), now, Box.TZ)
        assertEquals(20, fresh.freshCount)
        assertTrue(fresh.ahead.isEmpty())
    }
}
