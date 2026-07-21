package net.spross.kern.box

import kotlin.math.max
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import net.spross.kern.fsrs.Fsrs
import net.spross.kern.model.CardKind
import net.spross.kern.model.CardPhase
import net.spross.kern.model.DayStats
import net.spross.kern.model.UnitKey
import net.spross.kern.model.UnitScheduling

/** Aggregates for progress UI. All headline counts are CONCEPT-denominated. */
data class BoxStatistics(
    /** Concepts with at least one active (scheduled, non-suspended) unit. */
    val activeCount: Int,
    /** Concepts with at least one active unit due now. */
    val dueCount: Int,
    /** Concepts whose scheduled units are ALL suspended (fully out of rotation). */
    val suspendedCount: Int,
    /** Concepts that could enter the pool now; 0 when the health gate is closed. */
    val newSlotsAvailable: Int,
    /** Consecutive days with reviews > 0; one missed day is forgiven. */
    val streak: Int,
    /** Mean FSRS retrievability over active review-phase UNITS; null if none. */
    val averageRetrievability: Double?,
    val areas: List<AreaStatistics>,
)

data class AreaStatistics(
    val name: String,
    /** Concepts in the area (any status). */
    val total: Int,
    /** Concepts with at least one active unit. */
    val active: Int,
    /** Concepts whose produce unit sits in Review with stability >= unlock threshold. */
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
        val suspendedConcepts = Inventory.scheduled(state)
            .groupBy { it.cardId }
            .count { (_, units) -> units.all { it.suspended } }
        return BoxStatistics(
            activeCount = active.mapTo(mutableSetOf()) { it.cardId }.size,
            dueCount = active
                .filter { it.due != null && it.due <= now }
                .mapTo(mutableSetOf()) { it.cardId }
                .size,
            suspendedCount = suspendedConcepts,
            newSlotsAvailable = Growth.gatedNewBudget(state, nowEpochMillis),
            streak = streak(state.dailyStats, nowEpochMillis, tzId),
            averageRetrievability = averageRetrievability(state, active, now),
            areas = areaStatistics(state),
        )
    }

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

    /** Elapsed measured from each unit's last review (last log entry date). */
    private fun averageRetrievability(
        state: BoxState,
        active: List<UnitScheduling>,
        now: Instant,
    ): Double? {
        val review = active.filter { it.phase == CardPhase.Review && it.memory != null }
        if (review.isEmpty()) return null
        val fsrs = Fsrs(state.config.fsrsParameters())
        val sum = review.sumOf { sched ->
            val last = sched.log.lastOrNull()?.date ?: sched.addedAt
            val elapsed = max(0.0, (now - last).toDouble(DurationUnit.DAYS))
            fsrs.retrievability(elapsed, sched.memory!!.stability)
        }
        return sum / review.size
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
                    val produce = state.scheduling[UnitKey.produce(card.id).encoded]
                    if (produce != null && !produce.suspended &&
                        produce.phase == CardPhase.Review &&
                        (produce.memory?.stability ?: 0.0) >= state.config.phraseUnlockStability
                    ) {
                        sitting += 1
                    }
                    if (card.kind == CardKind.Phrase) {
                        val open = produce != null || card.components.isEmpty() ||
                            Growth.isPhraseUnlocked(state, card)
                        if (open) unlocked += 1 else locked += 1
                    }
                }
                AreaStatistics(area, cards.size, active, sitting, locked, unlocked)
            }
    }
}
