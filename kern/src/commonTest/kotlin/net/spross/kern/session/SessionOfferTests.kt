package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxState
import net.spross.kern.box.dayKey
import net.spross.kern.model.DayStats

/** Round classification, the counts behind it, and a headline pick that survives a relaunch. */
class SessionOfferTests {
    private val now = Box.day1

    private fun id(n: Int) = "w" + n.toString().padStart(2, '0')

    /** This round's line at the scenario clock. */
    private fun SessionOffer.line() = headline(now, Box.TZ)

    /** [due] cards overdue, [ahead] due later, [catalog] words in total. */
    private fun state(due: Int, ahead: Int, catalog: Int, sessionCap: Int): BoxState {
        var state = Box.state((1..catalog).map { Box.word(it) }, Box.config(sessionCap))
        var n = 0
        repeat(due) {
            n += 1
            state = Box.inject(
                state,
                Box.sched(id(n), dueMillis = now - n * 3_600_000L, lastReviewMillis = Box.plusDays(now, -10.0)),
            )
        }
        repeat(ahead) { i ->
            n += 1
            state = Box.inject(
                state,
                Box.sched(id(n), dueMillis = Box.plusDays(now, 3.0 + i), lastReviewMillis = Box.plusDays(now, -1.0)),
            )
        }
        return state
    }

    /** A rested box offers first sights, and they outnumber everything there is to recall. */
    @Test
    fun freshWorkLeadsWhenItOutnumbersRecall() {
        val offer = SessionOffers.offer(state(due = 0, ahead = 0, catalog = 30, sessionCap = 24), now, Box.TZ)
        assertEquals(SessionOfferKind.FreshSet, offer.kind)
        assertEquals(SessionComposer.NEW_CARDS_PER_ROUND, offer.fresh)
        assertEquals(0, offer.reviews)
        assertEquals(0, offer.dueHeldBack)
    }

    /** A backlog leads, and the cap's leftovers are named rather than hidden. */
    @Test
    fun recallLeadsAndTheCapsLeftoversAreNamed() {
        val offer = SessionOffers.offer(state(due = 40, ahead = 0, catalog = 50, sessionCap = 24), now, Box.TZ)
        assertEquals(SessionOfferKind.Reviews, offer.kind)
        assertEquals(20, offer.reviews)
        assertEquals(20, offer.dueHeldBack)
        assertEquals(4, offer.fresh)
    }

    /**
     * A handful over the cap goes unsaid: a box in good health nearly always carries a few,
     * so naming them would put an arrears notice on an ordinary day.
     * [recallLeadsAndTheCapsLeftoversAreNamed] holds the other side of the line.
     */
    @Test
    fun aRemainderTooSmallToNameIsNotNamed() {
        val offer = SessionOffers.offer(state(due = 25, ahead = 0, catalog = 50, sessionCap = 24), now, Box.TZ)
        assertEquals(20, offer.reviews)
        assertEquals(0, offer.dueHeldBack)
    }

    /** One or two due cards are a warm-up, never the round's headline. */
    @Test
    fun aTokenCoupleOfDueCardsIsAWarmUp() {
        val offer = SessionOffers.offer(state(due = 2, ahead = 3, catalog = 5, sessionCap = 24), now, Box.TZ)
        assertEquals(SessionOfferKind.WarmUp, offer.kind)
        assertEquals(2, offer.reviews)
        assertEquals(3, offer.ahead)
        assertEquals(0, offer.fresh)
        assertEquals(0, offer.dueHeldBack)

        // One more due card and recall takes the lead.
        val leading = SessionOffers.offer(state(due = 3, ahead = 2, catalog = 5, sessionCap = 24), now, Box.TZ)
        assertEquals(SessionOffer.REVIEWS_LEAD_FROM, leading.reviews)
        assertEquals(SessionOfferKind.Reviews, leading.kind)
    }

    /** An empty round has no words of its own; the headline still names a kind. */
    @Test
    fun anEmptyRoundBorrowsTheFreshSetHeadline() {
        val offer = SessionOffers.offer(Box.state(emptyList()), now, Box.TZ)
        assertEquals(SessionOfferKind.Nothing, offer.kind)
        assertEquals(HeadlineKind.FreshSet, offer.line().kind)
        assertTrue(offer.line().variant in 0 until offer.line().kind.variants)
    }

