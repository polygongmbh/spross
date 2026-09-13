package net.spross.kern.store

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import net.spross.kern.box.BoxState
import net.spross.kern.box.DayTally
import net.spross.kern.box.OwnWord
import net.spross.kern.box.OwnWords
import net.spross.kern.box.ReportedIssue
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.CardKind
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.MemoryState
import net.spross.kern.model.Rating
import net.spross.kern.model.ReviewLogEntry

/**
 * Persisted aggregate for one TARGET language (`box-<target>.json`), schema version 1.
 * Scheduling is keyed by CARD ID — one schedule per card.
 * Every serializable type in this file stays internal — the public door is [StoreCodec];
 * a public @Serializable class would flood the ObjC header with serialization internals.
 */
/** The v1 store's schema; this file exists only to leave it behind. */
internal const val LEGACY_SCHEMA_VERSION: Int = 1

/** A v1 box as it was written — only the parts [LegacyStore] carries over. */
internal data class DecodedBox(
    val scheduling: Map<String, CardScheduling>,
    val enqueued: List<String>,
    val ownWords: List<OwnWord>,
    val reportedIssues: Map<String, ReportedIssue>,
    val lastExportAt: Instant?,
)

/** Read one `box-<target>.json` as the build that wrote it meant it. */
@Throws(StoreFormatException::class)
internal fun decodeLegacyBox(json: String): DecodedBox {
    val document = try {
        StoreJson.json.decodeFromString(BoxDocument.serializer(), json)
    } catch (e: IllegalArgumentException) {
        throw StoreFormatException("invalid box document: ${e.message}")
    }
    return document.toDecoded()
}

@Serializable
internal data class BoxDocument(
    val schemaVersion: Int,
    val target: String,
    val source: String,
    val scheduling: Map<String, CardDto>,
    val enqueued: List<String>,
    // why: defaulted like the counters above — a document written before the learner
    // could author words at all decodes as one who has authored none.
    val ownWords: List<OwnWordDto> = emptyList(),
    // why: defaulted for the same reason — a document written before reporting existed
    // decodes as a learner who has filed nothing and exported nothing.
    val reportedIssues: List<ReportedIssueDto> = emptyList(),
    @Serializable(with = IsoInstantSerializer::class) val lastExportAt: Instant? = null,
)

/** A content problem the learner filed; see `ReportedIssue`. */
@Serializable
internal data class ReportedIssueDto(
    val cardId: String,
    val comment: String? = null,
    val learnerInput: String? = null,
    @Serializable(with = IsoInstantSerializer::class) val reportedAt: Instant,
)

/**
 * A word the learner wrote. The ONE piece of content the box document owns: every
 * other card in it is a derivation of the catalog and is re-derived on load, so this
 * is the only entry whose loss would lose a word rather than a computation.
 */
@Serializable
internal data class OwnWordDto(
    val id: String,
    val kind: String,
    val emoji: String? = null,
    /** language → the word in it, exactly as the catalog keys a concept's realizations. */
    val texts: Map<String, String>,
    // why: defaulted — a word written before the form could carry one decodes as a word
    // with nothing said about it, which is what it was.
    val comment: String? = null,
    // why: defaulted — a word written before the box recorded this reads as old, which
    // is what an export filter should conclude about it anyway.
    @Serializable(with = IsoInstantSerializer::class) val addedAt: Instant? = null,
)

@Serializable
internal data class CardDto(
    val cardId: String,
    val phase: String,
    val stepIndex: Int? = null,
    val memory: MemoryDto? = null,
    @Serializable(with = IsoInstantSerializer::class) val due: Instant? = null,
    val lapses: Int,
    val suspended: Boolean,
    val log: List<LogEntryDto>,
)

@Serializable
internal data class MemoryDto(val stability: Double, val difficulty: Double)

@Serializable
internal data class LogEntryDto(
    @Serializable(with = IsoInstantSerializer::class) val date: Instant,
    val rating: Int,
)

// Decoding (document → validated aggregate)

private fun fail(message: String): Nothing = throw StoreFormatException(message)

internal fun BoxDocument.toDecoded(): DecodedBox {
    if (schemaVersion != LEGACY_SCHEMA_VERSION) {
        fail("unsupported schemaVersion $schemaVersion (expected $LEGACY_SCHEMA_VERSION)")
    }
    if (target.isBlank() || source.isBlank() || target == source) {
        fail("invalid profile: source=\"$source\" target=\"$target\"")
    }
    return DecodedBox(
        scheduling = scheduling.entries.associate { (key, dto) -> key to dto.toDomain(key) },
        enqueued = enqueued,
        ownWords = ownWords.map { it.toDomain() },
        reportedIssues = reportedIssues.associate { it.cardId to it.toDomain() },
        lastExportAt = lastExportAt,
    )
}

private fun ReportedIssueDto.toDomain(): ReportedIssue {
    if (cardId.isEmpty()) fail("reported issue carries no card id")
    return ReportedIssue(
        cardId = cardId,
        comment = comment,
        learnerInput = learnerInput,
        reportedAt = reportedAt,
    )
}

private fun OwnWordDto.toDomain(): OwnWord {
    if (!OwnWords.owns(id)) fail("own word \"$id\" does not carry the ${OwnWords.ID_PREFIX} prefix")
    // why: a bare remark is the one entry that legitimately has no text — it was never
    // meant to become a card. Anything with neither text nor comment says nothing at all.
    if (texts.isEmpty() && comment.isNullOrBlank()) {
        fail("own word \"$id\" carries neither a text in any language nor a comment")
    }
    val parsedKind = when (kind) {
        "noun" -> CardKind.Noun
        "verb" -> CardKind.Verb
        "adjective" -> CardKind.Adjective
        "phrase" -> CardKind.Phrase
        "idiom" -> CardKind.Idiom
        else -> fail("own word \"$id\": unknown kind \"$kind\"")
    }
    return OwnWord(
        id = id,
        kind = parsedKind,
        emoji = emoji,
        texts = texts,
        comment = comment,
        addedAt = addedAt ?: Instant.DISTANT_PAST,
    )
}

private fun CardDto.toDomain(key: String): CardScheduling {
    if (cardId != key) {
        fail("scheduling key \"$key\" does not match its entry (\"$cardId\")")
    }
    val parsedPhase = when (phase) {
        "new" -> CardPhase.New
        "learning" -> CardPhase.Learning
        "review" -> CardPhase.Review
        "relearning" -> CardPhase.Relearning
        else -> fail("scheduling entry $key: unknown phase \"$phase\"")
    }
    val isNew = parsedPhase == CardPhase.New
    if (isNew != (memory == null) || isNew != (due == null)) {
        fail("scheduling entry $key violates the phase/memory/due invariant")
    }
    return try {
        CardScheduling(
            cardId = cardId,
            phase = parsedPhase,
            stepIndex = stepIndex,
            memory = memory?.let { MemoryState(stability = it.stability, difficulty = it.difficulty) },
            due = due,
            lapses = lapses,
            suspended = suspended,
            log = log.map { entry ->
                ReviewLogEntry(
                    date = entry.date,
                    rating = Rating.entries.firstOrNull { it.value == entry.rating }
                        ?: fail("scheduling entry $key: unknown rating ${entry.rating}"),
                )
            },
        )
    } catch (e: IllegalArgumentException) {
        fail("scheduling entry $key: ${e.message}")
    }
}
