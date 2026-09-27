package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.session.SessionOffer
import net.spross.kern.session.SessionOfferKind

/** What Home offers first, on the three typical days. */
class DayLeadTests {

    private val pick = DrillSuggestion.Pick(Drill.Numbers, DrillSuggestion.Reason.Variety, -1)

    private fun offer(kind: SessionOfferKind, due: Int, doneToday: Int) =
        SessionOffer(kind, minOf(due, 20), 0, 0, 0, 0, doneToday, dueNow = due)

    @Test
    fun aFreshDayLeadsWithItsRound() {
        assertEquals(DayLead.Round, DayLead.of(offer(SessionOfferKind.Reviews, 40, 0), pick))
    }

    @Test
    fun aDayPastMostOfItsReviewsLeadsWithTheDrill() {
        assertEquals(DayLead.Drill, DayLead.of(offer(SessionOfferKind.Reviews, 8, 40), pick))
        assertEquals(DayLead.Drill, DayLead.of(offer(SessionOfferKind.Nothing, 0, 30), pick))
    }

    @Test
    fun aDoneDayWithNoDrillToNameStaysDone() {
        assertEquals(DayLead.Done, DayLead.of(offer(SessionOfferKind.Nothing, 0, 30), null))
    }
}
