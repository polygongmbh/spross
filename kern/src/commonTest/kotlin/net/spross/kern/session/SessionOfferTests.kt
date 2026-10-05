package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxState
import net.spross.kern.box.dayKey

/** Round classification, the counts behind it, and a headline pick that survives a relaunch. */
class SessionOfferTests {
    private val now = Box.day1

    private fun state(due: Int, ahead: Int, catalog: Int, sessionCap: Int = Box.config().sessionCap): BoxState =
        Box.scenario(catalog, due = due, later = ahead, config = Box.config(sessionCap))

    /** This round's line at the scenario clock. */
    private fun SessionOffer.line() = headline(now, Box.TZ)

    /** A rested box offers first sights, and they outnumber everything there is to recall. */
    @Test
    fun newWorkLeadsWhenItOutnumbersRecall() {
        val offer = SessionOffers.offer(state(due = 0, ahead = 0, catalog = 30), now, Box.TZ)
        assertEquals(SessionOfferKind.NewSet, offer.kind)
        assertEquals(SessionComposer.NEW_CARDS_PER_ROUND, offer.newCards)
        assertEquals(0, offer.reviews)
        assertEquals(0, offer.dueHeldBack)
    }

    /** A backlog leads, and the cap's leftovers are named rather than hidden. */
    @Test
    fun recallLeadsAndTheCapsLeftoversAreNamed() {
        val offer = SessionOffers.offer(state(due = 40, ahead = 0, catalog = 50), now, Box.TZ)
        assertEquals(SessionOfferKind.Reviews, offer.kind)
        assertEquals(40, offer.reviews + offer.dueHeldBack)
        assertTrue(offer.newCards > 0)
    }

    /**
     * A handful over the cap goes unsaid: a box in good health nearly always carries a few,
     * so naming them would put an arrears notice on an ordinary day.
     * [recallLeadsAndTheCapsLeftoversAreNamed] holds the other side of the line.
     */
    @Test
    fun aRemainderTooSmallToNameIsNotNamed() {
        val offer = SessionOffers.offer(state(due = 25, ahead = 0, catalog = 50), now, Box.TZ)
        assertTrue(offer.reviews < 25)
        assertEquals(0, offer.dueHeldBack)
    }

    /** One or two due cards are a warm-up, never the round's headline. */
    @Test
    fun aTokenCoupleOfDueCardsIsAWarmUp() {
        val offer = SessionOffers.offer(state(due = 2, ahead = 3, catalog = 5), now, Box.TZ)
        assertEquals(SessionOfferKind.WarmUp, offer.kind)
        assertEquals(2, offer.reviews)
        assertEquals(3, offer.ahead)
        assertEquals(0, offer.newCards)
        assertEquals(0, offer.dueHeldBack)

        // One more due card and recall takes the lead.
        val leading = SessionOffers.offer(state(due = 3, ahead = 2, catalog = 5), now, Box.TZ)
        assertEquals(SessionOffer.REVIEWS_LEAD_FROM, leading.reviews)
        assertEquals(SessionOfferKind.Reviews, leading.kind)
    }

    /** An empty round has no words of its own; the headline still names a kind. */
    @Test
    fun anEmptyRoundBorrowsTheNewSetHeadline() {
        val offer = SessionOffers.offer(Box.state(emptyList()), now, Box.TZ)
        assertEquals(SessionOfferKind.Nothing, offer.kind)
        assertEquals(HeadlineKind.NewSet, offer.line().kind)
        assertTrue(offer.line().variant in 0 until offer.line().kind.variants)
    }

    /**
     * A long round names what a short one would hand over instead; a round the learner can
     * finish in a sitting names nothing, because there is no second way in to offer.
     */
    @Test
    fun onlyALongRoundOffersAShortOne() {
        val behind = SessionOffers.offer(state(due = 40, ahead = 0, catalog = 50), now, Box.TZ)
        assertEquals(SessionComposer.SHORT_ROUND_CARDS, behind.shortRound)

        val rested = SessionOffers.offer(state(due = 0, ahead = 0, catalog = 30), now, Box.TZ)
        assertEquals(0, rested.shortRound)
    }