    /**
     * A long round names what a short one would hand over instead; a round the learner can
     * finish in a sitting names nothing, because there is no second way in to offer.
     */
    @Test
    fun onlyALongRoundOffersAShortOne() {
        val behind = SessionOffers.offer(state(due = 40, ahead = 0, catalog = 50, sessionCap = 24), now, Box.TZ)
        assertEquals(SessionComposer.SHORT_ROUND_CARDS, behind.shortRound)

        val rested = SessionOffers.offer(state(due = 0, ahead = 0, catalog = 30, sessionCap = 24), now, Box.TZ)
        assertEquals(0, rested.shortRound)
    }

    /**
     * The headline turns on the round's shape and the day's work so far, and on nothing else:
     * same counts, same variant, every run — a runtime-seeded hash would re-roll the line on
     * every launch, and the two platforms would disagree.
     */
    @Test
    fun theHeadlinePickIsStableAndSpreadAcrossVariants() {
        val offer = SessionOffer(SessionOfferKind.Reviews, reviews = 20, dueHeldBack = 20, ahead = 0, fresh = 4, shortRound = 7)
        assertEquals(offer.line(), offer.copy(dueHeldBack = 3).line())
        assertEquals(offer.line(), offer.copy(shortRound = 0).line())
        assertEquals(offer.line(), SessionOffers.offer(state(40, 0, 50, 24), now, Box.TZ).line())

        val variants = (0..40).flatMap { reviews ->
            (0..7).map { fresh ->
                SessionOffer(SessionOfferKind.Reviews, reviews, 0, 0, fresh, shortRound = 0).line().variant
            }
        }
        assertTrue(variants.all { it in 0 until HeadlineKind.Reviews.variants })
        assertEquals(HeadlineKind.Reviews.variants, variants.toSet().size)
    }

    /**
     * A day's second round of the same make gets a different line: the shape alone left the
     * card frozen on the words the learner just finished reading, and a screen that never
     * moves stops being read.
     */
    @Test
    fun aFinishedRoundMovesTheHeadlineOn() {
        val first = SessionOffer(SessionOfferKind.Reviews, reviews = 12, dueHeldBack = 0, ahead = 0, fresh = 3, shortRound = 0)
        val variants = (0..6).map { first.copy(doneToday = it * 12).line().variant }
        assertTrue(variants.toSet().size > 1, "a day's rounds all headlined the same: $variants")
        // Still fixed per day-state: the same round read twice never re-rolls between renders.
        assertEquals(first.line(), first.copy().line())
    }

    /**
     * Late in a day that has not paid into a standing run, the card says so instead of naming
     * the round — the round keeps until tomorrow and the run does not.
     */
    @Test
    fun anExposedRunTakesOverTheHeadlineAfterTheMorning() {
        val offer = SessionOffers.offer(state(due = 12, ahead = 0, catalog = 20, sessionCap = 24), now, Box.TZ)
        assertEquals(HeadlineKind.Reviews, offer.line().kind)
        assertEquals(HeadlineKind.StreakReminder, offer.copy(streakExposed = true).line().kind)

        // A morning is owed nothing yet.
        val morning = Box.millis(2026, 7, 1, hour = 8)
        assertEquals(HeadlineKind.Reviews, offer.copy(streakExposed = true).headline(morning, Box.TZ).kind)
    }

    /**
     * The run a reminder speaks for is THIS language's: a yesterday worked here and a today
     * still empty exposes it, and no amount of exposure in another box answers for it.
     */
    @Test
    fun theExposedRunIsReadOffThisLanguageAlone() {
        val worked = Box.state((1..20).map { Box.word(it) }, Box.config())
            .let { it.copy(dailyStats = mapOf(dayKey(Box.plusDays(now, -1.0), Box.TZ) to DayStats(reviews = 8))) }
        assertTrue(SessionOffers.offer(worked, now, Box.TZ).streakExposed)

        val alsoToday = worked.copy(
            dailyStats = worked.dailyStats + (dayKey(now, Box.TZ) to DayStats(reviews = 3)),
        )
        assertFalse(SessionOffers.offer(alsoToday, now, Box.TZ).streakExposed)
    }

    /** The pick is a fixed function of the counts — pinned so a rewrite cannot drift it. */
    @Test
    fun theHeadlinePickIsPinned() {
        fun variant(reviews: Int, ahead: Int, fresh: Int) =
            SessionOffer(SessionOfferKind.Reviews, reviews, 0, ahead, fresh, shortRound = 0).line().variant
        assertEquals(listOf(3, 0, 1), listOf(variant(0, 0, 0), variant(1, 0, 0), variant(20, 0, 5)))
    }

    private fun summary(reviews: Int, ahead: Int, fresh: Int) =
        SessionOffer(SessionOfferKind.Reviews, reviews, dueHeldBack = 0, ahead = ahead, fresh = fresh, shortRound = 0)
            .summaryParts()

