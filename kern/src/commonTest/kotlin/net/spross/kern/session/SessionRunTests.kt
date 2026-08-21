package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.BoxStatistics
import net.spross.kern.box.StreakHealth
import net.spross.kern.box.dayKey
import net.spross.kern.model.CardPhase
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Rating

/**
 * The session run: the promise the composed queue makes, the summary tallies,
 * the extra round, endless refill, folds, and stale joins.
 */
class SessionRunTests {
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

    /** Nothing due; [soon] cards come back inside tomorrow, [catalog] words are unseen. */
    private fun quietState(soon: Int, catalog: Int): BoxState {
        var state = Box.state((1..catalog).map { Box.word(it) })
        repeat(soon) { i ->
            state = Box.inject(
                state,
                Box.sched(
                    id(i + 1),
                    stability = 0.5,
                    dueMillis = Box.plusSeconds(now, 3_600L * (i + 1)),
                    lastReviewMillis = Box.plusDays(now, -1.0),
                ),
            )
        }
        return state
    }

    private fun started(state: BoxState, nowMillis: Long): SessionRunState =
        SessionRun.reduce(SessionRun.idle(state), SessionIntent.Start, nowMillis, Box.TZ).state

    private fun answer(run: SessionRunState, rating: Rating, nowMillis: Long): SessionRunState =
        SessionRun.reduce(run, SessionIntent.Answer(rating), nowMillis, Box.TZ).state

    private fun dayReviews(run: SessionRunState): Int =
        run.box.dailyStats[dayKey(now, Box.TZ)]?.reviews ?: 0

    /**
     * The composed queue IS the run: 20 of 40 due cards enter, and the 20 the cap held back
     * never join while the learner is sitting there — the count on screen is a promise.
     */
    @Test
    fun nothingJoinsARunAlreadyUnderWay() {
        var run = started(backloggedState(), now)
        assertEquals(25, run.total)
        while (run.currentCardId != null) {
            run = answer(run, Rating.Good, now)
            assertEquals(25, run.total)
        }
        assertEquals(25, run.answered)
        assertTrue(run.finished)
        assertEquals(SessionStep.Completed, run.step)
        // The held-back work is still due — the summary is where it gets offered.
        assertEquals(20, BoxEngine.dueNow(run.box, now).size)
        assertTrue(SessionOffers.canPracticeMore(run.box, now, Box.TZ))
    }

    /** A card that leaves the box under the run shrinks the promise instead of stalling it. */
    @Test
    fun aDroppedAnswerShrinksTheTotal() {
        val run = started(backloggedState(), now)
        val cardId = assertNotNull(run.currentCardId)
        val pruned = SessionRun.withBox(run, run.box.copy(cards = run.box.cards - cardId))
        val after = answer(pruned, Rating.Good, now)
        assertEquals(24, after.total)
        assertEquals(0, after.answered)
        assertTrue(after.ratings.isEmpty())
        assertTrue(after.currentCardId != null && after.currentCardId != cardId)
    }

    /**
     * …but never past the answers already booked. The ratings ARE the progress bar's
     * parts, so a total under them is a run drawing more of itself than it says it
     * holds — a counter reading "20/20" beside twenty-one segments. The promise, the
     * answers and what is left stay one arithmetic through every drop.
     */
    @Test
    fun aDropNeverLeavesMoreAnswersThanTheRunClaimsToHold() {
        var run = started(backloggedState(), now)
        repeat(10) { run = answer(run, Rating.Good, now) }
        // Five cards still ahead leave the box under the run, then the last one does.
        val ahead = run.queue.take(5).toSet()
        run = SessionRun.withBox(run, run.box.copy(cards = run.box.cards - ahead))

        while (run.currentCardId != null) {
            if (run.queue.size == 1) {
                run = SessionRun.withBox(run, run.box.copy(cards = run.box.cards - run.queue.first()))
            }
            run = answer(run, Rating.Good, now)
            assertTrue(
                run.segments.size <= run.total,
                "${run.segments.size} segments in a run of ${run.total}",
            )
            assertEquals(run.total, run.answered + run.remaining)
            assertTrue(run.position <= run.total)
        }
        // 25 composed, 6 gone: the promise pays for the answers and nothing more.
        assertEquals(19, run.answered)
        assertEquals(19, run.total)
    }