    /**
     * The headline turns on the round's shape and the day's work so far, and on nothing else:
     * same counts, same variant, every run — a runtime-seeded hash would re-roll the line on
     * every launch, and the two platforms would disagree.
     */
    @Test
    fun theHeadlinePickIsStableAndSpreadAcrossVariants() {
        val offer = SessionOffer(SessionOfferKind.Reviews, reviews = 20, dueHeldBack = 20, ahead = 0, newCards = 4, shortRound = 7)
        assertEquals(offer.line(), offer.copy(dueHeldBack = 3).line())
        assertEquals(offer.line(), offer.copy(shortRound = 0).line())
        assertEquals(offer.line(), SessionOffers.offer(state(40, 0, 50), now, Box.TZ).line())

        val variants = (0..40).flatMap { reviews ->
            (0..7).map { newCards ->
                SessionOffer(SessionOfferKind.Reviews, reviews, 0, 0, newCards, shortRound = 0).line().variant
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
        val first = SessionOffer(SessionOfferKind.Reviews, reviews = 12, dueHeldBack = 0, ahead = 0, newCards = 3, shortRound = 0)
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
        val offer = SessionOffers.offer(state(due = 12, ahead = 0, catalog = 20), now, Box.TZ)
        assertEquals(HeadlineKind.Reviews, offer.line().kind)
        assertEquals(HeadlineKind.StreakReminder, offer.copy(streakExposed = true).line().kind)

        // A morning is owed nothing yet.
        val morning = Box.millis(2026, 7, 1, hour = 8)
        assertEquals(HeadlineKind.Reviews, offer.copy(streakExposed = true).headline(morning, Box.TZ).kind)
    }

    /**
     * The run a reminder speaks for is the merged one the flame beside it counts: a day
     * spent in ANOTHER language has paid today, and this card stops warning about it.
     */
    @Test
    fun theExposedRunIsTheMergedOne() {
        // Answered yesterday and not yet today: the run stands and this day still owes it.
        val worked = Box.inject(
            Box.state((1..20).map { Box.word(it) }, Box.config()),
            Box.sched("zz", dueMillis = now, lastReviewMillis = Box.plusDays(now, -1.0), logCount = 8),
        )
        assertTrue(SessionOffers.offer(worked, now, Box.TZ).streakExposed)

        val alsoToday = Box.inject(
            worked,
            Box.sched("zy", dueMillis = now, lastReviewMillis = now, logCount = 3),
        )
        assertFalse(SessionOffers.offer(alsoToday, now, Box.TZ).streakExposed)

        // Today worked in another box: the same day, and this one no longer calls it unpaid.
        val elsewhere = mapOf(dayKey(now, Box.TZ) to 6)
        assertFalse(SessionOffers.offer(worked, now, Box.TZ, elsewhere).streakExposed)

        // A day that is not today answers for nothing.
        val staleElsewhere = mapOf(dayKey(Box.plusDays(now, -1.0), Box.TZ) to 6)
        assertTrue(SessionOffers.offer(worked, now, Box.TZ, staleElsewhere).streakExposed)
    }

    private fun summary(reviews: Int, ahead: Int, newCards: Int) =
        SessionOffer(SessionOfferKind.Reviews, reviews, dueHeldBack = 0, ahead = ahead, newCards = newCards, shortRound = 0)
            .summaryParts()

    /**
     * A pulled-forward card is the same act of recalling as a due one, so it counts
     * INTO the repetitions instead of standing as a pile of its own.
     */
    @Test
    fun recallAbsorbsWhatWasPulledForward() {
        assertEquals(
            listOf(OfferPart(OfferPartKind.Reviews, 8), OfferPart(OfferPartKind.NewCards, 5)),
            summary(reviews = 6, ahead = 2, newCards = 5),
        )
        assertEquals(listOf(OfferPart(OfferPartKind.Reviews, 8)), summary(reviews = 6, ahead = 2, newCards = 0))
        assertEquals(listOf(OfferPart(OfferPartKind.Reviews, 6)), summary(reviews = 6, ahead = 0, newCards = 0))
    }

    /** Carrying the round alone is the one thing that gets pull-ahead named as itself. */
    @Test
    fun pullAheadIsNamedOnlyWhenItCarriesTheRoundAlone() {
        assertEquals(
            listOf(OfferPart(OfferPartKind.Ahead, 4), OfferPart(OfferPartKind.NewCards, 3)),
            summary(reviews = 0, ahead = 4, newCards = 3),
        )
        assertEquals(listOf(OfferPart(OfferPartKind.Ahead, 4)), summary(reviews = 0, ahead = 4, newCards = 0))
    }

    /** First sights are their own part wherever there are any, and can stand alone. */
    @Test
    fun firstSightsStayApartFromRecall() {
        assertEquals(listOf(OfferPart(OfferPartKind.NewCards, 7)), summary(reviews = 0, ahead = 0, newCards = 7))
    }

    /** A round that names no count says so in one plain phrase — kern hands back nothing to spell. */
    @Test
    fun aRoundWithNothingNameableSpellsNothing() {
        assertEquals(emptyList<OfferPart>(), summary(reviews = 0, ahead = 0, newCards = 0))
        assertEquals(
            emptyList<OfferPart>(),
            SessionOffers.offer(Box.state(emptyList()), now, Box.TZ).summaryParts(),
        )
    }

    /** Nothing composed and nothing due: the day has nothing to offer. */
    @Test
    fun anEmptyBoxOffersNothing() {
        assertFalse(SessionOffers.offer(Box.state(emptyList()), now, Box.TZ).hasRound)
        assertTrue(SessionOffers.offer(state(0, 0, 30, 25), now, Box.TZ).hasRound)
    }

    /**
     * A round the learner asks for reaches ahead of a due time, so a caught-up box still has
     * one in it. Only a box with nothing left at all answers no — which is what makes the
     * offer safe to show wherever a round can be opened.
     */
    @Test
    fun anAskedForRoundOutlastsACaughtUpBox() {
        val caughtUp = state(due = 0, ahead = 4, catalog = 4)
        assertTrue(SessionOffers.canPracticeMore(caughtUp, now, Box.TZ))
        assertFalse(SessionOffers.canPracticeMore(Box.state(emptyList()), now, Box.TZ))
    }
}
