package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.model.DayStats

/**
 * The trailing activity window: the streak rule told day by day, and the same
 * answer as [Statistics.streak] told as a number.
 */
class StreakWindowTests {

    /** July days with 5 reviews each; everything else is an empty day. */
    private fun julyStats(vararg days: Int): Map<String, DayStats> =
        days.associate { "2026-07-" + it.toString().padStart(2, '0') to DayStats(reviews = 5, activeCount = 1) }

    private fun roles(window: List<ActivityDay>): List<StreakRole> = window.map { it.role }

    @Test
    fun windowRunsOldestFirstAndEndsToday() {
        val window = streakWindow(julyStats(4), days = 3, nowEpochMillis = Box.millis(2026, 7, 4), tzId = Box.TZ)
        assertEquals(listOf("2026-07-02", "2026-07-03", "2026-07-04"), window.map { it.day })
        assertEquals(listOf(0, 0, 5), window.map { it.reviews })
    }

    @Test
    fun earnedDaysInTheWindowAreExactlyWhatStreakCounted() {
        val stats = julyStats(1, 2, 4)
        val now = Box.millis(2026, 7, 4)
        val window = streakWindow(stats, days = 5, nowEpochMillis = now, tzId = Box.TZ)
        assertEquals(Statistics.streak(stats, now, Box.TZ), window.count { it.role == StreakRole.Earned })
        assertEquals(3, window.count { it.role == StreakRole.Earned })
    }

    @Test
    fun aMissedDayBetweenEarnedDaysIsBridged() {
        val window = streakWindow(julyStats(1, 2, 4), days = 4, nowEpochMillis = Box.millis(2026, 7, 4), tzId = Box.TZ)
        assertEquals(
            listOf(StreakRole.Earned, StreakRole.Earned, StreakRole.Bridged, StreakRole.Earned),
            roles(window),
        )
    }

    @Test
    fun aGapOlderThanTheRunIsNeverBridged() {
        // June 30 is empty and nothing earned sits behind it — forgiveness spans a run,
        // it does not start one.
        val window = streakWindow(julyStats(1, 2), days = 3, nowEpochMillis = Box.millis(2026, 7, 2), tzId = Box.TZ)
        assertEquals(listOf(StreakRole.Outside, StreakRole.Earned, StreakRole.Earned), roles(window))
    }

    @Test
    fun anEmptyBoxLightsUpNothing() {
        val window = streakWindow(emptyMap(), days = 4, nowEpochMillis = Box.day1, tzId = Box.TZ)
        assertEquals(List(4) { StreakRole.Outside }, roles(window))
    }

    @Test
    fun anUnfinishedTodayIsOutsideWithoutEndingTheRun() {
        val stats = julyStats(1, 2, 3)
        val now = Box.millis(2026, 7, 4)
        val window = streakWindow(stats, days = 4, nowEpochMillis = now, tzId = Box.TZ)
        assertEquals(
            listOf(StreakRole.Earned, StreakRole.Earned, StreakRole.Earned, StreakRole.Outside),
            roles(window),
        )
        assertEquals(3, Statistics.streak(stats, now, Box.TZ))
    }

    @Test
    fun activeDaysBeforeTwoMissesFallOutOfTheCurrentRun() {
        // 1,2,3 · gap · gap · 6,7 — the early run still has bars, but not the flame.
        val stats = julyStats(1, 2, 3, 6, 7)
        val now = Box.millis(2026, 7, 7)
        val window = streakWindow(stats, days = 7, nowEpochMillis = now, tzId = Box.TZ)
        assertEquals(
            listOf(
                StreakRole.Outside, StreakRole.Outside, StreakRole.Outside,
                StreakRole.Outside, StreakRole.Outside,
                StreakRole.Earned, StreakRole.Earned,
            ),
            roles(window),
        )
        assertEquals(listOf(5, 5, 5, 0, 0, 5, 5), window.map { it.reviews })
        assertEquals(2, Statistics.streak(stats, now, Box.TZ))
    }

    @Test
    fun windowFollowsTheCallersZoneDownToEachDaysMidnight() {
        val lateUtc = Box.millis(2026, 7, 1, 23, 30) // already July 2 in Kiritimati (UTC+14)
        val window = streakWindow(julyStats(2), days = 2, nowEpochMillis = lateUtc, tzId = "Pacific/Kiritimati")
        assertEquals(listOf("2026-07-01", "2026-07-02"), window.map { it.day })
        assertEquals(listOf(0, 5), window.map { it.reviews })
        // Local midnight of July 2 in Kiritimati is July 1, 10:00 UTC.
        assertEquals(Box.millis(2026, 7, 1, 10, 0), window.last().dayStartEpochMillis)
    }

    @Test
    fun statisticsStreakAndTheWindowNeverDisagree() {
        val state = Box.state(listOf(Box.word(1)))
        val fixtures = listOf(
            julyStats(1, 2, 4, 5, 7, 8, 9),
            julyStats(2, 3, 9),
            julyStats(9),
            julyStats(),
        )
        val now = Box.millis(2026, 7, 9)
        for (stats in fixtures) {
            val window = streakWindow(stats, days = 14, nowEpochMillis = now, tzId = Box.TZ)
            val streak = BoxEngine.statistics(state.copy(dailyStats = stats), now, Box.TZ).streak
            assertEquals(streak, window.count { it.role == StreakRole.Earned })
        }
    }
}
