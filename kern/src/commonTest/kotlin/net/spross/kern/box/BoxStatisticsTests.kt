package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.spross.kern.model.CardPhase
import net.spross.kern.model.DayStats
import net.spross.kern.model.Rating

/** Statistics: streak bridging, session end fold + prune, headline counts. */
class BoxStatisticsTests {
    private val now = Box.day1

    private fun statsState(reviewDays: List<Int>): BoxState {
        val state = Box.state(listOf(Box.word(1)))
        return state.copy(
            dailyStats = reviewDays.associate {
                "2026-07-" + it.toString().padStart(2, '0') to DayStats(reviews = 5, introduced = 0, activeCount = 1)
            },
        )
    }

    @Test
    fun streakSingleGapForgiven() {
        val stats = BoxEngine.statistics(statsState(listOf(1, 2, 4)), Box.millis(2026, 7, 4), Box.TZ)
        assertEquals(3, stats.streak)
    }

    @Test
    fun streakTwoDayGapBreaks() {
        val stats = BoxEngine.statistics(statsState(listOf(1, 2, 5)), Box.millis(2026, 7, 5), Box.TZ)
        assertEquals(1, stats.streak)
    }

    @Test
    fun streakBridgesEveryIsolatedMiss() {
        // A second miss never takes back the days built before the first.
        val days = listOf(1, 2, 3, 4, 5, 7, 8, 9, 11, 12)
        val stats = BoxEngine.statistics(statsState(days), Box.millis(2026, 7, 12), Box.TZ)
        assertEquals(10, stats.streak)
    }

    @Test
    fun streakTodayInProgressIsNoMissAtAll() {
        val stats = BoxEngine.statistics(statsState(listOf(2, 3, 4)), Box.millis(2026, 7, 5), Box.TZ)
        assertEquals(3, stats.streak)
        // An empty today does not pair with an empty yesterday — the day isn't over.
        assertEquals(3, BoxEngine.statistics(statsState(listOf(1, 2, 3)), Box.millis(2026, 7, 5), Box.TZ).streak)
        assertEquals(0, BoxEngine.statistics(statsState(emptyList()), now, Box.TZ).streak)
    }

    @Test
    fun longestStreakSurvivesTheRunThatBrokeIt() {
        // 1,2,3 · gap · gap · 6,7 — the early run is the record, today's is 2 long.
        val stats = BoxEngine.statistics(statsState(listOf(1, 2, 3, 6, 7)), Box.millis(2026, 7, 7), Box.TZ)
        assertEquals(2, stats.streak)
        assertEquals(3, stats.longestStreak)
    }

    @Test
    fun longestStreakBridgesEveryIsolatedMiss() {
        val days = listOf(1, 2, 3, 4, 5, 7, 8, 9, 11, 12)
        val stats = BoxEngine.statistics(statsState(days), Box.millis(2026, 7, 12), Box.TZ)
        assertEquals(10, stats.longestStreak) // the run today IS the record
    }

    @Test
    fun longestStreakBridgesAForgivenDayLikeTheWalkBack() {
        val stats = BoxEngine.statistics(statsState(listOf(1, 2, 4)), Box.millis(2026, 7, 4), Box.TZ)
        assertEquals(3, stats.streak)
        assertEquals(3, stats.longestStreak) // the run today IS the record
    }

    @Test
    fun longestStreakIsNeverEndedByAnUnfinishedToday() {
        val stats = BoxEngine.statistics(statsState(listOf(1, 2, 3)), Box.millis(2026, 7, 4), Box.TZ)
        assertEquals(3, stats.streak)
        assertEquals(3, stats.longestStreak)
        assertEquals(0, BoxEngine.statistics(statsState(emptyList()), now, Box.TZ).longestStreak)
    }

