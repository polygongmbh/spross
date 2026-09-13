package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.fsrs.FsrsScheduler
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.Rating

/**
 * A schedule replayed from its own log is the schedule that recorded it — the guarantee the
 * store rests on, since everything but `due` is derived from the log on load.
 */
class ReplayTests {
    private val scheduler = FsrsScheduler(BoxConfig().fsrsParameters())

    private fun replayOf(sched: CardScheduling): CardScheduling =
        replayed(sched.cardId, sched.due!!, sched.log, sched.suspended, scheduler)

    /** A live run and its replay agree on everything the log implies. */
    private fun assertReplays(sched: CardScheduling) {
        assertEquals(sched, replayOf(sched))
    }

    @Test
    fun aRunOfAnswersEqualsItsReplay() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, "w01", Rating.Good, Box.day1)
        // a same-day retry, then real waits, then a lapse after graduating
        state = Box.answered(state, "w01", Rating.Again, Box.plusSeconds(Box.day1, 600))
        state = Box.answered(state, "w01", Rating.Good, Box.plusDays(Box.day1, 1.0))
        state = Box.answered(state, "w01", Rating.Again, Box.plusDays(Box.day1, 6.0))
        state = Box.answered(state, "w01", Rating.Good, Box.plusDays(Box.day1, 20.0))

        val sched = state.scheduling.getValue("w01")
        assertEquals(5, sched.log.size)
        assertEquals(2, sched.lapses) // the introducing Again is no lapse
        assertReplays(sched)
    }

    /** The watch answers offline and hands its event over dated: the log is not sorted. */
    @Test
    fun anAnswerDatedBeforeTheLastOneReplaysInRecordedOrder() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, "w01", Rating.Good, Box.plusDays(Box.day1, 2.0))
        state = Box.answered(state, "w01", Rating.Good, Box.day1)

        val sched = state.scheduling.getValue("w01")
        assertTrue(sched.log.last().date < sched.log.first().date) // the log is not sorted
        assertReplays(sched)
    }

    /** A word suspended before it was ever asked, answered later: the suspension holds. */
    @Test
    fun anAnsweredHuskReplaysSuspended() {
        var state = Box.state(listOf(Box.word(1)))
        state = BoxEngine.setSuspended(state, "w01", suspended = true, nowEpochMillis = Box.day1)
        state = Box.answered(state, "w01", Rating.Good, Box.plusDays(Box.day1, 1.0))

        val sched = state.scheduling.getValue("w01")
        assertTrue(sched.suspended)
        assertReplays(sched)
    }
}
