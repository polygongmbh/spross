package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import net.spross.kern.session.SessionOffer
import net.spross.kern.session.SessionOfferKind

/** What Home offers first, on the typical days. */
class DayLeadTests {

    private val pick = DrillSuggestion.Pick(Drill.Numbers, DrillSuggestion.Reason.Variety, -1)

    private fun offer(kind: SessionOfferKind, due: Int, doneToday: Int) =
        SessionOffer(kind, minOf(due, 20), 0, 0, 0, 0, doneToday, dueNow = due)

    private val worked = offer(SessionOfferKind.Reviews, 8, 40)

    private fun at(time: String) = Instant.parse("2026-10-25T$time:00Z").toEpochMilliseconds()

    private fun lead(
        offer: SessionOffer,
        pick: DrillSuggestion.Pick?,
        listening: Boolean = false,
        conversation: Boolean = false,
        time: String = "08:00",
    ) = DayLead.of(offer, pick, listening, conversation, at(time), "UTC")

    @Test
    fun aFreshDayLeadsWithItsRound() {
        assertEquals(DayLead.Round, lead(offer(SessionOfferKind.Reviews, 40, 0), pick, listening = true, conversation = true))
    }

    @Test
    fun aDayPastMostOfItsReviewsLeadsWithTheDrill() {
        assertEquals(DayLead.Drill, lead(worked, pick))
        assertEquals(DayLead.Drill, lead(offer(SessionOfferKind.Nothing, 0, 30), pick))
    }

    @Test
    fun aDoneDayWithNothingElseOfferedStaysDone() {
        assertEquals(DayLead.Done, lead(offer(SessionOfferKind.Nothing, 0, 30), null))
    }

    /**
     * RULE: past the round, the drill, listening and the conversation take turns, one per
     * stretch of the day, among those Home offers.
     * WHY: a card that always named a drill would hide the two ways in that need no hands
     * and no screen; one that offered what Home has no card for would lead to nothing.
     */
    @Test
    fun theWaysInTakeTurnsAmongWhatIsOffered() {
        val day = listOf("08:00", "13:00", "19:00")
        assertEquals(
            setOf(DayLead.Drill, DayLead.Listening, DayLead.Conversation),
            day.map { lead(worked, pick, listening = true, conversation = true, time = it) }.toSet(),
        )
        assertEquals(
            setOf(DayLead.Drill, DayLead.Conversation),
            day.map { lead(worked, pick, conversation = true, time = it) }.toSet(),
        )
    }
}
