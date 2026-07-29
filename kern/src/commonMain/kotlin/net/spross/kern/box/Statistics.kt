package net.spross.kern.box

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import net.spross.kern.model.CardKind
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.DayStats

/** Aggregates for progress UI. All counts are in cards. */
data class BoxStatistics(
    /** Cards with an active (scheduled, non-suspended) schedule. */
    val activeCount: Int,
    /** Active cards that have settled (see [Statistics.isSitting]); the rest are still fresh. */
    val sittingCount: Int,
    /** Active cards due now. */
    val dueCount: Int,
    /** Cards whose schedule is suspended (out of rotation). */
    val suspendedCount: Int,
    /** New words that could enter now; 0 when the health gate is closed. */
    val newSlotsAvailable: Int,
    /** Consecutive days with reviews > 0; one missed day is forgiven. */
    val streak: Int,
    val areas: List<AreaStatistics>,
)

data class AreaStatistics(
    val name: String,
    /** Cards in the area (any status). */
    val total: Int,
    /** Cards with an active schedule. */
    val active: Int,
    /** Cards in the area that have settled (see [Statistics.isSitting]). */
    val sitting: Int,
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
            sittingCount = active.count { isSitting(state, it) },
            dueCount = active.count { it.due != null && it.due <= now },
            suspendedCount = Inventory.scheduled(state).count { it.suspended },
            newSlotsAvailable = Growth.gatedNewBudget(state, nowEpochMillis),
            streak = streak(state.dailyStats, nowEpochMillis, tzId),
            areas = areaStatistics(state),
        )
    }

    /**
     * A card has settled once it sits in Review at or above [BoxConfig.sittingStability].
     * THE predicate for "has this word landed": it gates phrase unlock
     * ([Growth.isComponentStable]), splits sitting from fresh in the progress UI,
     * and decides which presentation supports a word still on its way in. A card
     * that just lapsed is back in Relearning, so it stops sitting — which is the
     * point: it needs the support again.
     */
    fun isSitting(state: BoxState, sched: CardScheduling): Boolean =
        sched.phase == CardPhase.Review &&
            (sched.memory?.stability ?: 0.0) >= state.config.sittingStability

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

    private fun areaStatistics(state: BoxState): List<AreaStatistics> {
        val activeCards = Inventory.active(state).mapTo(mutableSetOf()) { it.cardId }
        return state.cards.values.groupBy { it.area }.entries
            .sortedBy { it.key }
            .map { (area, cards) ->
                var active = 0
                var sitting = 0
                var locked = 0
                var unlocked = 0
                for (card in cards) {
                    if (card.id in activeCards) active += 1
                    val sched = state.scheduling[card.id]
                    if (sched != null && !sched.suspended && isSitting(state, sched)) sitting += 1
                    if (card.kind == CardKind.Phrase) {
                        val open = sched != null || card.components.isEmpty() ||
                            Growth.isPhraseUnlocked(state, card)
                        if (open) unlocked += 1 else locked += 1
                    }
                }
                AreaStatistics(area, cards.size, active, sitting, locked, unlocked)
            }
    }
}
