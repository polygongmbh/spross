package net.spross.kern.box

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import net.spross.kern.model.CardKind
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.DayStats

/** Aggregates for progress UI. All counts are in cards. */
data class BoxStatistics(
    /** Cards with an active (scheduled, non-suspended) schedule. */
    val activeCount: Int,
    /** Active cards that have consolidated (see [Statistics.isConsolidated]); the rest are still fresh. */
    val consolidatedCount: Int,
    /** Active cards due now. */
    val dueCount: Int,
    /** Cards whose schedule is suspended (out of rotation). */
    val suspendedCount: Int,
    /** Days with reviews > 0; a missed day is bridged, two in a row end the run. */
    val streak: Int,
    /** What today still owes the run — see [streakHealth]. */
    val streakHealth: StreakHealth,
    /** The longest such run the box has ever held; equals [streak] when today's run is it. */
    val longestStreak: Int,
    val areas: List<AreaStatistics>,
) {
    /**
     * Active cards that have not consolidated yet — the fresh half of the split.
     * Clamped: a caller may hold a statistics value older than the counts it reads
     * beside, and a negative bucket is never a truth about the box.
     */
    val learningCount: Int get() = maxOf(0, activeCount - consolidatedCount)
}

data class AreaStatistics(
    val name: String,
    /** Cards in the area (any status). */
    val total: Int,
    /** Cards with an active schedule. */
    val active: Int,
    /** Cards in the area that have consolidated (see [Statistics.isConsolidated]). */
    val consolidated: Int,
    /** Active cards in Review, short of the consolidated bar — kern's [GrowthStage.Fresh]. */
    val settling: Int = 0,
    /** Component phrases still waiting for their components to stabilize. */
    val phrasesLocked: Int,
    /** Phrases already introduced, component-free, or with all components stable. */
    val phrasesUnlocked: Int,
) {
    /** Active cards in the area still on their way in — see [BoxStatistics.learningCount]. */
    val learning: Int get() = maxOf(0, active - consolidated)

    /** Cards the area holds that have never been introduced — the third bucket. */
    val notIntroduced: Int get() = maxOf(total - consolidated - learning, 0)

    /**
     * What the three buckets are measured against. Never below the introduced
     * count: [total] comes from the join and can lag the schedules, and a stale
     * total must not make the introduced cards read as more than everything.
     */
    val progressTotal: Int get() = maxOf(total, consolidated + learning, 1)
}

/**
 * Sum of daily activity across independent `dailyStats` maps — one per TARGET
 * language box. Growing is one commitment, not one per language the learner
 * happens to be studying, so a day earns the streak whichever language(s) it
 * was spent on; every count here (not just [DayStats.reviews]) sums the same
 * way, so a caller reading any bucket off the result sees the whole box.
 * A day present in only some maps still merges cleanly — the rest read as
 * [DayStats]'s all-zero default for that day.
 */
fun mergeDailyStats(dailyStatsByLanguage: List<Map<String, DayStats>>): Map<String, DayStats> {
    if (dailyStatsByLanguage.size <= 1) return dailyStatsByLanguage.firstOrNull() ?: emptyMap()
    val days = dailyStatsByLanguage.asSequence().flatMap { it.keys }.toSet()
    return days.associateWith { day ->
        dailyStatsByLanguage.fold(DayStats()) { sum, byDay ->
            val d = byDay[day] ?: return@fold sum
            DayStats(
                reviews = sum.reviews + d.reviews,
                introduced = sum.introduced + d.introduced,
                consolidated = sum.consolidated + d.consolidated,
                activeCount = sum.activeCount + d.activeCount,
            )
        }
    }
}

/** How the streak rule reads one day of the trailing window. */
enum class StreakRole {
    /** Reviews were done; the day is part of the current run and counts toward it. */
    Earned,

    /** No reviews, but the run spans the day — it stalls the streak rather than ending it. */
    Bridged,

    /** Outside the current run: an older active day, an unfinished today, or a gap that ended it. */
    Outside,
}

/** What today still owes the current run, safest first. */
enum class StreakHealth {
    /** Today has reviews: the run is earned and safe until tomorrow. */
    Earned,

