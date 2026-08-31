package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import net.spross.kern.model.DayStats
import net.spross.kern.model.Rating

/** Day keys: ISO in the caller's zone — DST-safe, calendar-independent. */
class DayKeyTests {

    private fun local(tz: String, y: Int, mo: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime(y, mo, d, h, min).toInstant(TimeZone.of(tz)).toEpochMilliseconds()

    /**
     * Zones are resolved by name and the last one is kept ([zoneOf]), so a learner who
     * travels — or two calls a frame apart naming different zones — must not be handed
     * the zone before it. Alternating proves the memo answers the name it was asked.
     */
    @Test
    fun alternatingZonesEachAnswerTheirOwn() {
        // 02:00 UTC: still yesterday evening in New York, already afternoon on Kiritimati.
        val earlyUtc = Box.millis(2026, 7, 1, 2, 0)
        repeat(3) {
            assertEquals("2026-07-01", dayKey(earlyUtc, "Pacific/Kiritimati"))
            assertEquals("2026-07-01", dayKey(earlyUtc, "UTC"))
            assertEquals("2026-06-30", dayKey(earlyUtc, "America/New_York"))
        }
    }

    @Test
    fun dayKeyFollowsTheCallersZone() {
        val noonUtc = Box.millis(2026, 7, 1)
        assertEquals("2026-07-01", dayKey(noonUtc, "UTC"))
        assertEquals("2026-07-02", dayKey(noonUtc, "Pacific/Kiritimati")) // UTC+14
        assertEquals("2026-07-01", dayKey(noonUtc, "America/New_York"))
    }

    // A non-Gregorian device region still yields ISO keys (v1's latent bug, fixed).
    @Test
    fun dayKeyIsIsoRegardlessOfRegionalCalendar() {
        val lateUtc = Box.millis(2026, 7, 1, 23, 30)
        assertEquals("2026-07-02", dayKey(lateUtc, "Asia/Tehran")) // UTC+03:30, next local day
    }

    @Test
    fun dstSpringForwardKeepsTheLocalDate() {
        // Berlin skips 02:00→03:00 on 2026-03-29.
        assertEquals("2026-03-29", dayKey(local("Europe/Berlin", 2026, 3, 29, 1, 30), "Europe/Berlin"))
        assertEquals("2026-03-29", dayKey(local("Europe/Berlin", 2026, 3, 29, 3, 30), "Europe/Berlin"))
        val justBeforeLocalMidnight = local("Europe/Berlin", 2026, 3, 28, 23, 59)
        assertEquals("2026-03-28", dayKey(justBeforeLocalMidnight, "Europe/Berlin"))
        assertEquals("2026-03-28", dayKey(justBeforeLocalMidnight, "UTC")) // 22:59 UTC
    }

    @Test
    fun dstFallBackMapsBothOccurrencesToOneDate() {
        // New York repeats 01:00–02:00 on 2026-11-01: 01:30 EDT and 01:30 EST.
        val firstUtcMillis = Box.millis(2026, 11, 1, 5, 30)
        val secondUtcMillis = Box.millis(2026, 11, 1, 6, 30)
        assertEquals("2026-11-01", dayKey(firstUtcMillis, "America/New_York"))
        assertEquals("2026-11-01", dayKey(secondUtcMillis, "America/New_York"))
    }

    @Test
    fun introductionAndSessionFoldUseCallerZone() {
        var state = Box.state(listOf(Box.word(1)))
        val lateUtc = Box.millis(2026, 7, 1, 23, 30) // already July 2 in Kiritimati
        state = BoxEngine.answer(state, "w01", Rating.Good, lateUtc, "Pacific/Kiritimati")
        assertEquals(1, state.newIntroduced["2026-07-02"])

        state = BoxEngine.endSession(state, reviewsDone = 1, nowEpochMillis = lateUtc, tzId = "Pacific/Kiritimati")
        assertEquals(
            // A single Good doesn't consolidate on sight (only Easy does) — not yet consolidated.
            DayStats(reviews = 1, introduced = 1, consolidated = 0, activeCount = 1),
            state.dailyStats["2026-07-02"],
        )
    }

    @Test
    fun pruneCutoffIsStringComparedAtSixtyDays() {
        var state = Box.state(listOf(Box.word(1)))
        state = state.copy(
            // 2026-07-01 − 59 days = 2026-05-03: the last kept key.
            newIntroduced = mapOf("2026-05-02" to 1, "2026-05-03" to 2),
        )
        state = BoxEngine.endSession(state, reviewsDone = 0, nowEpochMillis = Box.day1, tzId = Box.TZ)
        assertNull(state.newIntroduced["2026-05-02"])
        assertEquals(2, state.newIntroduced["2026-05-03"])
    }

    @Test
    fun endOfTomorrowIsTheSecondLocalMidnightAhead() {
        // 23:30 UTC on July 1 is already July 2 in Kiritimati, so its horizon is July 4 local.
        val lateUtc = Box.millis(2026, 7, 1, 23, 30)
        assertEquals(local("UTC", 2026, 7, 3, 0, 0), endOfTomorrow(lateUtc, "UTC").toEpochMilliseconds())
        assertEquals(
            local("Pacific/Kiritimati", 2026, 7, 4, 0, 0),
            endOfTomorrow(lateUtc, "Pacific/Kiritimati").toEpochMilliseconds(),
        )
        // Berlin falls back on 2026-10-25: the horizon is a local midnight, not now + 48h.
        val beforeFallBack = local("Europe/Berlin", 2026, 10, 24, 20, 0)
        assertEquals(
            local("Europe/Berlin", 2026, 10, 26, 0, 0),
            endOfTomorrow(beforeFallBack, "Europe/Berlin").toEpochMilliseconds(),
        )
    }

    @Test
    fun dueThroughTomorrowReadsTheHorizonKernNames() {
        var state = Box.state(listOf(Box.word(1), Box.word(2), Box.word(3)))
        val now = Box.millis(2026, 7, 1, 20, 0)
        state = Box.inject(state, Box.sched("w01", dueMillis = now, lastReviewMillis = now))
        // Tomorrow evening: inside the horizon. Day after: outside it.
        state = Box.inject(state, Box.sched("w02", dueMillis = Box.millis(2026, 7, 2, 22, 0), lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w03", dueMillis = Box.millis(2026, 7, 3, 10, 0), lastReviewMillis = now))

        val horizon = endOfTomorrow(now, Box.TZ).toEpochMilliseconds()
        assertEquals(listOf("w01", "w02"), BoxEngine.dueNow(state, horizon).sorted())
    }

    @Test
    fun dayPartFollowsTheLanguagesOwnBoundaries() {
        val at = { h: Int, lang: String? ->
            dayPart(local("Europe/Berlin", 2026, 7, 1, h, 0), "Europe/Berlin", lang)
        }
        assertEquals(DayPart.Night, at(3, "de"))
        assertEquals(DayPart.Morning, at(7, "de"))
        // Four in the afternoon: still nachmittags in German, already jioni in Swahili.
        assertEquals(DayPart.Day, at(16, "de"))
        assertEquals(DayPart.Evening, at(16, "sw"))
        // Seven in the evening: abends in German, still de la tarde in Spanish.
        assertEquals(DayPart.Evening, at(19, "de"))
        assertEquals(DayPart.Evening, at(19, "es"))
        // Noon: the languages that name it with a word of its own leave the drill nothing
        // there, so the hour before it answers.
        assertEquals(DayPart.Morning, at(12, "fr"))
        // A language the drills do not cover reads on English's hours.
        assertEquals(DayPart.Day, at(14, "zz"))
        // The phrasing holds inside a part and is picked afresh in the next one: same hour,
        // different minute, doesn't reroll between renders.
        val variant = { h: Int, min: Int ->
            partVariant(local("Europe/Berlin", 2026, 7, 1, h, min), "Europe/Berlin", "de", 2)
        }
        assertEquals(variant(10, 0), variant(10, 45))
        assertTrue((0..23).all { variant(it, 0) in 0..1 })
    }

    @Test
    fun chromePartIgnoresTheTaughtLanguagesOwnHours() {
        val now = local("Europe/Berlin", 2026, 7, 1, 19, 30)
        // Half seven: usiku already in Swahili, but chrome's own "night owl" schedule
        // isn't there yet — chrome keeps the same hours no matter which language is
        // spoken or learned.
        assertEquals(DayPart.Night, dayPart(now, "Europe/Berlin", "sw"))
        assertEquals(DayPart.Evening, chromePart(now, "Europe/Berlin"))
    }

    @Test
    fun streakWalksLocalDaysAcrossDstChange() {
        val state = Box.state(listOf(Box.word(1))).copy(
            dailyStats = listOf("2026-10-24", "2026-10-25", "2026-10-26")
                .associateWith { DayStats(reviews = 1, introduced = 0, activeCount = 1) },
        )
        // Berlin falls back on 2026-10-25; the walk still visits each local date once.
        val eveningAfter = local("Europe/Berlin", 2026, 10, 26, 20, 0)
        assertEquals(3, BoxEngine.statistics(state, eveningAfter, "Europe/Berlin").streak)
    }
}
