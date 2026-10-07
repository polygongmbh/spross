package net.spross.kern.design

import kotlin.math.max
import kotlin.math.sqrt
import net.spross.kern.box.ActivityDay
import net.spross.kern.box.StreakRole

/**
 * How the underline beneath one column reads.
 *
 * Two runs, not one: the streak the flame counts is the learner's CURRENT run and takes the
 * accent, while an older stretch of active days is history and takes the bars' own hue.
 * Drawn as one continuous rule wherever neighbors agree.
 */
enum class StripRun { Bare, Current, Past }

/**
 * One column of an activity strip: the day's volume expressed twice
 * ([height] in points (dp) and [fillOpacity]), and which run it belongs to.
 * [dayStartEpochMillis] is kern's own local midnight, the instant the weekday letter is read from.
 */
data class ActivityBar(
    val day: String,
    val dayStartEpochMillis: Long,
    val reviews: Int,
    val height: Double,
    val fillOpacity: Double,
    val isToday: Boolean,
    val run: StripRun,
) {
    /** A day with answers fills its column; a day with none draws a stub. */
    val worked: Boolean get() = reviews > 0

    /** Today with nothing on it yet: outlined rather than filled, and never a gray gap. */
    val isEmptyToday: Boolean get() = !worked && isToday
}

/**
 * The sizes one strip is drawn at, in points (dp):
 * the tallest a bar grows (the row reserves exactly this), the floor a worked day never drops under,
 * and the stub an unworked day draws.
 */
class ActivityScale private constructor(val maxHeight: Double, val minHeight: Double, val stubHeight: Double) {
    companion object {
        /** The app's fortnight under the trees. */
        val strip = ActivityScale(52.0, 10.0, 6.0)

        /** The widget header's, beside a caption-height flame. */
        val widget = ActivityScale(16.0, 3.0, 1.5)
    }
}

/**
 * Every activity strip's arithmetic — the app's and the widget's.
 *
 * The √-scale is the rule the look is built on: one 40-review day must not squash every
 * 4-review day into the same stub. The intensity repeats the volume for the short bars,
 * which keeps a quiet fortnight legible instead of uniformly pale.
 */
object ActivityBars {
    private const val BASE_OPACITY = 0.45
    private const val OPACITY_RANGE = 0.55

    /**
     * [days] as columns in the order kern hands them over — oldest first, today last.
     * The busiest day sets the scale, floored at 1 so an empty fortnight divides by something.
     */
    fun of(days: List<ActivityDay>, scale: ActivityScale = ActivityScale.strip): List<ActivityBar> {
        val busiest = max(days.maxOfOrNull { it.reviews } ?: 0, 1)
        return days.mapIndexed { index, day ->
            val scaled = if (day.reviews > 0) sqrt(day.reviews.toDouble() / busiest) else 0.0
            ActivityBar(
                day = day.day,
                dayStartEpochMillis = day.dayStartEpochMillis,
                reviews = day.reviews,
                height = if (day.reviews > 0) max(scale.minHeight, scale.maxHeight * scaled) else scale.stubHeight,
                // why: float arithmetic can carry the busiest day a hair past 1, and an alpha outside 0..1 is not a color.
                fillOpacity = (BASE_OPACITY + OPACITY_RANGE * scaled).coerceIn(0.0, 1.0),
                isToday = index == days.lastIndex,
                // Earned and Bridged are both inside the run: a bridged gap stalls the streak
                // rather than ending it, and an underline that skipped it would draw two runs where the flame counts one.
                run = when {
                    day.role != StreakRole.Outside -> StripRun.Current
                    day.reviews > 0 -> StripRun.Past
                    else -> StripRun.Bare
                },
            )
        }
    }

    /** How many days of the window were worked — the number the strip's spoken summary names. */
    fun activeDays(bars: List<ActivityBar>): Int = bars.count { it.worked }
}
