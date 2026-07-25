package net.spross.kern.snapshot

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import net.spross.kern.box.BoxState
import net.spross.kern.box.Inventory
import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.emojiVisible
import net.spross.kern.model.presentationRole
import net.spross.kern.model.recognitionPromptForm
import net.spross.kern.store.StoreJson

/**
 * Phone-side builder of the watch application-context snapshot, v2:
 * one entry per CARD with BOTH sides pre-resolved, so the watch stays pure
 * Swift and never joins. [WatchEntryDto.nextRole]/[WatchEntryDto.promptForm]
 * are resolved from the log count at build time; the watch presents
 * accordingly (flip + self-grade both roles; the watch never types).
 * Capped at [ENTRY_CAP] to stay under the ~60 KB `updateApplicationContext`
 * limit.
 */
object WatchSnapshotBuilder {
    const val SCHEMA_VERSION: Int = 2
    const val ENTRY_CAP: Int = 60

    fun build(state: BoxState, nowEpochMillis: Long): String =
        StoreJson.encodeSorted(WatchSnapshotDoc.serializer(), doc(state, nowEpochMillis))

    internal fun doc(state: BoxState, nowEpochMillis: Long): WatchSnapshotDoc {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        val ranked = Inventory.active(state).mapNotNull { sched ->
            val memory = sched.memory ?: return@mapNotNull null
            val due = sched.due ?: return@mapNotNull null
            // Exposure tiers for scheduled cards; the watch never introduces,
            // so the enqueued-new and upcoming tiers are absent by design.
            val tier = when (sched.phase) {
                CardPhase.Relearning -> 0
                CardPhase.Learning -> 2
                else -> 3
            }
            Ranked(due <= now, tier, memory.stability, sched)
        }
        // why: due-first ranking — every currently-due card outranks all non-due
        // ones, so the watch never under-reports due cards within the cap.
        val entries = ranked
            .sortedWith(compareBy({ !it.isDue }, { it.tier }, { it.order }, { it.sched.cardId }))
            .take(ENTRY_CAP)
            .map { entry(it.sched, state.cards.getValue(it.sched.cardId)) }
        return WatchSnapshotDoc(
            schemaVersion = SCHEMA_VERSION,
            generated = nowEpochMillis,
            entries = entries,
        )
    }

    private data class Ranked(
        val isDue: Boolean,
        val tier: Int,
        val order: Double,
        val sched: CardScheduling,
    )

    private fun entry(sched: CardScheduling, card: Card): WatchEntryDto {
        val reviewCount = sched.reviewCount
        val nextRole = presentationRole(card.id, reviewCount)
        return WatchEntryDto(
            cardId = card.id,
            sourceText = card.source.text,
            targetText = card.target.text,
            accepted = listOf(card.target.text) + card.target.synonyms + card.target.variants,
            emoji = card.emoji?.takeIf { emojiVisible(nextRole, sched.phase, reviewCount) },
            articleTint = articleTint(card),
            femMarker = card.promptFeminineMarker,
            due = sched.due!!.toEpochMilliseconds(),
            stability = sched.memory!!.stability,
            nextRole = when (nextRole) {
                PresentationRole.Produce -> "produce"
                PresentationRole.Recognize -> "recognize"
            },
            promptForm = recognitionPromptForm(card, reviewCount),
        )
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
 * One drainable card with both sides. [nextRole] "produce": prompt [sourceText]
 * (+ labeled ♀ badge when [femMarker]), reveal the target family. "recognize":
 * prompt [promptForm] (the rotated target form), reveal [sourceText] decorated.
 * [emoji] is pre-gated by the emoji policy; [accepted] lists the full target
 * family for reveal display (the watch never types).
 */
@Serializable
internal data class WatchEntryDto(
    val cardId: String,
    val sourceText: String,
    val targetText: String,
    val accepted: List<String>,
    val emoji: String? = null,
    val articleTint: String? = null,
    val femMarker: Boolean,
    val due: Long,
    val stability: Double,
    val nextRole: String,
    val promptForm: String,
)
