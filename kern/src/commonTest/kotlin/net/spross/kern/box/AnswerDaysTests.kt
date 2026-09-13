package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.model.Rating

/** Answers per day, counted off the logs: the streak's input, and every strip built on it. */
class AnswerDaysTests {

    @Test
    fun everyAnswerCountsOnTheDayItWasGiven() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.answered(state, "w01", Rating.Good, Box.day1)
        state = Box.answered(state, "w02", Rating.Good, Box.day1)
        // a retry the same day counts again — every answer is a review
        state = Box.answered(state, "w01", Rating.Again, Box.plusSeconds(Box.day1, 600))
        state = Box.answered(state, "w01", Rating.Good, Box.plusDays(Box.day1, 2.0))

        assertEquals(
            mapOf("2026-07-01" to 3, "2026-07-03" to 1),
            answerDays(state.scheduling, Box.TZ),
        )
    }

    /** The day is cut where the learner stands now, not where they stood when they answered. */
    @Test
    fun theCallersZoneCutsTheDay() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.answered(state, "w01", Rating.Good, Box.millis(2026, 7, 1, hour = 23))

        assertEquals(mapOf("2026-07-01" to 1), answerDays(state.scheduling, "UTC"))
        // Kiritimati is UTC+14, so the same instant is already the next day there.
        assertEquals(mapOf("2026-07-02" to 1), answerDays(state.scheduling, "Pacific/Kiritimati"))
    }

    /** A suspended word, or one this join does not carry, was still answered that day. */
    @Test
    fun suspendedAndUnjoinedSchedulesCount() {
        val state = Box.inject(
            Box.state(listOf(Box.word(1))),
            Box.sched("zz", dueMillis = Box.day1, lastReviewMillis = Box.day1,
                      suspended = true, logCount = 2),
        )
        assertEquals(mapOf("2026-07-01" to 2), answerDays(state.scheduling, Box.TZ))
    }

    /** Growing is one commitment: a day earns it whichever language it was spent on. */
    @Test
    fun mergingSumsTheSameDayAcrossLanguages() {
        assertEquals(
            mapOf("2026-07-01" to 5, "2026-07-02" to 1),
            mergeAnswerDays(listOf(mapOf("2026-07-01" to 2), mapOf("2026-07-01" to 3, "2026-07-02" to 1))),
        )
    }

    /** The one-day read and the whole history agree — they are the same count. */
    @Test
    fun todaysCountMatchesTheDayWalk() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = Box.answered(state, "w01", Rating.Good, Box.day1)
        state = Box.answered(state, "w02", Rating.Good, Box.day1)
        state = Box.answered(state, "w01", Rating.Good, Box.plusDays(Box.day1, 1.0))

        assertEquals(
            answerDays(state.scheduling, Box.TZ)["2026-07-01"],
            answersOn(state.scheduling, Box.day1, Box.TZ),
        )
    }
}
