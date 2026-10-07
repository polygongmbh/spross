package net.spross.kern.snapshot

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import net.spross.kern.box.ACTIVITY_WINDOW_DAYS
import net.spross.kern.box.localDate
import net.spross.kern.box.streakWindow
import net.spross.kern.box.zoneOf
import net.spross.kern.design.ActivityBars
import net.spross.kern.design.ActivityScale

/**
 * One bar of the widget header's activity strip, sized by [ActivityBars] at [ActivityScale.widget]:
 * [height] in points (dp), the fill's [fillOpacity]; today is the window's last bar.
 */
@Serializable
data class WidgetBar(
    val reviews: Int,
    val height: Double,
    val fillOpacity: Double,
) {
    /** A day with answers fills its bar; a day with none draws a stub. */
    val worked: Boolean get() = reviews > 0
}

/**
 * The widget strip as each render day will draw it, keyed by ISO day:
 * the build day, then every following day through the first whose window holds no answer.
 * An answer only reaches a widget through a fresh build, so after this one the window can only empty,
 * and a widget sizes and scales nothing.
 */
internal fun activityTimeline(
    answerDays: Map<String, Int>,
    nowEpochMillis: Long,
    tzId: String,
): Map<String, List<WidgetBar>> {
    val zone = zoneOf(tzId)
    var day = localDate(nowEpochMillis, tzId)
    val timeline = mutableMapOf<String, List<WidgetBar>>()
    do {
        val at = day.atStartOfDayIn(zone).toEpochMilliseconds()
        val bars = ActivityBars.of(streakWindow(answerDays, ACTIVITY_WINDOW_DAYS, at, tzId), ActivityScale.widget)
            .map { WidgetBar(it.reviews, it.height, it.fillOpacity) }
        timeline[day.toString()] = bars
        day = day.plus(1, DateTimeUnit.DAY)
    } while (bars.any { it.worked })
    return timeline
}
