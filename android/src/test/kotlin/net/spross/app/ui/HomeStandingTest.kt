package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.app.Chrome
import net.spross.kern.box.TodayReport
import net.spross.kern.box.TomorrowNote
import net.spross.kern.session.HeadlineKind
import net.spross.kern.session.SessionOffer
import net.spross.kern.session.SessionOfferKind
import net.spross.kern.trainer.DayLead

/**
 * The WORDS Home wraps around the box's answers, and the precedence between its
 * cards. Which parts an offer or a day names, and in which order, is kern's
 * (`SessionOffer.summaryParts` / `TodayReport.tallyParts`) and tested there.
 */
class HomeStandingTest {

    private val chrome = Chrome.forSource("de")

    private fun report(answers: Int, introduced: Int = 0, settled: Int = 0) = TodayReport(
        answers = answers,
        introduced = introduced,
        settled = settled,
        missed = 0,
        expectedRecall = 0.9,
    )

    @Test
    fun aFailureOutranksWhateverLeadsTheDay() {
        assertEquals(HomeCard.Drill, homeCard(failed = false, lead = DayLead.Drill))
        assertEquals(HomeCard.Failure, homeCard(failed = true, lead = DayLead.Drill))
    }

    @Test
    fun aRoundThatNamesNothingSaysSoInOnePhrase() {
        val offer = SessionOffer(SessionOfferKind.Nothing, reviews = 0, dueHeldBack = 0, ahead = 0, newCards = 0, shortRound = 0)

        assertEquals(chrome.homeTallySomeCards, offerSummary(chrome, offer))
    }

    /**
     * Kern says how many phrasings a kind owes, and the table owes exactly that many — the
     * count is asserted against the series itself, because [headlineText] wraps and would
     * hide a table that is short. iOS composes the same key without a wrap, so a short table
     * prints the raw key there; this is the gate that catches it for both.
     */
    @Test
    fun everyKindAndVariantResolvesToAPhrasing() {
        for (kind in HeadlineKind.entries) {
            val series = when (kind) {
                HeadlineKind.Reviews -> chrome.headlineReviews
                HeadlineKind.WarmUp -> chrome.headlineWarmUp
                HeadlineKind.NewSet -> chrome.headlineNewSet
                HeadlineKind.StreakReminder -> chrome.headlineStreak
            }
            assertEquals(kind.variants, series.size, "$kind owes as many phrasings as kern says")
            series.forEachIndexed { variant, text ->
                assertTrue(text.isNotBlank(), "$kind/$variant had no words")
            }
        }
    }

    /** A run that owes nothing headlines by the round's shape, at any hour of the day. */
    @Test
    fun aSafeRunHeadlinesByTheRoundsShapeAtAnyHour() {
        val offer = SessionOffer(SessionOfferKind.Reviews, reviews = 5, dueHeldBack = 0, ahead = 0, newCards = 2, shortRound = 0)

        for (hour in 0..23) {
            val headline = offer.headline(hour * 3_600_000L, "UTC")
            assertEquals(chrome.headlineReviews[headline.variant], headlineText(chrome, headline))
        }
    }

    /** Once the day owes the run, the card says that instead of naming the round. */
    @Test
    fun anExposedRunHeadlinesTheStreakInstead() {
        val offer = SessionOffer(
            SessionOfferKind.Reviews, reviews = 5, dueHeldBack = 0, ahead = 0, newCards = 2,
            shortRound = 0, streakExposed = true,
        )
        val headline = offer.headline(14 * 3_600_000L, "UTC")

        assertEquals(HeadlineKind.StreakReminder, headline.kind)
        assertEquals(chrome.headlineStreak[headline.variant], headlineText(chrome, headline))
    }

    @Test
    fun anUnworkedDayHasAStateAndNoTally() {
        assertNull(todayTally(chrome, report(answers = 0)))
    }

    @Test
    fun queuedWordsOutrankTheDueCountInWhatTomorrowIsToldToHold() {
        assertEquals(chrome.homeDoneQueued, tomorrowText(chrome, TomorrowNote.Queued, due = 9))
        assertEquals(chrome.homeDoneTomorrowFresh, tomorrowText(chrome, TomorrowNote.Empty, due = 0))
        assertEquals(
            chrome.homeDoneTomorrowDue.format(9),
            tomorrowText(chrome, TomorrowNote.Due, due = 9),
        )
    }
}
