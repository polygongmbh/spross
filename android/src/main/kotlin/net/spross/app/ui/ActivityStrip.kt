package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.spross.app.Chrome
import net.spross.kern.box.ActivityDay

/**
 * The fortnight of practice, one column per day: how much was answered, and which days
 * the streak rule counts.
 *
 * A stub — it emits nothing yet, so Heute stands exactly as it did; the wiring around it
 * is what this file carries. [days] is kern's own walk
 * ([net.spross.kern.box.streakWindow], held on `AppModel.activityWindow`), oldest day
 * first and today last, and [streakDays] is `BoxStatistics.streak`: the strip NEVER
 * counts a run of its own, or the flame and the underline could disagree.
 *
 * What still has to land here (rules, not layout):
 * bar height `max(10, 52 · √(reviews / max))` with a stub for a day with none;
 * fill opacity `0.45 + 0.55 · √share`; today's empty column outlined rather than filled;
 * the run underline styled per column — [net.spross.kern.box.StreakRole.Earned] and
 * `Bridged` both count as in-streak, an older active day takes the bars' hue;
 * the weekday letter read off [ActivityDay.dayStartEpochMillis], never a calendar walk
 * of its own; and one combined a11y label from [Chrome.activityDays] plus
 * [Chrome.streakDays] when a run stands.
 */
@Composable
fun ActivityStrip(
    days: List<ActivityDay>,
    streakDays: Int,
    chrome: Chrome,
    modifier: Modifier = Modifier,
) {
    // Nothing is drawn until the bars are: an absent section reads better than an empty one.
}
