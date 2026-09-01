package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.app.Chrome
import net.spross.kern.box.TodayReport
import net.spross.kern.box.TomorrowNote
import net.spross.kern.session.HeadlineKind
import net.spross.kern.session.SessionHeadline
import net.spross.kern.session.SessionOffer
import net.spross.kern.session.SessionOfferKind

/**
 * The WORDS Home wraps around the box's answers, and the precedence between its three
 * cards. Which parts an offer or a day names, and in which order, is kern's
 * (`SessionOffer.summaryParts` / `TodayReport.tallyParts`) and tested there.
 */
class HomeStandingTest {

    private val chrome = Chrome.forSource("de")

    private fun report(reviews: Int, introduced: Int = 0, consolidated: Int = 0) = TodayReport(
        reviews = reviews,
        introduced = introduced,
        consolidated = consolidated,
        stillFresh = 0,
        missed = 0,
        expectedRecall = 0.9,
    )

    @Test
    fun anOfferOutranksADoneState() {
        assertEquals(
            HomeCard.Session,
            homeCard(failed = false, offerKind = SessionOfferKind.WarmUp),
        )
        assertEquals(
            HomeCard.Done,
            homeCard(failed = false, offerKind = SessionOfferKind.Nothing),
        )
        assertEquals(
            HomeCard.Failure,
            homeCard(failed = true, offerKind = SessionOfferKind.Reviews),
        )
    }

    @Test
    fun pullAheadsCountIntoTheRepetitionsRatherThanStandingAsTheirOwnPile() {
        val offer = SessionOffer(SessionOfferKind.Reviews, reviews = 12, dueHeldBack = 0, ahead = 3, fresh = 2, shortRound = 0)

        assertEquals("15 Checks · 2 Neue", offerSummary(chrome, offer))
    }

    @Test
    fun aCountOfOneDeclinesItsNoun() {
        val offer = SessionOffer(SessionOfferKind.Reviews, reviews = 1, dueHeldBack = 0, ahead = 0, fresh = 1, shortRound = 0)

        assertEquals("1 Check · 1 Neues", offerSummary(chrome, offer))
    }

    @Test
    fun freshWordsSpellOutTheirNounWhenTheyCarryTheRoundAlone() {
        val offer = SessionOffer(SessionOfferKind.FreshSet, reviews = 0, dueHeldBack = 0, ahead = 0, fresh = 5, shortRound = 0)

        assertEquals("5 neue Wörter", offerSummary(chrome, offer))
    }

    @Test
    fun aheadIsNamedOnlyWhenItCarriesTheRoundAlone() {
        val offer = SessionOffer(SessionOfferKind.WarmUp, reviews = 0, dueHeldBack = 0, ahead = 4, fresh = 0, shortRound = 0)

        assertEquals("4 Auffrischer", offerSummary(chrome, offer))
    }

    @Test
    fun aRoundThatNamesNothingSaysSoInOnePhrase() {
        val offer = SessionOffer(SessionOfferKind.Nothing, reviews = 0, dueHeldBack = 0, ahead = 0, fresh = 0, shortRound = 0)

        assertEquals(chrome.homeTallySomeCards, offerSummary(chrome, offer))
    }

    @Test
    fun everyKindAndVariantResolvesToAPhrasing() {
        for (kind in HeadlineKind.entries) {
            for (variant in 0 until SessionOffer.HEADLINE_VARIANTS) {
                val text = headlineText(chrome, SessionHeadline(kind, variant))
                assertTrue(text.isNotBlank(), "$kind/$variant had no words")
            }
        }
    }

    /** A run that owes nothing headlines by the round's shape, at any hour of the day. */
    @Test
    fun aSafeRunHeadlinesByTheRoundsShapeAtAnyHour() {
        val offer = SessionOffer(SessionOfferKind.Reviews, reviews = 5, dueHeldBack = 0, ahead = 0, fresh = 2, shortRound = 0)

        for (hour in 0..23) {
            val headline = offer.headline(hour * 3_600_000L, "UTC")
            assertEquals(chrome.headlineReviews[headline.variant], headlineText(chrome, headline))
        }
    }

    /** Once the day owes the run, the card says that instead of naming the round. */
    @Test
    fun anExposedRunHeadlinesTheStreakInstead() {
        val offer = SessionOffer(
            SessionOfferKind.Reviews, reviews = 5, dueHeldBack = 0, ahead = 0, fresh = 2,
            shortRound = 0, streakExposed = true,
        )
        val headline = offer.headline(14 * 3_600_000L, "UTC")

        assertEquals(HeadlineKind.StreakReminder, headline.kind)
        assertEquals(chrome.headlineStreak[headline.variant], headlineText(chrome, headline))
    }

    @Test
    fun anUnworkedDayHasAStateAndNoTally() {
        assertNull(todayTally(chrome, report(reviews = 0, introduced = 3)))
    }

    @Test
    fun theCrossingsReadLastOnAWorkedDay() {
        assertEquals(
            "24 Checks · 3 Neue · 2 gefestigt",
            todayTally(chrome, report(reviews = 24, introduced = 3, consolidated = 2)),
        )
        assertEquals("8 Checks", todayTally(chrome, report(reviews = 8)))
    }

    @Test
    fun aPackOutranksTheDueCountInWhatTomorrowIsToldToHold() {
        assertEquals(chrome.homeDonePacked, tomorrowText(chrome, TomorrowNote.Packed, due = 9))
        assertEquals(chrome.homeDoneTomorrowFresh, tomorrowText(chrome, TomorrowNote.Fresh, due = 0))
        assertEquals(
            chrome.homeDoneTomorrowDue.format(9),
            tomorrowText(chrome, TomorrowNote.Due, due = 9),
        )
    }
}
