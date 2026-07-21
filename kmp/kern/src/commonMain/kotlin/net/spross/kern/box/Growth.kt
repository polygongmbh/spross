package net.spross.kern.box

import kotlin.math.max
import kotlin.math.min
import kotlin.time.Instant
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.CardPhase
import net.spross.kern.model.ExerciseUnit
import net.spross.kern.model.Role
import net.spross.kern.model.UnitKey

/** New-unit candidates: automatic unlock fast-path phrases separate from the rest. */
internal data class NewCandidates(
    val unlockedPhrases: List<String>,
    val newUnits: List<String>,
) {
    companion object {
        val empty = NewCandidates(emptyList(), emptyList())
    }
}

internal object Growth {

    /**
     * Health gate (denominated in UNITS): projected post-session backlog < dueSoftCap,
     * and relearning share < 20 % of active units — the sub-gate applies only once
     * active >= 10 (else it passes; day-one bootstrap).
     */
    fun healthGateOpen(state: BoxState, nowEpochMillis: Long): Boolean {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        val active = Inventory.active(state)
        val dueCount = active.count { it.due != null && it.due <= now }
        val projectedBacklog = dueCount - min(dueCount, state.config.sessionCap)
        if (projectedBacklog >= state.config.dueSoftCap) return false
        if (active.size >= 10) {
            val relearning = active.count { it.phase == CardPhase.Relearning }
            if (relearning >= 0.2 * active.size) return false
        }
        return true
    }

    /** Free learning-pool slots in CONCEPTS, ignoring the health gate. */
    fun learningPoolBudget(state: BoxState): Int =
        max(0, state.config.maxLearning - Inventory.conceptsInFlight(state).size)

    /** Budget after the health gate: 0 when the gate is closed. */
    fun gatedNewBudget(state: BoxState, nowEpochMillis: Long): Int =
        if (healthGateOpen(state, nowEpochMillis)) learningPoolBudget(state) else 0

    // Phrase unlock reads `id|produce` schedules RAW BY KEY — join- and source-independent,
    // so a source switch can never re-lock a phrase.
    fun isComponentStable(state: BoxState, componentId: String): Boolean {
        val sched = state.scheduling[UnitKey.produce(componentId).encoded] ?: return false
        if (sched.suspended || sched.phase != CardPhase.Review) return false
        return (sched.memory?.stability ?: 0.0) >= state.config.phraseUnlockStability
    }

    /** Zero-component phrases never take the fast path (they follow seed order). */
    fun isPhraseUnlocked(state: BoxState, card: Card): Boolean =
        card.kind == CardKind.Phrase &&
            card.components.isNotEmpty() &&
            card.components.all { isComponentStable(state, it) }

    /** Whether the card may enter at all: word, component-free phrase, or unlocked phrase. */
    private fun cardIntroducible(state: BoxState, card: Card): Boolean =
        card.kind != CardKind.Phrase || card.components.isEmpty() || isPhraseUnlocked(state, card)

    /**
     * Whether an unscheduled unit may auto-enter. Produce follows card eligibility;
     * recognize waits for its card's produce unit to sit in Review un-suspended
     * (eligibility lag — kills first-rating echo and halves day-one pool pressure).
     */
    fun isIntroducible(state: BoxState, unit: ExerciseUnit): Boolean = when (unit.role) {
        Role.Produce -> cardIntroducible(state, unit.card)
        Role.Recognize -> {
            val produce = state.scheduling[UnitKey.produce(unit.card.id).encoded]
            produce != null && !produce.suspended && produce.phase == CardPhase.Review
        }
    }

    /** Enqueued concept ids that could enter now: joined, produce-unscheduled, not locked. */
    fun enqueuedEligible(state: BoxState): List<String> = state.enqueued.filter { id ->
        val card = state.cards[id] ?: return@filter false
        state.scheduling[UnitKey.produce(id).encoded] == null && cardIntroducible(state, card)
    }

    /**
     * Candidate selection in unit keys. `conceptBudget` caps how many concepts may ENTER
     * the learning pool; units of concepts already in flight ride free (they do not grow
     * the pool). Enqueued produce units lead and bypass the health gate; with the gate
     * open, unlocked phrases enter next, then seed-order units (recognize backfill of
     * graduated material naturally leads new produce — lower seed index). `capacityUnits`
     * caps the total in units.
     */
    fun newCandidates(
        state: BoxState,
        conceptBudget: Int,
        gateOpen: Boolean,
        capacityUnits: Int,
    ): NewCandidates {
        var capacity = capacityUnits
        var conceptSlots = conceptBudget
        if (capacity <= 0) return NewCandidates.empty

        val inFlight = Inventory.conceptsInFlight(state).toMutableSet()
        val takenKeys = mutableSetOf<String>()
        val unlockedPhrases = mutableListOf<String>()
        val newUnits = mutableListOf<String>()

        // 1. Enqueued lead — within the concept throttle, bypassing the health gate.
        for (id in enqueuedEligible(state)) {
            if (capacity <= 0 || conceptSlots <= 0) break
            val key = UnitKey.produce(id).encoded
            if (key in takenKeys) continue
            newUnits += key
            takenKeys += key
            inFlight += id
            conceptSlots -= 1
            capacity -= 1
        }
        if (!gateOpen) return NewCandidates(unlockedPhrases, newUnits)

        val unscheduled = Inventory.joinedUnits(state).filter { state.scheduling[it.key] == null }

        // 2a. Unlock fast path: component phrases whose components all sit stable.
        for (unit in unscheduled) {
            if (capacity <= 0 || conceptSlots <= 0) break
            if (unit.role != Role.Produce || unit.card.kind != CardKind.Phrase) continue
            if (unit.card.components.isEmpty() || unit.key in takenKeys) continue
            if (!isPhraseUnlocked(state, unit.card)) continue
            unlockedPhrases += unit.key
            takenKeys += unit.key
            inFlight += unit.card.id
            conceptSlots -= 1
            capacity -= 1
        }

        // 2b. Automatic seed-order growth (locked phrases wait for their components).
        for (unit in unscheduled) {
            if (capacity <= 0) break
            if (unit.key in takenKeys || !isIntroducible(state, unit)) continue
            val ridesFree = unit.card.id in inFlight
            if (!ridesFree && conceptSlots <= 0) continue
            newUnits += unit.key
            takenKeys += unit.key
            if (!ridesFree) {
                inFlight += unit.card.id
                conceptSlots -= 1
            }
            capacity -= 1
        }
        return NewCandidates(unlockedPhrases, newUnits)
    }
}
