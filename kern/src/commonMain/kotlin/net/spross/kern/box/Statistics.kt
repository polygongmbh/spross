package net.spross.kern.box

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
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
    val settledCount: Int,
    /** Active cards due now. */
    val dueCount: Int,
    /** Cards whose schedule is suspended (out of rotation). */
    val suspendedCount: Int,
    /** New words that could enter now; 0 when the health gate is closed. */
    val newSlotsAvailable: Int,
    /** Consecutive days with reviews > 0; one missed day is forgiven. */
    val streak: Int,
    /** The longest such run the box has ever held; equals [streak] when today's run is it. */
    val longestStreak: Int,
    val areas: List<AreaStatistics>,
)

data class AreaStatistics(
    val name: String,
    /** Cards in the area (any status). */
    val total: Int,
    /** Cards with an active schedule. */
    val active: Int,
    /** Cards in the area that have consolidated (see [Statistics.isConsolidated]). */
    val settled: Int,
    /** Component phrases still waiting for their components to stabilize. */
    val phrasesLocked: Int,
    /** Phrases already introduced, component-free, or with all components stable. */
    val phrasesUnlocked: Int,
)

internal object Statistics {

    fun statistics(state: BoxState, nowEpochMillis: Long, tzId: String): BoxStatistics {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        val active = Inventory.active(state)
        return BoxStatistics(
            activeCount = active.size,
            settledCount = active.count { isConsolidated(state, it) },
            dueCount = active.count { it.due != null && it.due <= now },
            suspendedCount = Inventory.scheduled(state).count { it.suspended },
            newSlotsAvailable = Growth.gatedNewBudget(state, nowEpochMillis),
            streak = streak(state.dailyStats, nowEpochMillis, tzId),
            longestStreak = longestStreak(state.dailyStats, nowEpochMillis, tzId),
            areas = areaStatistics(state),
        )
    }

    /**
     * A card has settled once it sits in Review at or above [BoxConfig.settledStability].
     * THE predicate for "has this word landed": it gates phrase unlock
     * ([Growth.isComponentStable]), splits settled from fresh in the progress UI,
     * and decides which presentation supports a word still on its way in. A card
     * that just lapsed is back in Relearning, so it stops being settled — which is the
     * point: it needs the support again.
     */
    fun isSettled(state: BoxState, sched: CardScheduling): Boolean =
        sched.phase == CardPhase.Review &&
            (sched.memory?.stability ?: 0.0) >= state.config.settledStability

    /**
     * The stricter "really landed" bar: Review phase at or above
     * [BoxConfig.consolidatedStability]. Feeds the fresh/settled stats split, the
     * session-summary tally, and phrase unlock — never budget pacing or in-session
     * presentation support, which stay on the faster [isSettled].
     */
    fun isConsolidated(state: BoxState, sched: CardScheduling): Boolean =
        sched.phase == CardPhase.Review &&
            (sched.memory?.stability ?: 0.0) >= state.config.consolidatedStability

    /**
     * Walk back from today. Today without reviews neither breaks the streak nor consumes
     * forgiveness (the day isn't over); afterwards exactly ONE 0-review day is forgiven,
     * the next miss ends the streak. Forgiven days do not increment the count.
     */
    fun streak(dailyStats: Map<String, DayStats>, nowEpochMillis: Long, tzId: String): Int {
        var count = 0
        var forgivenessLeft = 1
        var day = localDate(nowEpochMillis, tzId)
        var isToday = true
        while (true) {
            val reviews = dailyStats[day.toString()]?.reviews ?: 0
            if (reviews > 0) {
                count += 1
            } else if (!isToday) {
                if (forgivenessLeft > 0) forgivenessLeft -= 1 else break
            }
            isToday = false
            day = day.minus(1, DateTimeUnit.DAY)
        }
        return count
    }

    /**
     * The longest run the box has ever held, under the same rule [streak] walks back
     * with: one 0-review day inside a run is bridged and does not count, a second in a
     * row ends it, and each fresh run is forgiven once again. `dailyStats` is never
     * pruned, so this reaches back to the first day the box was used.
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
        var forgivenessLeft = 1
        while (day <= today) {
            val reviews = dailyStats[day.toString()]?.reviews ?: 0
            if (reviews > 0) {
                run += 1
                if (run > best) best = run
            } else if (day != today) {
                if (forgivenessLeft > 0) {
                    forgivenessLeft -= 1
                } else {
                    run = 0
                    forgivenessLeft = 1
                }
            }
            day = day.plus(1, DateTimeUnit.DAY)
        }
        return best
    }

    private fun areaStatistics(state: BoxState): List<AreaStatistics> {
        val activeCards = Inventory.active(state).mapTo(mutableSetOf()) { it.cardId }
        return state.cards.values.groupBy { it.area }.entries
            .sortedBy { it.key }
            .map { (area, cards) ->
                var active = 0
                var settled = 0
                var locked = 0
                var unlocked = 0
                for (card in cards) {
                    if (card.id in activeCards) active += 1
                    val sched = state.scheduling[card.id]
                    if (sched != null && !sched.suspended && isConsolidated(state, sched)) settled += 1
                    if (card.kind == CardKind.Phrase) {
                        val open = sched != null || card.components.isEmpty() ||
                            Growth.isPhraseUnlocked(state, card)
                        if (open) unlocked += 1 else locked += 1
                    }
                }
                AreaStatistics(area, cards.size, active, settled, locked, unlocked)
            }
    }
}
