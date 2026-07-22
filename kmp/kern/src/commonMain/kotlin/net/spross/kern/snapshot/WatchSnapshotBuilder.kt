package net.spross.kern.snapshot

import kotlinx.serialization.Serializable
import net.spross.kern.box.BoxState
import net.spross.kern.box.Inventory
import net.spross.kern.model.CardPhase
import net.spross.kern.model.ExerciseUnit
import net.spross.kern.model.Role
import net.spross.kern.model.UnitScheduling
import net.spross.kern.store.StoreJson

/**
 * Phone-side builder of the watch application-context snapshot, v2 (contract §7):
 * entries are pre-resolved per UNIT so the watch stays pure Swift and never joins.
 * Capped and deduped by card — unit growth would cross the ~60 KB
 * `updateApplicationContext` limit sooner than v1's card list did.
 */
object WatchSnapshotBuilder {
    const val SCHEMA_VERSION: Int = 2
    const val ENTRY_CAP: Int = 60

    fun build(state: BoxState, nowEpochMillis: Long): String =
        StoreJson.encodeSorted(WatchSnapshotDoc.serializer(), doc(state, nowEpochMillis))

    internal fun doc(state: BoxState, nowEpochMillis: Long): WatchSnapshotDoc {
        val unitsByKey = Inventory.joinedUnits(state).associateBy { it.key }
        val ranked = Inventory.active(state).mapNotNull { sched ->
            val memory = sched.memory ?: return@mapNotNull null
            if (sched.due == null) return@mapNotNull null
            val unit = unitsByKey[sched.key] ?: return@mapNotNull null
            // Exposure tiers for scheduled units (§6); the watch never introduces,
            // so the enqueued-new and upcoming tiers are absent by design.
            val tier = when (sched.phase) {
                CardPhase.Relearning -> 0
                CardPhase.Learning -> 2
                else -> 3
            }
            Ranked(tier, memory.stability, sched, unit)
        }
        val seenCards = mutableSetOf<String>()
        val entries = ranked
            .sortedWith(compareBy({ it.tier }, { it.order }, { it.sched.key }))
            .filter { seenCards.add(it.sched.cardId) }
            .take(ENTRY_CAP)
            .map { entry(it.sched, it.unit) }
        return WatchSnapshotDoc(
            schemaVersion = SCHEMA_VERSION,
            generated = nowEpochMillis,
            entries = entries,
        )
    }

    private data class Ranked(
        val tier: Int,
        val order: Double,
        val sched: UnitScheduling,
        val unit: ExerciseUnit,
    )

    private fun entry(sched: UnitScheduling, unit: ExerciseUnit): WatchEntryDto {
        val card = unit.card
        val due = sched.due!!.toEpochMilliseconds()
        val stability = sched.memory!!.stability
        return when (unit.role) {
            Role.Produce -> WatchEntryDto(
                unitKey = sched.key,
                prompt = decoratedSourceText(card),
                answer = card.target.text,
                accepted = listOf(card.target.text) + card.target.synonyms + card.target.variants,
                // why: v1's hide-after-learning rule — once past Learning the emoji
                // would give the answer away.
                emoji = card.emoji.takeIf { sched.phase == CardPhase.Learning },
                articleTint = articleTint(card),
                due = due,
                stability = stability,
            )
            Role.Recognize -> WatchEntryDto(
                unitKey = sched.key,
                prompt = unit.form!!,
                // The reveal shows the source side; ♀ decorates it (contract §3).
                answer = decoratedSourceText(card),
                accepted = emptyList(), // reveal + self-grade: nothing is typed
                emoji = null, // never on recognize prompts: it depicts the answer
                articleTint = articleTint(card),
                due = due,
                stability = stability,
            )
        }
    }
}

/** Watch document; dates are epoch millis for trivial Swift decoding. */
@Serializable
internal data class WatchSnapshotDoc(
    val schemaVersion: Int,
    val generated: Long,
    val entries: List<WatchEntryDto>,
)

/**
 * One drainable unit, fully pre-resolved: [prompt] is shown, [answer] revealed,
 * [accepted] grades typed produce input (empty = self-graded recognize).
 */
@Serializable
internal data class WatchEntryDto(
    val unitKey: String,
    val prompt: String,
    val answer: String,
    val accepted: List<String>,
    val emoji: String? = null,
    val articleTint: String? = null,
    val due: Long,
    val stability: Double,
)
