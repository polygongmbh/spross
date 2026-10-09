package net.spross.kern.box

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus

/** How the streak rule reads one day of the trailing window. */
enum class StreakRole {
    /** Reviews were done; the day is part of the current run and counts toward it. */
    Earned,

    /** No reviews, but the run spans the day — it stalls the streak rather than ending it. */
    Bridged,

    /** Outside the current run: an older active day, an unfinished today, or a gap that ended it. */
    Outside,
}

/**
 * What today still owes the current run, safest first —
 * and the grade the 🔥 mark wears for it on every surface that draws the run.
 *
 * The flame is multi-color artwork, so the grade is light and COLOR rather than a second shape:
 * full strength where the day is answered, only a whisper of fade and half the color
 * where a miss would only spend the bridge — a flame cooling, asking for renewal without
 * being faded out — and drained to gray where a miss would end the run, a flame gone cold,
 * which is louder than any amount of fading. With no run behind it the mark is faint as well.
 */
enum class StreakHealth(
    /** How much of the flame shows, 0 (none) to 1 (full). */
    val flameOpacity: Double,
    /** How much of the flame's color is left, 0 (gray) to 1 (full color). */
    val flameSaturation: Double,
) {
    /** Today has reviews: the run is earned and safe until tomorrow. */
    Earned(flameOpacity = 1.0, flameSaturation = 1.0),

    /** Nothing today yet, but yesterday was earned — a miss today is only the run's one bridge. */
    Bridgeable(flameOpacity = 0.9, flameSaturation = 0.5),

    /** Nothing today, and yesterday was already the bridge — a miss today ends the run. */
    Ending(flameOpacity = 0.9, flameSaturation = 0.0),

    /** The streak is 0: there is no run to protect. */
    NoRun(flameOpacity = 0.4, flameSaturation = 0.0),
    ;

    /**
     * Whether a run is standing and today has not yet paid into it — the one state a
     * surface may nag about. [Earned] is safe and [NoRun] has nothing to lose.
     */
    val isExposed: Boolean
        get() = this == Bridgeable || this == Ending
}

/**
 * How exposed the current run is to a day that ends without reviews, read off the
 * same walk [streak] counts — so the number, the window and the health are one answer
 * told three ways.
 */
fun streakHealth(
    answerDays: Map<String, Int>,
    nowEpochMillis: Long,
    tzId: String,
): StreakHealth {
    val today = localDate(nowEpochMillis, tzId)
    val run = Statistics.streakRun(answerDays, today)
    return when {
        run.isEmpty() -> StreakHealth.NoRun
        run[today] == true -> StreakHealth.Earned
        run[today.minus(1, DateTimeUnit.DAY)] == true -> StreakHealth.Bridgeable
        else -> StreakHealth.Ending
    }
}

/** One day of the activity window: what the box recorded, and how the streak rule reads it. */
data class ActivityDay(
    /** ISO `yyyy-MM-dd` local day key — the same key [answerDays] counts under. */
    val day: String,
    /** Local midnight of [day]; callers render the weekday from this, never from a calendar of their own. */
    val dayStartEpochMillis: Long,
    val reviews: Int,
    val role: StreakRole,
)

/**
 * The trailing window both phones' activity strip and widget show — a named home for the
 * number so three independent literal `14`s (one per surface) can't drift apart.
 */
const val ACTIVITY_WINDOW_DAYS: Int = 14

/**
 * The trailing [days] local days, OLDEST first and today last, each with its review
 * count and its place in the current streak.
 *
 * The very walk the streak number is counted from, so the two can never disagree:
 * the Earned days inside the window are exactly the days [BoxStatistics.streak]
 * counted — all of them, whenever the run is no longer than the window.
 *
 * A bridged gap only ever sits BETWEEN earned days. Reaching past the oldest earned
 * day it would be claiming a run that never started — an empty box would light up its
 * last two days.
 */
fun streakWindow(
    answerDays: Map<String, Int>,
    days: Int,
    nowEpochMillis: Long,
    tzId: String,
): List<ActivityDay> {
    require(days > 0) { "window needs at least one day" }
    val zone = zoneOf(tzId)
    val today = localDate(nowEpochMillis, tzId)
    val run = Statistics.streakRun(answerDays, today)
    return (days - 1 downTo 0).map { back ->
        val day = today.minus(back, DateTimeUnit.DAY)
        ActivityDay(
            day = day.toString(),
            dayStartEpochMillis = day.atStartOfDayIn(zone).toEpochMilliseconds(),
            reviews = answerDays[day.toString()] ?: 0,
            role = when (run[day]) {
                true -> StreakRole.Earned
                false -> StreakRole.Bridged
                null -> StreakRole.Outside
            },
        )
    }
}
