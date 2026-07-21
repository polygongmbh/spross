package net.spross.kern.box

import kotlin.time.Instant
import net.spross.kern.model.CardPhase
import net.spross.kern.model.ExerciseUnit
import net.spross.kern.model.ExerciseUnits
import net.spross.kern.model.UnitScheduling

/**
 * Join-filtered unit inventory. Composition, dueNow, statistics, and exposure all read
 * through here; only the phrase-unlock gate and `answer()` history reads touch
 * `state.scheduling` raw by key.
 */
internal object Inventory {

    /** Every exercise unit of the current join, in pinned unit order. */
    fun joinedUnits(state: BoxState): List<ExerciseUnit> =
        state.cards.values.flatMap(ExerciseUnits::of).sortedWith(ExerciseUnits.order)

    fun joinedUnitKeys(state: BoxState): Set<String> =
        state.cards.values.flatMap(ExerciseUnits::of).mapTo(mutableSetOf()) { it.key }

    /** Schedules whose unit exists under the current join, sorted by key (deterministic). */
    fun scheduled(state: BoxState): List<UnitScheduling> {
        val joined = joinedUnitKeys(state)
        return state.scheduling.entries
            .filter { it.key in joined }
            .sortedBy { it.key }
            .map { it.value }
    }

    fun active(state: BoxState): List<UnitScheduling> =
        scheduled(state).filter { !it.suspended }

    /** Active units with `due <= now`, oldest first, ties broken by unit key. */
    fun due(state: BoxState, nowEpochMillis: Long): List<UnitScheduling> {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        return active(state)
            .filter { it.due != null && it.due <= now }
            .sortedWith(compareBy({ it.due }, { it.key }))
    }

    /** Concepts holding a learning-pool slot: any joined active unit in Learning phase. */
    fun conceptsInFlight(state: BoxState): Set<String> =
        active(state)
            .filter { it.phase == CardPhase.Learning }
            .mapTo(mutableSetOf()) { it.cardId }

    /** Concepts with at least one active unit — the user-facing "active" denomination. */
    fun activeConceptCount(state: BoxState): Int =
        active(state).mapTo(mutableSetOf()) { it.cardId }.size
}