    /**
     * The tally boundary: one learning step puts a first-sight Good straight into Review while
     * its stability is still tiny, so the phase edge is not the signal — the CROSSING into
     * consolidated is.
     */
    @Test
    fun graduationIsTheCrossingNotThePhaseEdge() {
        var run = started(Box.state(listOf(Box.word(1))), now)
        assertEquals("w01", run.currentCardId)
        run = answer(run, Rating.Good, now)

        assertEquals(CardPhase.Review, run.box.scheduling.getValue("w01").phase)
        assertFalse(BoxEngine.isConsolidated(run.box, "w01"))
        assertEquals(1, run.newCards)
        assertEquals(0, run.graduated)
        assertEquals(0, run.reviews)

        // Second pass, known on sight: stability crosses the consolidated bar → "graduated".
        val later = Box.plusDays(now, 7.0)
        run = SessionRun.reduce(run, SessionIntent.Close, later, Box.TZ).state
        run = started(run.box, later)
        assertEquals("w01", run.currentCardId)
        run = answer(run, Rating.Easy, later)
        assertTrue(BoxEngine.isConsolidated(run.box, "w01"))
        assertEquals(0, run.newCards)
        assertEquals(1, run.graduated)
        assertEquals(0, run.reviews)

        // Third pass: already consolidated, nothing crosses — a plain review rep.
        val muchLater = Box.plusDays(now, 120.0)
        run = SessionRun.reduce(run, SessionIntent.Close, muchLater, Box.TZ).state
        run = started(run.box, muchLater)
        assertEquals("w01", run.currentCardId)
        run = answer(run, Rating.Good, muchLater)
        assertEquals(0, run.graduated)
        assertEquals(1, run.reviews)
    }

    /**
     * The extra round is an ordinary round: recall pulled forward AND new words, in the mix the
     * box asks for. It used to be composed by rules of its own and kept arriving as one extreme
     * or the other — all first sights, or all cards dragged forward.
     */
    @Test
    fun theExtraRoundMixesRecallWithFirstSights() {
        val state = quietState(soon = 3, catalog = 20)
        val run = SessionRun.reduce(SessionRun.idle(state), SessionIntent.StartExtra, now, Box.TZ).state
        assertEquals(listOf("w01", "w02", "w03"), run.queue.take(3))
        assertEquals(SessionComposer.SESSION_FLOOR_CARDS, run.total)
        assertTrue(run.active)
        assertFalse(run.finished)
    }