    /** Nothing today yet, but yesterday was earned — a miss today is only the run's one bridge. */
    Bridgeable,

    /** Nothing today, and yesterday was already the bridge — a miss today ends the run. */
    Ending,

    /** The streak is 0: there is no run to protect. */
    None,
    ;

    /**
     * Whether a run is standing and today has not yet paid into it — the one state a
     * surface may nag about. [Earned] is safe and [None] has nothing to lose.
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
    dailyStats: Map<String, DayStats>,
    nowEpochMillis: Long,
    tzId: String,
): StreakHealth {
    val today = localDate(nowEpochMillis, tzId)
    val run = Statistics.streakRun(dailyStats, today)
    return when {
        run.isEmpty() -> StreakHealth.None
        run[today] == true -> StreakHealth.Earned
        run[today.minus(1, DateTimeUnit.DAY)] == true -> StreakHealth.Bridgeable
        else -> StreakHealth.Ending
    }
}

/** One day of the activity window: what the box recorded, and how the streak rule reads it. */
data class ActivityDay(
    /** ISO `yyyy-MM-dd` local day key — the same key [BoxState.dailyStats] is keyed by. */
    val day: String,
    /** Local midnight of [day]; callers render the weekday from this, never from a calendar of their own. */
    val dayStartEpochMillis: Long,
    val reviews: Int,
    val role: StreakRole,
)

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
    dailyStats: Map<String, DayStats>,
    days: Int,
    nowEpochMillis: Long,
    tzId: String,
): List<ActivityDay> {
    require(days > 0) { "window needs at least one day" }
    val zone = zoneOf(tzId)
    val today = localDate(nowEpochMillis, tzId)
    val run = Statistics.streakRun(dailyStats, today)
    return (days - 1 downTo 0).map { back ->
        val day = today.minus(back, DateTimeUnit.DAY)
        ActivityDay(
            day = day.toString(),
            dayStartEpochMillis = day.atStartOfDayIn(zone).toEpochMilliseconds(),
            reviews = dailyStats[day.toString()]?.reviews ?: 0,
            role = when (run[day]) {
                true -> StreakRole.Earned
                false -> StreakRole.Bridged
                null -> StreakRole.Outside
            },
        )
    }
}

internal object Statistics {

    fun statistics(
        state: BoxState,
        nowEpochMillis: Long,
        tzId: String,
        otherLanguagesDailyStats: List<Map<String, DayStats>> = emptyList(),
    ): BoxStatistics {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        val active = Inventory.active(state)
        // why: the streak is a box-wide commitment, not a per-target-language one —
        // see [mergeDailyStats] — everything else here stays scoped to THIS join.
        val combinedDailyStats = mergeDailyStats(otherLanguagesDailyStats + state.dailyStats)
        return BoxStatistics(
            activeCount = active.size,
            consolidatedCount = active.count { isConsolidated(state, it) },
            dueCount = active.count { it.due != null && it.due <= now },
            suspendedCount = Inventory.suspendedCount(state),
            streak = streak(combinedDailyStats, nowEpochMillis, tzId),
            streakHealth = streakHealth(combinedDailyStats, nowEpochMillis, tzId),
            longestStreak = longestStreak(combinedDailyStats, nowEpochMillis, tzId),
            areas = areaStatistics(state, active),
        )
    }

    /**
     * "Has this word landed": Review phase at or above [BoxConfig.consolidatedStability].
     * The ONE bar — the stats split, the session-summary tally, phrase unlock, the drill
     * pools, and the in-session presentation rules all ask it. A card that just lapsed is
     * back in Relearning, so it stops being consolidated, which is the point: it needs the
     * support again and has to earn the reach back.
     */
    fun isConsolidated(state: BoxState, sched: CardScheduling): Boolean =
        sched.phase == CardPhase.Review &&
            (sched.memory?.stability ?: 0.0) >= state.config.consolidatedStability

