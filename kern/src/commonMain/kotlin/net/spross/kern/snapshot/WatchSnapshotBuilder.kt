package net.spross.kern.snapshot

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import net.spross.kern.box.BoxState
import net.spross.kern.box.Inventory
import net.spross.kern.box.Statistics
import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.EmojiPlacement
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.emojiPlacement
import net.spross.kern.model.presentationRole
import net.spross.kern.model.recognitionPromptForm
import net.spross.kern.session.MultipleChoice
import net.spross.kern.store.StoreJson

/**
 * Phone-side builder of the watch application-context snapshot, v3:
 * one entry per CARD with BOTH sides pre-resolved, so the watch stays pure
 * Swift and never joins. [WatchEntryDto.nextRole]/[WatchEntryDto.promptForm]
 * are resolved from the log count at build time; the watch presents
 * accordingly (flip + self-grade both roles; the watch never types).
 * [WatchEntryDto.distractors] ships the multiple-choice tiles already ranked
 * and already on the entry's own option side, so the watch only shuffles.
 * Capped at [ENTRY_CAP] to stay under the ~60 KB `updateApplicationContext`
 * limit.
 */
object WatchSnapshotBuilder {
    const val SCHEMA_VERSION: Int = 3
    const val ENTRY_CAP: Int = 60
    private const val RECOGNIZE = "recognize"

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
            .map { entry(it.sched, state.cards.getValue(it.sched.cardId), Statistics.isSettled(state, it.sched)) }
        return WatchSnapshotDoc(
            schemaVersion = SCHEMA_VERSION,
            generated = nowEpochMillis,
            entries = entries.map { it.copy(distractors = distractors(it, entries)) },
        )
    }

    private data class Ranked(
        val isDue: Boolean,
        val tier: Int,
        val order: Double,
        val sched: CardScheduling,
    )

    /**
     * The multiple-choice tiles for [entry], drawn from the other snapshot
     * entries read on ENTRY's option side — never on their own, or the watch
     * would offer source meanings and target words in the same question.
     */
    private fun distractors(entry: WatchEntryDto, pool: List<WatchEntryDto>): List<String> =
        MultipleChoice.distractors(
            answer = optionText(entry, entry.nextRole),
            candidates = pool.filter { it.cardId != entry.cardId }.map { optionText(it, entry.nextRole) },
        )

    /** [dto]'s text on the side a question in [role] asks the learner to pick. */
    private fun optionText(dto: WatchEntryDto, role: String): String =
        if (role == RECOGNIZE) dto.sourceText else dto.targetText

    private fun entry(sched: CardScheduling, card: Card, settled: Boolean): WatchEntryDto {
        val reviewCount = sched.reviewCount
        val nextRole = presentationRole(card.id, reviewCount)
        return WatchEntryDto(
            cardId = card.id,
            sourceText = card.source.text,
            targetText = card.target.text,
            accepted = listOf(card.target.text) + card.target.synonyms + card.target.variants,
            // why: the watch quiz has no reveal face to hang a picture on, so it only
            // ever carries the prompt-side emoji.
            emoji = card.emoji?.takeIf {
                emojiPlacement(nextRole, settled, reviewCount) == EmojiPlacement.Prompt
            },
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
 * family for reveal display (the watch never types). [distractors] are the
 * ranked wrong options for THIS entry's role — the watch picks three and
 * shuffles them with the answer.
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
    val distractors: List<String> = emptyList(),
)