    /** Nothing to compose, nothing to start: the extra round leaves the state alone. */
    @Test
    fun anEmptyExtraRoundIsANoOp() {
        val idle = SessionRun.idle(Box.state(emptyList()))
        val reduction = SessionRun.reduce(idle, SessionIntent.StartExtra, now, Box.TZ)
        assertEquals(idle, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    /** Endless refills only once asked for, and an answer with nothing on screen is a no-op. */
    @Test
    fun endlessRefillsOnlyOnceAskedFor() {
        var run = started(backloggedState(), now)
        while (run.currentCardId != null) run = answer(run, Rating.Good, now)
        assertTrue(run.finished)
        assertFalse(run.endless)

        val ignored = SessionRun.reduce(run, SessionIntent.Answer(Rating.Good), now, Box.TZ)
        assertEquals(run, ignored.state)
        assertTrue(ignored.effects.isEmpty())

        val more = SessionRun.reduce(run, SessionIntent.ContinueEndless, now, Box.TZ).state
        assertTrue(more.endless)
        assertFalse(more.finished)
        assertTrue(more.total > 25)
        assertNotNull(more.currentCardId)
    }

    /**
     * A dry refill leaves the run on its summary — asked for, but nothing to hand over.
     *
     * Dry now means a box with nothing left AT ALL: a refill is an ordinary round, so a single
     * card answered minutes ago still comes back as a pull-ahead. Here the catalog is taken out
     * from under the run, the way a source switch does.
     */
    @Test
    fun aDryEndlessRefillStaysOnTheSummary() {
        var run = started(Box.state(listOf(Box.word(1))), now)
        run = answer(run, Rating.Good, now)
        assertTrue(run.finished)
        // Still one active card, so the refill has a round in it …
        assertTrue(SessionOffers.canPracticeMore(run.box, now, Box.TZ))

        // … until there is nothing to compose from.
        val emptied = SessionRun.withBox(run, Box.state(emptyList()))
        val asked = SessionRun.reduce(emptied, SessionIntent.ContinueEndless, now, Box.TZ).state
        assertTrue(asked.endless)
        assertTrue(asked.finished)
        assertEquals(SessionStep.Completed, asked.step)
    }

    /** A backgrounded run keeps its streak-bearing reviews; later folds book the delta only. */
    @Test
    fun aPartialFoldBooksOnlyTheDelta() {
        var run = started(backloggedState(), now)
        repeat(3) { run = answer(run, Rating.Good, now) }

        val folded = SessionRun.reduce(run, SessionIntent.FoldPartial, now, Box.TZ)
        assertEquals(3, folded.state.folded)
        assertEquals(3, dayReviews(folded.state))
        assertTrue(SessionEffect.Persist(true) in folded.effects)
        assertTrue(SessionEffect.DayBooked in folded.effects)

        val again = SessionRun.reduce(folded.state, SessionIntent.FoldPartial, now, Box.TZ)
        assertEquals(folded.state, again.state)
        assertTrue(again.effects.isEmpty())

        var run2 = folded.state
        repeat(2) { run2 = answer(run2, Rating.Good, now) }
        val done = SessionRun.reduce(run2, SessionIntent.Finish, now, Box.TZ)
        assertEquals(5, done.state.answered)
        assertEquals(5, done.state.folded)
        assertEquals(5, dayReviews(done.state))
        // Finishing twice never books the day twice.
        assertEquals(5, dayReviews(SessionRun.reduce(done.state, SessionIntent.Finish, now, Box.TZ).state))
    }

    /** The join moved under the run (source switch, catalog update) → recompose, keep the count honest. */
    @Test
    fun aMovedJoinRecomposesAgainstTheLiveOne() {
        var run = started(backloggedState(), now)
        run = answer(run, Rating.Good, now)
        val rejoined = BoxEngine.rejoin(
            run.box,
            run.box.cards.values.toList(),
            JoinStamp("de", "sw", "fixture-v2"),
        )

        val fresh = SessionRun.reduce(
            SessionRun.withBox(run, rejoined),
            SessionIntent.RecomposeIfStale,
            now,
            Box.TZ,
        ).state
        assertEquals(rejoined.joinStamp, fresh.joinStamp)
        assertEquals(fresh.answered + fresh.queue.size, fresh.total)
        assertNotNull(fresh.currentCardId)

        // A stamp that still matches is left alone.
        val untouched = SessionRun.reduce(fresh, SessionIntent.RecomposeIfStale, now, Box.TZ)
        assertEquals(fresh, untouched.state)
        assertTrue(untouched.effects.isEmpty())
    }

    /** Closing books what was answered and keeps the summary's content for its way out. */
    @Test
    fun closingBooksWhatWasAnswered() {
        var run = started(backloggedState(), now)
        repeat(2) { run = answer(run, Rating.Good, now) }

        val closed = SessionRun.reduce(run, SessionIntent.Close, now, Box.TZ)
        assertTrue(closed.state.finished)
        assertFalse(closed.state.active)
        assertFalse(closed.state.endless)
        assertEquals(2, dayReviews(closed.state))
        assertTrue(closed.state.step is SessionStep.Card)
        assertTrue(SessionEffect.DayBooked in closed.effects)

        // An untouched run books nothing at all.
        val quiet = SessionRun.reduce(started(backloggedState(), now), SessionIntent.Close, now, Box.TZ)
        assertTrue(quiet.state.box.dailyStats.isEmpty())
    }

    /** Answers persist as they land; only the fold flushes immediately. */
    @Test
    fun everyAnswerAsksForASave() {
        val run = started(backloggedState(), now)
        val reduction = SessionRun.reduce(run, SessionIntent.Answer(Rating.Good), now, Box.TZ)
        assertTrue(SessionEffect.Persist(false) in reduction.effects)
        assertFalse(SessionEffect.DayBooked in reduction.effects)
    }

    @Test
    fun positionAndSegmentsFollowTheAnswers() {
        var run = started(backloggedState(), now)
        assertEquals(1, run.position)
        run = answer(run, Rating.Again, now)
        run = answer(run, Rating.Hard, now)
        run = answer(run, Rating.Easy, now)
        assertEquals(listOf(AnswerOutcome.Wrong, AnswerOutcome.Almost, AnswerOutcome.Right), run.segments)
        assertEquals(4, run.position)
        assertEquals(22, run.remaining)
    }

    /** A first day is not a record: every box has one. */
    @Test
    fun aRecordStreakNeedsMoreThanADay() {
        fun stats(streak: Int, longest: Int) = BoxStatistics(
            activeCount = 0, consolidatedCount = 0, dueCount = 0, suspendedCount = 0,
            streak = streak, streakHealth = StreakHealth.Earned, longestStreak = longest, areas = emptyList(),
        )
        assertFalse(SessionRun.streakIsRecord(stats(1, 1)))
        assertFalse(SessionRun.streakIsRecord(stats(3, 5)))
        assertTrue(SessionRun.streakIsRecord(stats(3, 3)))
    }
}
