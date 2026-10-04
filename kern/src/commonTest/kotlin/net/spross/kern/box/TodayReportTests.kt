package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.Rating
import net.spross.kern.store.StoreCodec
import net.spross.kern.store.StoredBox

/**
 * The day's own report: what was answered, met, settled — whether it is going badly,
 * and which parts of it a surface spells out.
 */
class TodayReportTests {
    private val now = Box.day1

    private fun boxOf(count: Int) = Box.state((1..count).map { Box.word(it) })

    private fun id(n: Int) = "w" + n.toString().padStart(2, '0')

    private fun report(reviews: Int, introduced: Int = 0, settled: Int = 0) = TodayReport(
        reviews = reviews,
        introduced = introduced,
        settled = settled,
        missed = 0,
        expectedRecall = 0.8,
    )

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
        // A single Good answer no longer settles on sight (only Easy does) —
        // none of today's words have proven themselves yet.
        assertEquals(0, today.settled)
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

    /** Only the crossing counts: a word already settled goes on being reviewed for free. */
    @Test
    fun aWordCountsOnTheDayItCrossesAndNotAgain() {
        var state = boxOf(2)
        // A single Good graduates to Review (stability 2.3065) but doesn't settle yet.
        state = Box.answered(state, "w01", Rating.Good, now)
        assertEquals(0, BoxEngine.today(state, now, Box.TZ).settled)

        // A second success, well after the natural interval, pushes stability past the
        // settled bar — that is the day the crossing is booked.
        val later = Box.plusDays(now, 30.0)
        state = Box.answered(state, "w01", Rating.Good, later)
        assertTrue(BoxEngine.hasSettled(state, "w01"))
        assertEquals(1, BoxEngine.today(state, later, Box.TZ).settled)

        // Already settled — reviewing it again does not cross a second time.
        val evenLater = Box.plusDays(later, 30.0)
        state = Box.answered(state, "w01", Rating.Good, evenLater)
        assertEquals(0, BoxEngine.today(state, evenLater, Box.TZ).settled)
    }

    /** The crossing is read off the log, so a box reloaded from its file still counts it. */
    @Test
    fun todaysCrossingComesBackFromTheStoredLog() {
        var state = boxOf(1)
        state = Box.answered(state, "w01", Rating.Good, now)
        val later = Box.plusDays(now, 30.0)
        state = Box.answered(state, "w01", Rating.Good, later)

        val reloaded = StoreCodec.decode(StoreCodec.encode(StoredBox.of(state)))
            .join(state.cards.values.toList(), state.joinStamp)
        assertEquals(1, BoxEngine.today(reloaded, later, Box.TZ).settled)
    }

    /** An older word crossing today is the settled tile's news, not today's arrival. */
    @Test
    fun anOlderWordSettlingIsNotTodaysIntroduction() {
        var state = boxOf(2)
        state = Box.answered(state, "w01", Rating.Good, now)

        val later = Box.plusDays(now, 30.0)
        state = Box.answered(state, "w01", Rating.Good, later)
        state = Box.answered(state, "w02", Rating.Good, later)

        val today = BoxEngine.today(state, later, Box.TZ)
        assertEquals(1, today.settled) // w01 crossed, having arrived a month ago
        assertEquals(1, today.introduced) // w02 only — the crossing is no arrival
    }

    /** A word on its way in crosses the moment its stability reaches the threshold. */
    @Test
    fun theCrossingIsBookedOnTheAnswerThatMakesIt() {
        var state = boxOf(2)
        state = Box.answered(state, "w01", Rating.Good, now)
        assertFalse(BoxEngine.hasSettled(state, "w01"))

        // A second success, well after the natural interval, pushes stability past the
        // settled bar — that is the day the crossing is booked.
        val later = Box.plusDays(now, 30.0)
        state = Box.answered(state, "w01", Rating.Good, later)
        assertTrue(BoxEngine.hasSettled(state, "w01"))
        assertEquals(1, BoxEngine.today(state, later, Box.TZ).settled)
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

    @Test
    fun theTallyLeadsWithReviewsAndReadsTheRarestPartLast() {
        assertEquals(
            listOf(
                TallyPart(TallyPartKind.Reviews, 24),
                TallyPart(TallyPartKind.Introduced, 3),
                TallyPart(TallyPartKind.Settled, 2),
            ),
            report(reviews = 24, introduced = 3, settled = 2).tallyParts(),
        )
        // Reviews carry a worked day on their own; a part with nothing in it is left unsaid.
        assertEquals(listOf(TallyPart(TallyPartKind.Reviews, 7)), report(reviews = 7).tallyParts())
        assertEquals(
            listOf(TallyPart(TallyPartKind.Reviews, 5), TallyPart(TallyPartKind.Settled, 2)),
            report(reviews = 5, settled = 2).tallyParts(),
        )
    }

    /** A round names what it bought, in the order it reads — and stays quiet about the rest. */
    @Test
    fun aFinishedRoundNamesOnlyItsNonZeroParts() {
        assertEquals(
            listOf(TallyPart(TallyPartKind.Introduced, 3), TallyPart(TallyPartKind.Reviews, 8)),
            completionTallyParts(introduced = 3, settled = 0, reviews = 8),
        )
        assertEquals(
            listOf(
                TallyPart(TallyPartKind.Introduced, 1),
                TallyPart(TallyPartKind.Reviews, 3),
                TallyPart(TallyPartKind.Settled, 2),
            ),
            completionTallyParts(introduced = 1, settled = 2, reviews = 3),
        )
        // Nothing nameable: the surface says so plainly instead of printing three zeros.
        assertEquals(emptyList(), completionTallyParts(introduced = 0, settled = 0, reviews = 0))
    }

    /** Packed words outrank the due count: the round they arrive in is the answer to them. */
    @Test
    fun theDayAheadIsNamedByWhatIsWaitingForIt() {
        assertEquals(TomorrowNote.Packed, tomorrowNote(hasPackedWords = true, tomorrowDue = 5))
        assertEquals(TomorrowNote.Fresh, tomorrowNote(hasPackedWords = false, tomorrowDue = 0))
        assertEquals(TomorrowNote.Due, tomorrowNote(hasPackedWords = false, tomorrowDue = 5))
    }
}
