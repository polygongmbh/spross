package net.spross.kern.reminder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** When a reminder goes out: at the learner's times, or at the times they habitually practice, and never once answered. */
class RemindersTests {

    private val tz = "UTC"

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime(2026, 7, day, hour, minute).toInstant(TimeZone.UTC)

    private fun fires(mode: ReminderMode, setMinutes: List<Int>, answers: List<Instant>, now: Instant, enabled: Instant = at(1, 0)) =
        Reminders.fireTimes(mode, setMinutes, enabled.toEpochMilliseconds(), answers, now.toEpochMilliseconds(), tz)

    private fun clock(instant: Instant): String = instant.toLocalDateTime(TimeZone.UTC).time.toString()

    @Test
    fun setTimesFireEachDay() {
        val (times, _) = fires(ReminderMode.Set, listOf(8 * 60, 20 * 60), emptyList(), now = at(1, 6))
        assertEquals(listOf(at(1, 8), at(1, 20), at(2, 8), at(2, 20)), times.take(4))
    }

    @Test
    fun aOnceADayLearnerIsRemindedOnceShortlyBeforeTheirUsualTime() {
        val answers = (1..10).flatMap { listOf(at(it, 19), at(it, 19, 5)) }
        val (times, _) = fires(ReminderMode.Usual, listOf(8 * 60), answers, now = at(11, 6), enabled = at(10, 0))
        assertEquals(listOf("18:45"), times.filter { it < at(12, 0) }.map(::clock))
    }

    @Test
    fun aMorningAndEveningLearnerIsRemindedTwice() {
        val answers = (1..10).flatMap { listOf(at(it, 7, 30), at(it, 21)) }
        val (times, _) = fires(ReminderMode.Usual, listOf(12 * 60), answers, now = at(11, 6), enabled = at(10, 0))
        assertEquals(listOf("07:15", "20:45"), times.filter { it < at(12, 0) }.map(::clock))
    }

    @Test
    fun aSlotAlreadyPracticedIsDropped() {
        val (times, _) = fires(ReminderMode.Set, listOf(8 * 60, 20 * 60), listOf(at(5, 7)), now = at(5, 7, 30), enabled = at(5, 0))
        assertEquals(at(5, 20), times.first())
    }

    @Test
    fun ignoredRemindersThinOutAndStop() {
        val answered = listOf(at(1, 8))
        val (thinning, _) = fires(ReminderMode.Set, listOf(20 * 60), answered, now = at(5, 6))
        assertEquals(listOf(6, 8), thinning.map { it.toLocalDateTime(TimeZone.UTC).date.day })
        val (stopped, paused) = fires(ReminderMode.Set, listOf(20 * 60), answered, now = at(10, 6))
        assertTrue(stopped.isEmpty() && paused)
    }
}
