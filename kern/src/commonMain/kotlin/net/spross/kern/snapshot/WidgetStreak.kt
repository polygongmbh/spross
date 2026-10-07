package net.spross.kern.snapshot

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import net.spross.kern.box.Statistics
import net.spross.kern.box.StreakHealth
import net.spross.kern.box.localDate
import net.spross.kern.box.streakHealth
import net.spross.kern.box.zoneOf

/**
 * The streak and its health as one render day reads them, with the flame's grade
 * ([StreakHealth.flameOpacity], [StreakHealth.flameSaturation]) spelled out
 * for the iOS extension, which cannot ask the enum.
 */
@Serializable
internal data class WidgetStreakDto(
    val streak: Int,
    val health: StreakHealth,
    val flameOpacity: Double,
    val flameSaturation: Double,
) {
    constructor(streak: Int, health: StreakHealth) :
        this(streak, health, health.flameOpacity, health.flameSaturation)
}

/**
 * The streak as each render day will read it, keyed by ISO day:
 * the build day, then every following day through the first one with no run left.
 * Each entry is [Statistics.streak] and [streakHealth] asked for that day,
 * so a widget walks and counts nothing.
 *
 * An answer only reaches a widget through a fresh build, so after this one the run can only age —
 * [StreakHealth.NoRun] arrives within three days and holds from there on.
 */
internal fun streakTimeline(
    answerDays: Map<String, Int>,
    nowEpochMillis: Long,
    tzId: String,
): Map<String, WidgetStreakDto> {
    val zone = zoneOf(tzId)
    var day = localDate(nowEpochMillis, tzId)
    val timeline = mutableMapOf<String, WidgetStreakDto>()
    do {
        val at = day.atStartOfDayIn(zone).toEpochMilliseconds()
        val entry = WidgetStreakDto(Statistics.streak(answerDays, at, tzId), streakHealth(answerDays, at, tzId))
        timeline[day.toString()] = entry
        day = day.plus(1, DateTimeUnit.DAY)
    } while (entry.health != StreakHealth.NoRun)
    return timeline
}

/**
 * A render-day timeline's entry for the local day of [nowEpochMillis]
 * ([streakTimeline], [activityTimeline]):
 * a day past its last entry reads the last, one before its first
 * (a device clock behind the phone's) reads the first.
 */
internal fun <T> onRenderDay(
    timeline: Map<String, T>,
    nowEpochMillis: Long,
    tzId: String,
): T {
    val today = localDate(nowEpochMillis, tzId).toString()
    val days = timeline.keys.sorted()
    return timeline.getValue(days.lastOrNull { it <= today } ?: days.first())
}