    @Test
    fun endSessionFoldsDayStatsAndPrunesNewIntroduced() {
        var state = Box.state((1..3).map { Box.word(it) })
        state = Box.answered(state, "w01", Rating.Easy, now)
        state = state.copy(
            newIntroduced = state.newIntroduced +
                mapOf("2026-01-01" to 4, "2026-06-30" to 2), // stale vs yesterday
        )

        state = BoxEngine.endSession(state, reviewsDone = 7, nowEpochMillis = now, tzId = Box.TZ)
        // consolidated = 1: the word was answered on sight, so it landed the day it arrived.
        assertEquals(
            DayStats(reviews = 7, introduced = 1, consolidated = 1, activeCount = 1),
            state.dailyStats["2026-07-01"],
        )
        assertNull(state.newIntroduced["2026-01-01"]) // > 60 days back, pruned
        assertEquals(2, state.newIntroduced["2026-06-30"])
        assertEquals(1, state.newIntroduced["2026-07-01"])

        // A second session on the same day accumulates reviews only.
        state = BoxEngine.endSession(state, reviewsDone = 3, nowEpochMillis = now, tzId = Box.TZ)
        assertEquals(10, state.dailyStats["2026-07-01"]?.reviews)
    }

    @Test
    fun streakHealthIsEarnedOnceTodayHasReviews() {
        val stats = BoxEngine.statistics(statsState(listOf(1, 2, 3)), Box.millis(2026, 7, 3), Box.TZ)
        assertEquals(3, stats.streak)
        assertEquals(StreakHealth.Earned, stats.streakHealth)
    }

    @Test
    fun streakHealthIsBridgeableWhileYesterdayWasEarned() {
        val stats = BoxEngine.statistics(statsState(listOf(1, 2, 3)), Box.millis(2026, 7, 4), Box.TZ)
        assertEquals(3, stats.streak)
        assertEquals(StreakHealth.Bridgeable, stats.streakHealth)
    }

    @Test
    fun streakHealthIsEndingOnceYesterdayIsTheBridge() {
        // 1, 2 earned · 3 bridged · today (4) empty — a second empty day would end the run.
        val stats = BoxEngine.statistics(statsState(listOf(1, 2)), Box.millis(2026, 7, 4), Box.TZ)
        assertEquals(2, stats.streak)
        assertEquals(StreakHealth.Ending, stats.streakHealth)
    }

    @Test
    fun streakHealthIsNoneWithoutARunToProtect() {
        assertEquals(StreakHealth.None, BoxEngine.statistics(statsState(emptyList()), now, Box.TZ).streakHealth)
        // The run already ended two days back; there is nothing today could still save.
        val broken = BoxEngine.statistics(statsState(listOf(1, 2)), Box.millis(2026, 7, 5), Box.TZ)
        assertEquals(0, broken.streak)
        assertEquals(StreakHealth.None, broken.streakHealth)
    }

    @Test
    fun streakHealthReadsTheCrossLanguageMerge() {
        // Today's reviews happened in a sibling target-language box, so this box alone
        // still owes the day while the combined commitment is already earned.
        val thisLanguage = statsState(listOf(1, 2))
        val sibling = mapOf("2026-07-03" to DayStats(reviews = 5))
        val today = Box.millis(2026, 7, 3)

        assertEquals(StreakHealth.Bridgeable, BoxEngine.statistics(thisLanguage, today, Box.TZ).streakHealth)

        val combined = BoxEngine.statistics(thisLanguage, today, Box.TZ, otherLanguagesDailyStats = listOf(sibling))
        assertEquals(3, combined.streak)
        assertEquals(StreakHealth.Earned, combined.streakHealth)
    }

    @Test
    fun streakCombinesReviewsFromOtherTargetLanguages() {
        // This language's own box only has day 4; the streak-earning activity on
        // 1, 2 and 3 happened in sibling target-language boxes.
        val thisLanguage = statsState(listOf(4))
        val sibling1 = mapOf("2026-07-01" to DayStats(reviews = 5))
        val sibling2 = mapOf("2026-07-02" to DayStats(reviews = 5), "2026-07-03" to DayStats(reviews = 5))

        val alone = BoxEngine.statistics(thisLanguage, Box.millis(2026, 7, 4), Box.TZ)
        assertEquals(1, alone.streak)

        val combined = BoxEngine.statistics(
            thisLanguage,
            Box.millis(2026, 7, 4),
            Box.TZ,
            otherLanguagesDailyStats = listOf(sibling1, sibling2),
        )
        assertEquals(4, combined.streak)
        assertEquals(4, combined.longestStreak)
    }

