package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.model.Rating

/** Session composition: caps, ordering, growth reserve, the quiet-day balance, determinism. */
class SessionComposerTests {
    private val now = Box.day1

    private fun id(n: Int) = "w" + n.toString().padStart(2, '0')

    /** 40 review-phase cards with staggered past dues + [spare] untouched words. */
    private fun backloggedState(spare: Int = 10): BoxState {
        var state = Box.state((1..(40 + spare)).map { Box.word(it) })
        for (n in 1..40) {
            state = Box.inject(
                state,
                Box.sched(
                    id(n),
                    dueMillis = now - n * 3_600_000L,
                    lastReviewMillis = Box.plusDays(now, -10.0),
                ),
            )
        }
        return state
    }

    @Test
    fun slotReservationForGrowth() {
        // 40 due cards, sessionCap 25 → 20 reviews + 5 reserved growth slots.
        val plan = SessionComposer.composeSession(backloggedState(), now, Box.TZ)
        assertEquals(20, plan.reviews.size)
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
        assertEquals(20, plan.reviews.size)
    }

    @Test
    fun noReservationWithoutNewWork() {
        // Every card already scheduled → nothing to introduce → reviews take the whole cap.
        val plan = SessionComposer.composeSession(backloggedState(spare = 0), now, Box.TZ)
        assertEquals(25, plan.reviews.size)
        assertTrue(plan.newCards.isEmpty())
        assertTrue(plan.unlockedPhrases.isEmpty())
    }

