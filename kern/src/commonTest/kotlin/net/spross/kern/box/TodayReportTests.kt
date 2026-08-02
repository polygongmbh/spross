package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.Rating

/** The day's own report: what was answered, met, consolidated — and whether it is going badly. */
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
        assertEquals(3, today.reviews)
        assertEquals(3, today.introduced)
        assertEquals(1, today.missed)
        // A single Good answer no longer consolidates on sight (only Easy does) —
        // none of today's words have proven themselves yet.
        assertEquals(0, today.consolidated)
    }

    /** Yesterday's work belongs to yesterday — the day boundary is the caller's zone. */
    @Test
    fun onlyTodaysAnswersCount() {
        var state = boxOf(2)
        state = Box.answered(state, "w01", Rating.Good, Box.plusDays(now, -1.0))
        state = Box.answered(state, "w02", Rating.Good, now)

        assertEquals(1, BoxEngine.today(state, now, Box.TZ).reviews)
        assertEquals(1, BoxEngine.today(state, Box.plusDays(now, -1.0), Box.TZ).reviews)
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
        assertEquals(12, today.reviews)
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

    /** Only the crossing counts: a word already consolidated goes on being reviewed for free. */
    @Test
    fun aWordCountsOnTheDayItCrossesAndNotAgain() {
        var state = boxOf(2)
        // A single Good graduates to Review (stability 2.3065) but doesn't consolidate yet.
        state = Box.answered(state, "w01", Rating.Good, now)
        assertEquals(0, BoxEngine.today(state, now, Box.TZ).consolidated)

        // A second success, well after the natural interval, pushes stability past the
        // consolidated bar — that is the day the crossing is booked.
        val later = Box.plusDays(now, 30.0)
        state = Box.answered(state, "w01", Rating.Good, later)
        assertTrue(BoxEngine.isConsolidated(state, "w01"))
        assertEquals(1, BoxEngine.today(state, later, Box.TZ).consolidated)

        // Already consolidated — reviewing it again does not cross a second time.
        val evenLater = Box.plusDays(later, 30.0)
        state = Box.answered(state, "w01", Rating.Good, evenLater)
        assertEquals(0, BoxEngine.today(state, evenLater, Box.TZ).consolidated)
    }

    /** Today's arrivals, minus the ones that landed on arrival — not minus every crossing. */
    @Test
    fun stillFreshCountsTodaysArrivalsThatHaveNotLanded() {
        var state = boxOf(3)
        state = Box.answered(state, "w01", Rating.Good, now)
        state = Box.answered(state, "w02", Rating.Good, now)
        // Known on sight: introduced and consolidated by the same answer, so it
        // belongs to the consolidated tally and never to the fresh one.
        state = Box.answered(state, "w03", Rating.Easy, now)

        val today = BoxEngine.today(state, now, Box.TZ)
        assertEquals(3, today.introduced)
        assertEquals(1, today.consolidated)
        assertEquals(2, today.stillFresh)
    }

    /** An older word crossing today is the consolidated tile's news, not the fresh tile's loss. */
    @Test
    fun anOlderWordConsolidatingDoesNotEatTodaysFreshCount() {
        var state = boxOf(2)
        state = Box.answered(state, "w01", Rating.Good, now)

        val later = Box.plusDays(now, 30.0)
        state = Box.answered(state, "w01", Rating.Good, later)
        state = Box.answered(state, "w02", Rating.Good, later)

        val today = BoxEngine.today(state, later, Box.TZ)
        assertEquals(1, today.consolidated) // w01 crossed, having arrived a month ago
        assertEquals(1, today.introduced)
        assertEquals(1, today.stillFresh) // w02 only — never net, never negative
    }

    /** A word on its way in crosses the moment its stability reaches the threshold. */
    @Test
    fun theCrossingIsBookedOnTheAnswerThatMakesIt() {
        var state = boxOf(2)
        state = Box.inject(
            state,
            Box.sched(
                "w01",
                stability = 1.5, // under consolidatedStability 6.0 — still on its way in
                dueMillis = now,
                lastReviewMillis = Box.plusDays(now, -10.0),
            ),
        )
        assertFalse(BoxEngine.isConsolidated(state, "w01"))
        state = Box.answered(state, "w01", Rating.Good, now)
        assertTrue(BoxEngine.isConsolidated(state, "w01"))
        assertEquals(1, BoxEngine.today(state, now, Box.TZ).consolidated)
    }
}