    @Test
    fun streakMergeSumsSameDayReviewsAcrossLanguages() {
        // Day 1 alone in either language would not be enough on its own; combined
        // reviews on the same calendar day still count as one earned day.
        val thisLanguage = statsState(emptyList()).copy(
            dailyStats = mapOf("2026-07-01" to DayStats(reviews = 2)),
        )
        val sibling = mapOf("2026-07-01" to DayStats(reviews = 3))

        val stats = BoxEngine.statistics(
            thisLanguage,
            Box.millis(2026, 7, 1),
            Box.TZ,
            otherLanguagesDailyStats = listOf(sibling),
        )
        assertEquals(1, stats.streak)
    }

    @Test
    fun mergeDailyStatsSumsEveryBucketPerDay() {
        val a = mapOf("2026-07-01" to DayStats(reviews = 2, introduced = 1, consolidated = 1, activeCount = 3))
        val b = mapOf(
            "2026-07-01" to DayStats(reviews = 5, introduced = 0, consolidated = 2, activeCount = 4),
            "2026-07-02" to DayStats(reviews = 1),
        )
        val merged = mergeDailyStats(listOf(a, b))
        assertEquals(DayStats(reviews = 7, introduced = 1, consolidated = 3, activeCount = 7), merged["2026-07-01"])
        assertEquals(DayStats(reviews = 1), merged["2026-07-02"])
    }

    @Test
    fun headlineNumbersReflectClockAndSuspension() {
        var state = Box.state((1..4).map { Box.word(it) })
        state = Box.inject(state, Box.sched("w01", dueMillis = now - 60_000, lastReviewMillis = Box.plusDays(now, -1.0)))
        state = Box.inject(state, Box.sched("w02", dueMillis = Box.plusDays(now, 1.0), lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w03", dueMillis = now, lastReviewMillis = now, suspended = true))

        val stats = BoxEngine.statistics(state, now, Box.TZ)
        assertEquals(2, stats.activeCount)
        assertEquals(1, stats.dueCount)
        assertEquals(1, stats.suspendedCount)
    }

    @Test
    fun consolidatedCountsOnlyReviewCardsAtOrAboveTheConsolidatedThreshold() {
        var state = Box.state((1..3).map { Box.word(it) })
        state = Box.inject(state, Box.sched("w01", stability = 6.0, dueMillis = now, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w02", stability = 5.9, dueMillis = now, lastReviewMillis = now))
        state = Box.inject(
            state,
            // Stable enough, but still stepping through Learning — not consolidated.
            Box.sched("w03", phase = CardPhase.Learning, stability = 9.0, dueMillis = now, lastReviewMillis = now),
        )

        val stats = BoxEngine.statistics(state, now, Box.TZ)
        assertEquals(3, stats.activeCount)
        assertEquals(1, stats.consolidatedCount)
    }

    @Test
    fun areaBreakdownTotalsConsolidatedAndPhraseLocks() {
        var state = Box.state(
            listOf(
                Box.word(1, area = "kitchen"), Box.word(2, area = "kitchen"),
                Box.phrase("p-locked", components = listOf("w01", "w02"), area = "kitchen", seedIndex = 90),
                Box.phrase("p-free", components = emptyList(), area = "kitchen", seedIndex = 91),
                Box.word(3, area = "market"),
            ),
        )
        val future = Box.plusDays(now, 5.0)
        state = Box.inject(state, Box.sched("w01", stability = 7.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(
            state,
            Box.sched("w02", phase = CardPhase.Learning, stability = 1.0, dueMillis = future, lastReviewMillis = now),
        )

        val stats = BoxEngine.statistics(state, now, Box.TZ)
        assertEquals(listOf("kitchen", "market"), stats.areas.map { it.name })
        val kitchen = stats.areas[0]
        assertEquals(4, kitchen.total)
        assertEquals(2, kitchen.active)
        assertEquals(1, kitchen.consolidated) // only w01: Review phase & stability ≥ 6.0
        assertEquals(1, kitchen.phrasesLocked) // p-locked: w02 not stable yet
        assertEquals(1, kitchen.phrasesUnlocked) // p-free has no components
        assertEquals(
            AreaStatistics(
                "market", total = 1, active = 0, consolidated = 0, settling = 0,
                phrasesLocked = 0, phrasesUnlocked = 0,
            ),
            stats.areas[1],
        )
    }
}
