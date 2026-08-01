package net.spross.kern.snapshot

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import net.spross.kern.box.BoxState
import net.spross.kern.box.Inventory
import net.spross.kern.box.Statistics
import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.EmojiCue
import net.spross.kern.model.Language
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.emojiCue
import net.spross.kern.model.presentationRole
import net.spross.kern.model.recognitionPromptForm
import net.spross.kern.session.MultipleChoice
import net.spross.kern.store.StoreJson

/**
 * Phone-side builder of the watch application-context snapshot, v4:
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
    const val SCHEMA_VERSION: Int = 4
    const val ENTRY_CAP: Int = 60
    private const val RECOGNIZE = "recognize"

    fun build(
        state: BoxState,
        nowEpochMillis: Long,
        citationPrefixes: Map<Language, List<String>> = emptyMap(),
    ): String =
        StoreJson.encodeSorted(
            WatchSnapshotDoc.serializer(),
            doc(state, nowEpochMillis, citationPrefixes),
        )

    /**
     * [citationPrefixes] are `languages.json`'s `optionalVerbPrefixes` per language —
     * only [MultipleChoice.optionForm] reads them, and an empty map simply leaves every
     * verb in its citation form.
     */
    internal fun doc(
        state: BoxState,
        nowEpochMillis: Long,
        citationPrefixes: Map<Language, List<String>> = emptyMap(),
    ): WatchSnapshotDoc {
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
        // why: options are drawn from every card the learner has met, not just the
        // capped entries — the cap is a wire budget, and a pool that small leaves a
        // question no same-class company to keep. Unscheduled cards stay out: a word
        // first met as somebody else's wrong answer is no longer new when it arrives.
        val pool = ranked.map { state.cards.getValue(it.sched.cardId) }
        return WatchSnapshotDoc(
            schemaVersion = SCHEMA_VERSION,
            generated = nowEpochMillis,
            entries = entries.map { offer(it, state, pool, citationPrefixes) },
        )
    }

    private data class Ranked(
        val isDue: Boolean,
        val tier: Int,
        val order: Double,
        val sched: CardScheduling,
    )

    /**
     * [entry] with its multiple-choice options resolved: the wrong ones ranked out
     * of [pool], and its own [WatchEntryDto.optionForm] whenever the form it is
     * offered in differs from the form it is taught in. Every option is read on
     * ENTRY's side — never on its own, or the watch would offer source meanings
     * and target words in the same question.
     */
    private fun offer(
        entry: WatchEntryDto,
        state: BoxState,
        pool: List<Card>,
        citationPrefixes: Map<Language, List<String>>,
    ): WatchEntryDto {
        val role = entry.nextRole
        val answer = option(state.cards.getValue(entry.cardId), role, citationPrefixes)
        return entry.copy(
            optionForm = answer.text.takeIf { it != sideText(entry, role) },
            distractors = MultipleChoice.distractors(
                answer = answer,
                candidates = pool.filter { it.id != entry.cardId }.map { option(it, role, citationPrefixes) },
            ),
        )
    }

    /** [card] as it can be offered for a question in [role]. */
    private fun option(
        card: Card,
        role: String,
        citationPrefixes: Map<Language, List<String>>,
    ): MultipleChoice.Option {
        val side = if (role == RECOGNIZE) card.source else card.target
        return MultipleChoice.Option(
            text = MultipleChoice.optionForm(side.text, card.kind, citationPrefixes[side.lang].orEmpty()),
            kind = card.kind,
            area = card.area,
        )
    }

    /** [dto]'s taught text on the side a question in [role] asks the learner to pick. */
    private fun sideText(dto: WatchEntryDto, role: String): String =
        if (role == RECOGNIZE) dto.sourceText else dto.targetText

    private fun entry(sched: CardScheduling, card: Card, settled: Boolean): WatchEntryDto {
        val reviewCount = sched.reviewCount
        val nextRole = presentationRole(card.id, reviewCount)
        return WatchEntryDto(
            cardId = card.id,
            sourceText = card.source.text,
            targetText = card.target.text,
            // why: the snapshot is answered in one shot, so a picture held back for a
            // reveal has no honest moment to appear and could only leak the answer —
            // it is withheld from the wire rather than trusted to the reader.
            emoji = card.emoji?.takeIf {
                emojiCue(nextRole, settled, reviewCount) == EmojiCue.Upfront
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
 * [emoji] is pre-gated by the emoji policy. [distractors] are the
 * ranked wrong options for THIS entry's role — the watch picks three and
 * shuffles them with the answer, which it reads off [optionForm].
 */
@Serializable
internal data class WatchEntryDto(
    val cardId: String,
    val sourceText: String,
    val targetText: String,
    val emoji: String? = null,
    val articleTint: String? = null,
    val femMarker: Boolean,
    val due: Long,
    val stability: Double,
    val nextRole: String,
    val promptForm: String,
    val distractors: List<String> = emptyList(),
    /**
     * This entry's own option, when it is offered in a different form than it is
     * taught in ([MultipleChoice.optionForm]) — absent whenever the two agree, which
     * is every card but a bound stem and a verb. The reveal keeps the taught form.
     */
    val optionForm: String? = null,
)
