package net.spross.app.ui

import kotlin.math.max
import kotlin.math.sqrt
import net.spross.kern.box.ActivityDay
import net.spross.kern.box.StreakRole

/**
 * How the underline beneath one column reads.
 *
 * Two runs, not one: the streak the flame counts is the learner's CURRENT run and takes the
 * accent, while an older stretch of active days is history and takes the bars' own hue.
 * Drawn as one continuous rule wherever neighbors agree — which is why this is a value
 * a column can be compared on rather than a color picked at paint time.
 */
enum class StripRun { None, Current, Past }

/**
 * One drawn column of the activity strip: the day's volume expressed twice
 * (height and fill intensity), which run it belongs to, and the instant its weekday
 * letter is read from.
 *
 * Everything here is a fraction or a length in dp — no screen positions come from kern,
 * and no calendar is walked on this side ([dayStartEpochMillis] is kern's own local midnight).
 */
data class ActivityBar(
    val day: String,
    val dayStartEpochMillis: Long,
    val reviews: Int,
    /** √ of the day's share of the window's busiest day; 0 on a day with nothing. */
    val scaled: Float,
    val heightDp: Float,
    val fillOpacity: Float,
    val isToday: Boolean,
    val run: StripRun,
) {
    /** Today with nothing on it yet: outlined rather than filled, and never a gray gap. */
    val isEmptyToday: Boolean get() = reviews == 0 && isToday
}

/**
 * The strip's arithmetic, kept out of the composition so it can be read at a glance
 * and pinned by a test.
 *
 * The √-scale is the rule the look is built on: one 40-review day must not squash every
 * 4-review day into the same stub. The intensity repeats the volume for the short bars,
 * which is what keeps a quiet fortnight legible instead of uniformly pale.
 */
object ActivityBars {

    /** The tallest a bar grows; the whole row reserves exactly this much. */
    const val MAX_HEIGHT_DP: Float = 52f

    /** A day with reviews is never shorter than this, however small its share. */
    const val MIN_BAR_DP: Float = 10f

    /** A day with none: present, but making no claim. */
    const val STUB_DP: Float = 6f

    private const val BASE_OPACITY = 0.45f
    private const val OPACITY_RANGE = 0.55f

    /**
     * The window as columns, in the order kern hands it over — oldest first, today last.
     * The busiest day sets the scale, floored at 1 so an empty fortnight divides by something.
     */
    fun of(days: List<ActivityDay>): List<ActivityBar> {
        val busiest = max(days.maxOfOrNull { it.reviews } ?: 0, 1)
        val lastIndex = days.size - 1
        return days.mapIndexed { index, day ->
            val scaled = if (day.reviews > 0) {
                sqrt(day.reviews.toFloat() / busiest.toFloat())
            } else {
                0f
            }
            ActivityBar(
                day = day.day,
                dayStartEpochMillis = day.dayStartEpochMillis,
                reviews = day.reviews,
                scaled = scaled,
                heightDp = if (day.reviews > 0) max(MIN_BAR_DP, MAX_HEIGHT_DP * scaled) else STUB_DP,
                // Clamped: float arithmetic can carry the busiest day a hair past 1,
                // and an alpha outside 0..1 is not a color.
                fillOpacity = (BASE_OPACITY + OPACITY_RANGE * scaled).coerceIn(0f, 1f),
                isToday = index == lastIndex,
                // Earned and Bridged are both inside the run: a bridged gap stalls the
                // streak rather than ending it, and an underline that skipped it would
                // draw two runs where the flame counts one.
                run = when {
                    day.role != StreakRole.Outside -> StripRun.Current
                    day.reviews > 0 -> StripRun.Past
                    else -> StripRun.None
                },
            )
        }
    }

    /** How many days of the window were worked — the number the strip's spoken summary names. */
    fun activeDays(bars: List<ActivityBar>): Int = bars.count { it.reviews > 0 }
}
