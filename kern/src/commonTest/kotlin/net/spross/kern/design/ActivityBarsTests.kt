package net.spross.kern.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.ActivityDay
import net.spross.kern.box.StreakRole

class ActivityBarsTests {

    private fun day(
        key: String,
        reviews: Int,
        role: StreakRole = StreakRole.Outside,
    ) = ActivityDay(day = key, dayStartEpochMillis = 0L, reviews = reviews, role = role)

    @Test
    fun aQuieterDayStandsShorterAndPalerThanTheBusiestButAboveAStub() {
        val bars = ActivityBars.of(listOf(day("d1", 4), day("d2", 26), day("d3", 0)))

        assertTrue(bars[0].height < bars[1].height)
        assertTrue(bars[0].fillOpacity < bars[1].fillOpacity)
        assertTrue(bars[2].height < bars[0].height)
    }

    @Test
    fun aDayWithNoneIsAStubAndTodayWithNoneIsOutlinedInstead() {
        val bars = ActivityBars.of(listOf(day("d1", 0), day("d2", 5), day("today", 0)))

        assertFalse(bars[0].worked)
        assertFalse(bars[0].isEmptyToday)
        assertTrue(bars[2].isEmptyToday, "an empty today is 'nothing yet', never a gray gap")
        assertEquals(1, ActivityBars.activeDays(bars))
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
            listOf(StripRun.Current, StripRun.Current, StripRun.Current, StripRun.Bare),
            bars.map { it.run },
        )
    }

    @Test
    fun anOlderActiveDayOutsideTheRunIsAPastRun() {
        val bars = ActivityBars.of(
            listOf(
                day("d1", 3, StreakRole.Outside),
                day("d2", 0, StreakRole.Outside),
                day("d3", 4, StreakRole.Earned),
                day("today", 2, StreakRole.Earned),
            ),
        )

        assertEquals(
            listOf(StripRun.Past, StripRun.Bare, StripRun.Current, StripRun.Current),
            bars.map { it.run },
        )
    }
}