    /**
     * A pulled-forward card is the same act of recalling as a due one, so it counts
     * INTO the repetitions instead of standing as a pile of its own.
     */
    @Test
    fun recallAbsorbsWhatWasPulledForward() {
        assertEquals(
            listOf(OfferPart(OfferPartKind.Reviews, 8), OfferPart(OfferPartKind.Fresh, 5)),
            summary(reviews = 6, ahead = 2, fresh = 5),
        )
        assertEquals(listOf(OfferPart(OfferPartKind.Reviews, 8)), summary(reviews = 6, ahead = 2, fresh = 0))
        assertEquals(listOf(OfferPart(OfferPartKind.Reviews, 6)), summary(reviews = 6, ahead = 0, fresh = 0))
    }

    /** Carrying the round alone is the one thing that gets pull-ahead named as itself. */
    @Test
    fun pullAheadIsNamedOnlyWhenItCarriesTheRoundAlone() {
        assertEquals(
            listOf(OfferPart(OfferPartKind.Ahead, 4), OfferPart(OfferPartKind.Fresh, 3)),
            summary(reviews = 0, ahead = 4, fresh = 3),
        )
        assertEquals(listOf(OfferPart(OfferPartKind.Ahead, 4)), summary(reviews = 0, ahead = 4, fresh = 0))
    }

    /** First sights are their own part wherever there are any, and can stand alone. */
    @Test
    fun firstSightsStayApartFromRecall() {
        assertEquals(listOf(OfferPart(OfferPartKind.Fresh, 7)), summary(reviews = 0, ahead = 0, fresh = 7))
    }

    /** A round that names no count says so in one plain phrase — kern hands back nothing to spell. */
    @Test
    fun aRoundWithNothingNameableSpellsNothing() {
        assertEquals(emptyList<OfferPart>(), summary(reviews = 0, ahead = 0, fresh = 0))
        assertEquals(
            emptyList<OfferPart>(),
            SessionOffers.offer(Box.state(emptyList()), now, Box.TZ).summaryParts(),
        )
    }

    /** The same rule over live compositions, not just hand-built counts. */
    @Test
    fun theSummaryReadsOffALiveRound() {
        // A token couple of due cards still absorbs the pull-ahead behind it.
        assertEquals(
            listOf(OfferPart(OfferPartKind.Reviews, 5)),
            SessionOffers.offer(state(due = 2, ahead = 3, catalog = 5, sessionCap = 24), now, Box.TZ).summaryParts(),
        )
        // A caught-up box has only pull-ahead to offer, so it is named.
        assertEquals(
            listOf(OfferPart(OfferPartKind.Ahead, 4)),
            SessionOffers.offer(state(due = 0, ahead = 4, catalog = 4, sessionCap = 24), now, Box.TZ).summaryParts(),
        )
        // A rested box offers first sights and nothing to recall.
        assertEquals(
            listOf(OfferPart(OfferPartKind.Fresh, SessionComposer.NEW_CARDS_PER_ROUND)),
            SessionOffers.offer(state(due = 0, ahead = 0, catalog = 30, sessionCap = 24), now, Box.TZ).summaryParts(),
        )
    }

    /** Nothing composed and nothing due: the day has nothing to offer. */
    @Test
    fun anEmptyBoxOffersNothing() {
        assertFalse(SessionOffers.sessionAvailable(Box.state(emptyList()), now, Box.TZ))
        assertTrue(SessionOffers.sessionAvailable(state(0, 0, 30, 25), now, Box.TZ))
    }

    /**
     * Due work counts even when the composed round cannot carry it — a session cap that holds
     * every due card back still leaves work waiting, and answering "nothing" would be a lie.
     */
    @Test
    fun dueWorkCountsEvenWhenTheRoundCannotCarryIt() {
        val squeezed = state(due = 5, ahead = 0, catalog = 20, sessionCap = 0)
        assertTrue(SessionComposer.composeSession(squeezed, now, Box.TZ).isEmpty)
        assertTrue(SessionOffers.sessionAvailable(squeezed, now, Box.TZ))
    }

    /**
     * A round the learner asks for reaches ahead of a due time, so a caught-up box still has
     * one in it. Only a box with nothing left at all answers no — which is what makes the
     * offer safe to show wherever a round can be opened.
     */
    @Test
    fun anAskedForRoundOutlastsACaughtUpBox() {
        val caughtUp = state(due = 0, ahead = 4, catalog = 4, sessionCap = 24)
        assertTrue(SessionOffers.canPracticeMore(caughtUp, now, Box.TZ))
        assertFalse(SessionOffers.canPracticeMore(Box.state(emptyList()), now, Box.TZ))
    }
}
