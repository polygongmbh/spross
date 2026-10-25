package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.Rating

/**
 * The day's own report: what was answered and met — whether it is going badly,
 * and which parts of it a surface spells out.
 */
class TodayReportTests {
    private val now = Box.day1

    private fun boxOf(count: Int) = Box.state((1..count).map { Box.word(it) })

    private fun id(n: Int) = "w" + n.toString().padStart(2, '0')

    @Test
    fun countsAnswersMisesAndFirstMeetings() {
        var state = boxOf(3)
        state = Box.answered(state, "w01", Rating.Good, now)
        state = Box.answered(state, "w02", Rating.Again, now)
        state = Box.answered(state, "w03", Rating.Good, now)

        val today = BoxEngine.today(state, now, Box.TZ)
        assertEquals(3, today.answers)
        assertEquals(3, today.introduced)
        assertEquals(1, today.missed)
    }

    /** Yesterday's work belongs to yesterday — the day boundary is the caller's zone. */
    @Test
    fun onlyTodaysAnswersCount() {
        var state = boxOf(2)
        state = Box.answered(state, "w01", Rating.Good, Box.plusDays(now, -1.0))
        state = Box.answered(state, "w02", Rating.Good, now)

        assertEquals(1, BoxEngine.today(state, now, Box.TZ).answers)
        assertEquals(1, BoxEngine.today(state, Box.plusDays(now, -1.0), Box.TZ).answers)
    }

    @Test
    fun recallNeedsEnoughAnswersToMeanAnything() {
        var state = boxOf(30)
        // Three answers, two of them missed — a terrible ratio that says nothing yet.
        state = Box.answered(state, "w01", Rating.Again, now)
        state = Box.answered(state, "w02", Rating.Again, now)
        state = Box.answered(state, "w03", Rating.Good, now)
        val early = BoxEngine.today(state, now, Box.TZ)
        assertNull(early.recall)
        assertFalse(early.recallStrained)
    }

    @Test
    fun recallStrainedOnceTheDayIsClearlyGoingBadly() {
        var state = boxOf(30)
        for (n in 1..12) {
            state = Box.answered(state, id(n), if (n <= 8) Rating.Again else Rating.Good, now)
        }
        val today = BoxEngine.today(state, now, Box.TZ)
        assertEquals(12, today.answers)
        assertEquals(8, today.missed)
        assertEquals(1.0 - 8.0 / 12.0, today.recall)
        assertTrue(today.recallStrained) // 0.33 against a scheduled 0.8

        // A day merely under target is not a day going badly.
        var fine = boxOf(30)
        for (n in 1..12) {
            fine = Box.answered(fine, id(n), if (n <= 2) Rating.Again else Rating.Good, now)
        }
        assertFalse(BoxEngine.today(fine, now, Box.TZ).recallStrained)
    }

    /** A word settling today is one of the day's reviews, never its arrival nor a part of its own. */
    @Test
    fun aWordSettlingTodayCountsAsAReview() {
        var state = boxOf(2)
        state = Box.answered(state, "w01", Rating.Good, now)

        // A second success, well after the natural interval, carries w01 across the settled bar.
        val later = Box.plusDays(now, 30.0)
        state = Box.answered(state, "w01", Rating.Good, later)
        state = Box.answered(state, "w02", Rating.Good, later)
        assertTrue(BoxEngine.hasSettled(state, "w01"))

        assertEquals(
            listOf(TallyPart(TallyPartKind.Introduced, 1), TallyPart(TallyPartKind.Reviewed, 1)),
            BoxEngine.today(state, later, Box.TZ).tallyParts(),
        )
    }

    /** A day nothing was answered on is clear, never finished — and it has no tally to show. */
    @Test
    fun theDayIsWorkedOnlyOnceSomethingHasBeenAnswered() {
        var state = boxOf(2)
        assertFalse(BoxEngine.today(state, now, Box.TZ).worked)
        assertEquals(emptyList(), BoxEngine.today(state, now, Box.TZ).tallyParts())

        state = Box.answered(state, "w01", Rating.Good, now)
        assertTrue(BoxEngine.today(state, now, Box.TZ).worked)
    }

    /** A tally names what was answered and stays quiet about the rest. */
    @Test
    fun aTallyNamesOnlyItsNonZeroParts() {
        assertEquals(
            listOf(TallyPart(TallyPartKind.Introduced, 3), TallyPart(TallyPartKind.Reviewed, 8)),
            tallyParts(introduced = 3, reviewed = 8, settled = 0),
        )
        // Nothing nameable: the surface says so plainly instead of printing three zeros.
        assertEquals(emptyList(), tallyParts(introduced = 0, reviewed = 0, settled = 0))
    }

    /** Queued words outrank the due count: the round they arrive in is the answer to them. */
    @Test
    fun theDayAheadIsNamedByWhatIsWaitingForIt() {
        assertEquals(TomorrowNote.Queued, tomorrowNote(hasQueuedWords = true, tomorrowDue = 5))
        assertEquals(TomorrowNote.Empty, tomorrowNote(hasQueuedWords = false, tomorrowDue = 0))
        assertEquals(TomorrowNote.Due, tomorrowNote(hasQueuedWords = false, tomorrowDue = 5))
    }
}