    /**
     * Walk back from today: a missed day is bridged, two in a row end the run. Forgiveness
     * is a property of the neighborhood, not a budget — showing up restores it, so a
     * second miss weeks later never takes back the days built before the first. A bridged
     * day does not increment the count: the streak stalls for a day instead of dying.
     *
     * Today without reviews is not a miss at all (the day isn't over) — it neither breaks
     * the run nor pairs with an empty yesterday.
     */
    fun streak(dailyStats: Map<String, DayStats>, nowEpochMillis: Long, tzId: String): Int =
        streakRun(dailyStats, localDate(nowEpochMillis, tzId)).count { it.value }

    /**
     * The days the current run covers, newest first: date → earned (false = bridged).
     * The one walk both [streak] and [streakWindow] read, so the count and the
     * days shown as covered are the same answer told twice.
     *
     * Bridges past the oldest earned day drop out: forgiveness spans a run, it does
     * not start one.
     */
    fun streakRun(dailyStats: Map<String, DayStats>, today: LocalDate): Map<LocalDate, Boolean> {
        val walked = mutableListOf<Pair<LocalDate, Boolean>>()
        var previousWasMiss = false
        var day = today
        var isToday = true
        while (true) {
            val reviews = dailyStats[day.toString()]?.reviews ?: 0
            if (reviews > 0) {
                walked += day to true
                previousWasMiss = false
            } else if (!isToday) {
                if (previousWasMiss) break
                previousWasMiss = true
                walked += day to false
            }
            isToday = false
            day = day.minus(1, DateTimeUnit.DAY)
        }
        val oldestEarned = walked.indexOfLast { it.second }
        if (oldestEarned < 0) return emptyMap()
        return walked.take(oldestEarned + 1).toMap()
    }

    /**
     * The longest run the box has ever held, under the same rule [streak] walks back
     * with: a 0-review day inside a run is bridged and does not count, two in a row end
     * it. `dailyStats` is never pruned, so this reaches back to the first day the box
     * was used.
     *
     * Today can extend a run but never end one — the day is not over — which is what
     * keeps a record set today standing while it is still being added to, and keeps
     * this ≥ [streak] at all times.
     */
    fun longestStreak(dailyStats: Map<String, DayStats>, nowEpochMillis: Long, tzId: String): Int {
        val today = localDate(nowEpochMillis, tzId)
        var day = dailyStats.keys.minOrNull()?.let { LocalDate.parse(it) } ?: return 0
        var best = 0
        var run = 0
        var previousWasMiss = false
        while (day <= today) {
            val reviews = dailyStats[day.toString()]?.reviews ?: 0
            if (reviews > 0) {
                run += 1
                if (run > best) best = run
                previousWasMiss = false
            } else if (day != today) {
                if (previousWasMiss) run = 0 else previousWasMiss = true
            }
            day = day.plus(1, DateTimeUnit.DAY)
        }
        return best
    }

    private fun areaStatistics(state: BoxState, active: List<CardScheduling>): List<AreaStatistics> {
        val activeCards = active.mapTo(mutableSetOf()) { it.cardId }
        return state.cards.values.groupBy { it.area }.entries
            .sortedBy { it.key }
            .map { (area, cards) ->
                var active = 0
                var consolidated = 0
                var settling = 0
                var locked = 0
                var unlocked = 0
                for (card in cards) {
                    if (card.id in activeCards) active += 1
                    val sched = state.scheduling[card.id]
                    if (sched != null && !sched.suspended && isConsolidated(state, sched)) consolidated += 1
                    // Counted on its own bar, never off `consolidated`: that one also carries
                    // the matured cards, so the two buckets read different rungs.
                    if (sched != null && !sched.suspended && stageOf(state, sched) == GrowthStage.Fresh) {
                        settling += 1
                    }
                    if (card.kind == CardKind.Phrase) {
                        val open = sched != null || card.components.isEmpty() ||
                            Growth.isPhraseUnlocked(state, card)
                        if (open) unlocked += 1 else locked += 1
                    }
                }
                AreaStatistics(
                    name = area, total = cards.size, active = active, consolidated = consolidated,
                    settling = settling, phrasesLocked = locked, phrasesUnlocked = unlocked,
                )
            }
    }
}
