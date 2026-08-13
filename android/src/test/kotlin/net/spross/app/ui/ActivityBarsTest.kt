package net.spross.app.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.ActivityDay
import net.spross.kern.box.StreakRole

/**
 * The strip's own arithmetic. The WINDOW and each day's streak role are kern's
 * (`streakWindow`) and pinned in `:kern:jvmTest`; what is checked here is the shape this
 * platform draws them in — the √-scale, the floor, the stub, the intensity, and which
 * columns join into one run.
 */
class ActivityBarsTest {

    private fun day(
        key: String,
        reviews: Int,
        role: StreakRole = StreakRole.Outside,
    ) = ActivityDay(day = key, dayStartEpochMillis = 0L, reviews = reviews, role = role)

    private fun near(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) < 0.01f, "expected ~$expected, was $actual")

    @Test
    fun theBusiestDayFillsTheColumnAndTheRestScaleBySquareRoot() {
        val bars = ActivityBars.of(listOf(day("d1", 4), day("d2", 26)))

        near(52f, bars[1].heightDp)
        near(1f, bars[1].fillOpacity)
        // √(4/26) ≈ 0.392 — a linear scale would have squashed this to a stub.
        near(0.392f, bars[0].scaled)
        near(20.4f, bars[0].heightDp)
        near(0.666f, bars[0].fillOpacity)
    }

    @Test
    fun aDayWithReviewsIsNeverShorterThanTheFloor() {
        val bars = ActivityBars.of(listOf(day("d1", 1), day("d2", 400)))

        assertEquals(ActivityBars.MIN_BAR_DP, bars[0].heightDp)
    }

    @Test
    fun aDayWithNoneIsAStubAndTodayWithNoneIsOutlinedInstead() {
        val bars = ActivityBars.of(listOf(day("d1", 0), day("d2", 5), day("today", 0)))

        assertEquals(ActivityBars.STUB_DP, bars[0].heightDp)
        assertTrue(!bars[0].isEmptyToday)
        assertTrue(bars[2].isToday)
        assertTrue(bars[2].isEmptyToday, "an empty today is 'nothing yet', never a gray gap")
    }

    @Test
    fun anEmptyFortnightStillDividesBySomething() {
        val bars = ActivityBars.of(List(14) { day("d$it", 0) })

        assertEquals(14, bars.size)
        assertTrue(bars.all { it.heightDp == ActivityBars.STUB_DP })
        assertEquals(0, ActivityBars.activeDays(bars))
    }

    @Test
    fun bridgedDaysStayInsideTheCurrentRun() {
        val bars = ActivityBars.of(
            listOf(
                day("d1", 5, StreakRole.Earned),
                day("d2", 0, StreakRole.Bridged),
                day("d3", 7, StreakRole.Earned),
                day("today", 0, StreakRole.Outside),
            ),
        )

        assertEquals(
            listOf(StripRun.Current, StripRun.Current, StripRun.Current, StripRun.None),
            bars.map { it.run },
        )
    }

    @Test
    fun anOlderActiveDayOutsideTheRunTakesTheBarsHueInstead() {
        val bars = ActivityBars.of(
            listOf(
                day("d1", 3, StreakRole.Outside),
                day("d2", 0, StreakRole.Outside),
                day("d3", 4, StreakRole.Earned),
                day("today", 2, StreakRole.Earned),
            ),
        )

        assertEquals(
            listOf(StripRun.Past, StripRun.None, StripRun.Current, StripRun.Current),
            bars.map { it.run },
        )
        assertEquals(3, ActivityBars.activeDays(bars))
    }

    @Test
    fun onlyTheLastColumnIsToday() {
        val bars = ActivityBars.of(List(14) { day("d$it", 1) })

        assertEquals(listOf(13), bars.indices.filter { bars[it].isToday })
    }
}
