package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.box.Box
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.model.Rating

/** What the run touched, in the order it touched it. */
class AnsweredIdsTests {
    private val now = Box.day1

    private fun started(state: BoxState): SessionRunState =
        SessionRun.reduce(SessionRun.idle(state), SessionIntent.Start, now, Box.TZ).state

    private fun answered(run: SessionRunState): SessionRunState =
        SessionRun.reduce(run, SessionIntent.Answer(Rating.Good), now, Box.TZ).state

    @Test
    fun theTallyRecordsTheCardsTheRatingsLandedOn() {
        var run = started(Box.state((1..4).map { Box.word(it) }))
        val seen = mutableListOf<String>()
        repeat(3) {
            run.currentCardId?.let { seen += it }
            run = answered(run)
        }

        assertEquals(seen, run.tally.cardIds)
    }

    @Test
    fun anUnknownCardStillRecordsTheAnswer() {
        // The join dropped the card under the run: the box is untouched, but the
        // answer is booked regardless.
        var run = started(Box.state((1..3).map { Box.word(it) }))
        run = run.copy(box = BoxEngine.rejoin(run.box, emptyList(), run.box.joinStamp))

        run = answered(run)

        assertEquals(1, run.tally.cardIds.size)
    }

    @Test
    fun aFreshRunStartsWithNothingTouched() {
        assertEquals(emptyList(), started(Box.state((1..3).map { Box.word(it) })).tally.cardIds)
    }
}
