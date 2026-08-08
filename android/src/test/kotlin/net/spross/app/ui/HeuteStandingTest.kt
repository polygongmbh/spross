package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.app.Chrome
import net.spross.kern.box.TodayReport
import net.spross.kern.box.TomorrowNote
import net.spross.kern.session.SessionHeadline
import net.spross.kern.session.SessionOffer
import net.spross.kern.session.SessionOfferKind

/**
 * The WORDS Heute wraps around the box's answers, and the precedence between its four
 * cards. Which parts an offer or a day names, and in which order, is kern's
 * (`SessionOffer.summaryParts` / `TodayReport.tallyParts`) and tested there.
 */
class HeuteStandingTest {

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
    fun anOfferOutranksADoneStateAndAnEmptyBoxIsNeverCaughtUp() {
        assertEquals(
            HeuteCard.Session,
            heuteCard(failed = false, offerKind = SessionOfferKind.WarmUp, activeCards = 0),
        )
        assertEquals(
            HeuteCard.Done,
            heuteCard(failed = false, offerKind = SessionOfferKind.Nothing, activeCards = 7),
        )
        assertEquals(
            HeuteCard.EmptyBox,
            heuteCard(failed = false, offerKind = SessionOfferKind.Nothing, activeCards = 0),
        )
        assertEquals(
            HeuteCard.Failure,
            heuteCard(failed = true, offerKind = SessionOfferKind.Reviews, activeCards = 7),
        )
    }

    @Test
    fun pullAheadsCountIntoTheRepetitionsRatherThanStandingAsTheirOwnPile() {
        val offer = SessionOffer(SessionOfferKind.Reviews, reviews = 12, dueHeldBack = 0, ahead = 3, fresh = 2)

        assertEquals("15 Wiederholungen · 2 Frischlinge", offerSummary(chrome, offer))
    }

    @Test
    fun aheadIsNamedOnlyWhenItCarriesTheRoundAlone() {
        val offer = SessionOffer(SessionOfferKind.WarmUp, reviews = 0, dueHeldBack = 0, ahead = 4, fresh = 0)

        assertEquals("4 Auffrischer", offerSummary(chrome, offer))
    }

    @Test
    fun aRoundThatNamesNothingSaysSoInOnePhrase() {
        val offer = SessionOffer(SessionOfferKind.Nothing, reviews = 0, dueHeldBack = 0, ahead = 0, fresh = 0)

        assertEquals(chrome.sessionSomeCards, offerSummary(chrome, offer))
    }

    @Test
    fun everyKindAndVariantResolvesToAPhrasing() {
        for (kind in SessionOfferKind.entries) {
            for (variant in 0 until SessionOffer.HEADLINE_VARIANTS) {
                val text = headlineText(chrome, SessionHeadline(kind, variant))
                assertTrue(text.isNotBlank(), "$kind/$variant had no words")
            }
        }
    }

    @Test
    fun theHeadlineFollowsTheRoundsShapeRatherThanTheClock() {
        val offer = SessionOffer(SessionOfferKind.Reviews, reviews = 5, dueHeldBack = 0, ahead = 0, fresh = 2)

        assertEquals(
            chrome.headlineReviews[offer.headline.variant],
            headlineText(chrome, offer.headline),
        )
    }

    @Test
    fun anUnworkedDayHasAStateAndNoTally() {
        assertNull(todayTally(chrome, report(reviews = 0, introduced = 3)))
    }

    @Test
    fun theCrossingsReadLastOnAWorkedDay() {
        assertEquals(
            "24 Wiederholungen · 3 Frischlinge · 2 gefestigt",
            todayTally(chrome, report(reviews = 24, introduced = 3, consolidated = 2)),
        )
        assertEquals("8 Wiederholungen", todayTally(chrome, report(reviews = 8)))
    }

    @Test
    fun aPackOutranksTheDueCountInWhatTomorrowIsToldToHold() {
        assertEquals(chrome.tomorrowPacked, tomorrowText(chrome, TomorrowNote.Packed, due = 9))
        assertEquals(chrome.tomorrowFresh, tomorrowText(chrome, TomorrowNote.Fresh, due = 0))
        assertEquals(
            chrome.tomorrowDue.format(9),
            tomorrowText(chrome, TomorrowNote.Due, due = 9),
        )
    }
}