    @Test
    fun newCardsNeverExceedRemainingSessionCapacity() {
        var state = Box.state((1..40).map { Box.word(it) })
        for (n in 1..22) {
            state = Box.inject(
                state,
                Box.sched(id(n), dueMillis = now - n * 60_000L, lastReviewMillis = Box.plusDays(now, -5.0)),
            )
        }
        val plan = SessionComposer.composeSession(state, now, Box.TZ)
        // reviewCap = 25 − 5 = 20 → 20 reviews; 5 card slots remain for new.
        assertEquals(20, plan.reviews.size)
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
        var state = Box.state(listOf(Box.word(1), Box.word(2)))

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

    /**
     * Nothing due right now: [soon] cards come due inside tomorrow, [later] in five days
     * and beyond. [catalog] sets how much unseen material is left behind them.
     */
    private fun quietBox(soon: Int, later: Int, catalog: Int = 30): BoxState {
        var state = Box.state((1..catalog).map { Box.word(it) })
        var n = 0
        repeat(soon) { i ->
            n += 1
            state = Box.inject(
                state,
                Box.sched(
                    id(n),
                    stability = 0.5,
                    dueMillis = Box.plusSeconds(now, 3_600L * (i + 1)),
                    lastReviewMillis = Box.plusDays(now, -1.0),
                ),
            )
        }
        repeat(later) { i ->
            n += 1
            state = Box.inject(
                state,
                Box.sched(
                    id(n),
                    dueMillis = Box.plusDays(now, 5.0 + i),
                    lastReviewMillis = Box.plusDays(now, -1.0),
                ),
            )
        }
        return state
    }

    @Test
    fun aQuietDayBalancesTomorrowAgainstNewWords() {
        // Half the floor is held for cards that come due inside tomorrow; new words take
        // the rest, so the round is a mix rather than a wall of first sights.
        val plan = SessionComposer.composeSession(quietBox(soon = 5, later = 0), now, Box.TZ)
        assertTrue(plan.reviews.isEmpty())
        assertEquals(4, plan.freshCount)
        assertEquals(listOf("w01", "w02", "w03"), plan.ahead)
        assertEquals(SessionComposer.SESSION_FLOOR_CARDS, plan.cardCount)
        assertEquals(plan.ahead + plan.unlockedPhrases + plan.newCards, plan.queue)
    }

    @Test
    fun nothingDueTomorrowMeansNewWordsAlone() {
        // Nothing coming due inside tomorrow also means nothing was recently missed —
        // there is no warm-up to offer, so the round is new words.
        val plan = SessionComposer.composeSession(quietBox(soon = 0, later = 5), now, Box.TZ)
        assertTrue(plan.ahead.isEmpty())
        assertEquals(SessionComposer.NEW_CARDS_PER_ROUND, plan.freshCount)
    }

    @Test
    fun theRoundReachesPastTomorrowOnlyWhenNothingElseFillsIt() {
        // Catalog exhausted and nothing due until day five: rather than an empty screen,
        // the round pulls those forward. The niche case, never the everyday one.
        val plan = SessionComposer.composeSession(
            quietBox(soon = 0, later = 5, catalog = 5),
            now,
            Box.TZ,
        )
        assertEquals(0, plan.freshCount)
        assertEquals(5, plan.ahead.size)
        assertEquals(5, plan.cardCount)
    }

    @Test
    fun theFloorTakesWhatTheBoxHasAndNeverInventsWork() {
        // Three active cards and nothing unseen left: three is the whole round.
        val plan = SessionComposer.composeSession(quietBox(soon = 3, later = 0, catalog = 3), now, Box.TZ)
        assertEquals(0, plan.freshCount)
        assertEquals(3, plan.ahead.size)
        // An empty box stays empty: "come back later" is a real answer.
        val nothing = SessionComposer.composeSession(Box.state(emptyList()), now, Box.TZ)
        assertTrue(nothing.isEmpty)
        assertTrue(nothing.ahead.isEmpty())
    }

    /**
     * A day the learner has not really worked is never closed: nothing due plus nothing
     * done means a round, not a finish line. A round's worth of answers is the bar —
     * one tap used to be enough, which closed a day nobody had worked.
     */
    @Test
    fun aDayIsOnlyDoneOnceARoundsWorthIsDone() {
        val state = quietBox(soon = 3, later = 0)
        val floor = SessionComposer.SESSION_FLOOR_CARDS

        val barelyStarted = BoxEngine.endSession(state, reviewsDone = floor - 1, nowEpochMillis = now, tzId = Box.TZ)
        assertEquals(floor, SessionComposer.composeSession(barelyStarted, now, Box.TZ).cardCount)

        val worked = BoxEngine.endSession(state, reviewsDone = floor, nowEpochMillis = now, tzId = Box.TZ)
        assertTrue(SessionComposer.composeSession(worked, now, Box.TZ).isEmpty)
        // …and the next day opens with a round again.
        val tomorrow = Box.plusDays(now, 1.0)
        assertTrue(SessionComposer.composeSession(worked, tomorrow, Box.TZ).cardCount > 0)
    }

    @Test
    fun cardsThePlayerPackedThemselvesStillEnterOnAFinishedDay() {
        // Automatic growth rests once the day is done; an explicit "pack in die Box" does not.
        val state = BoxEngine.enqueue(quietBox(soon = 3, later = 0), listOf("w20"))
        val worked = BoxEngine.endSession(
            state,
            reviewsDone = SessionComposer.SESSION_FLOOR_CARDS,
            nowEpochMillis = now,
            tzId = Box.TZ,
        )
        val plan = SessionComposer.composeSession(worked, now, Box.TZ)
        assertEquals(listOf("w20"), plan.newCards)
        assertTrue(plan.ahead.isEmpty())
    }

    @Test
    fun aRoundThatAlreadyClearsTheFloorPullsNothingForward() {
        assertTrue(SessionComposer.composeSession(backloggedState(), now, Box.TZ).ahead.isEmpty())
        // Fresh box: nothing due, but a round's worth of new words is a round in itself.
        val fresh = SessionComposer.composeSession(Box.state((1..30).map { Box.word(it) }), now, Box.TZ)
        assertEquals(SessionComposer.NEW_CARDS_PER_ROUND, fresh.freshCount)
        assertTrue(fresh.ahead.isEmpty())
    }

    /** The round cap is what keeps a rested box from arriving as one wall of first sights. */
    @Test
    fun growthNeverExceedsARoundsWorthOfNewCards() {
        val restedBox = Box.state((1..40).map { Box.word(it) })
        assertEquals(
            SessionComposer.NEW_CARDS_PER_ROUND,
            SessionComposer.composeSession(restedBox, now, Box.TZ).freshCount,
        )
        assertEquals(
            SessionComposer.NEW_CARDS_PER_ROUND,
            SessionComposer.composeEndless(restedBox, now).freshCount,
        )
        // Enqueued cards lead composition, but the round holds them to the same size.
        val packed = restedBox.copy(enqueued = (1..12).map { id(it) })
        assertEquals(
            SessionComposer.NEW_CARDS_PER_ROUND,
            SessionComposer.composeExtraSession(packed, now).freshCount,
        )
    }
}
