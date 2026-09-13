package net.spross.kern.store

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import net.spross.kern.box.DayTally
import net.spross.kern.box.OwnWord
import net.spross.kern.box.OwnWords
import net.spross.kern.box.ReportedIssue
import net.spross.kern.box.fsrsParameters
import net.spross.kern.box.replayed
import net.spross.kern.fsrs.FsrsScheduler
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.CardKind
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.Language
import net.spross.kern.model.Rating
import net.spross.kern.model.ReviewLogEntry

/** The store's schema — the same number on a language's file and on an export. */
internal const val STORE_SCHEMA_VERSION: Int = 2

/**
 * One language's stored box (`box-<target>.json`, `kern/docs/snapshots.md`), and the
 * envelope an export wraps every language in.
 *
 * A card is `[dueEpochSeconds, [[answerEpochSeconds, rating], …]]` and nothing else: memory,
 * phase, step and lapses are replayed from that log on the way in, so the file carries only
 * what a replay cannot give back. Timestamps are epoch seconds, which no timezone touches.
 */
@Serializable
internal data class StoredBoxDto(
    /** cardId → `[due, [[date, rating], …]]`, all seconds. */
    val cards: Map<String, JsonArray> = emptyMap(),
    /** Out of rotation. A word suspended before it was ever answered is only an id here. */
    val suspended: List<String> = emptyList(),
    val enqueued: List<String> = emptyList(),
    val ownWords: List<StoredOwnWordDto> = emptyList(),
    val reportedIssues: List<StoredReportDto> = emptyList(),
    val lastExportAt: Long? = null,
    /** The crossings booked on ONE day; local to the device, so an export omits it. */
    val today: Map<String, Int> = emptyMap(),
)

/** Every language in one document — what an export is, and nothing else. */
@Serializable
internal data class StoreDocument(
    val schemaVersion: Int,
    val boxes: Map<String, StoredBoxDto> = emptyMap(),
)

@Serializable
internal data class StoredOwnWordDto(
    val id: String,
    val kind: String,
    val emoji: String? = null,
    /** language → the word in it, exactly as the catalog keys a concept's realizations. */
    val texts: Map<String, String> = emptyMap(),
    val comment: String? = null,
    val addedAt: Long? = null,
)

@Serializable
internal data class StoredReportDto(
    val cardId: String,
    val comment: String? = null,
    val learnerInput: String? = null,
    val reportedAt: Long,
)

// Encoding

/**
 * One language's file: the box's own fields with [STORE_SCHEMA_VERSION] beside them, rather
 * than a version nested under a wrapper nothing else would carry.
 */
internal fun encodeBoxFile(box: StoredBox): String {
    val payload = StoreJson.json
        .encodeToJsonElement(StoredBoxDto.serializer(), storedBoxDto(box)).asMap()
    return StoreJson.encodeSorted(
        JsonElement.serializer(),
        JsonObject(payload + ("schemaVersion" to JsonPrimitive(STORE_SCHEMA_VERSION))),
    )
}

internal fun encodeStore(boxes: StoredBoxes): String = StoreJson.encodeSorted(
    StoreDocument.serializer(),
    StoreDocument(
        schemaVersion = STORE_SCHEMA_VERSION,
        boxes = boxes.boxes.mapValues { (_, box) -> storedBoxDto(box) },
    ),
)

private fun storedBoxDto(box: StoredBox): StoredBoxDto = StoredBoxDto(
    // why: a schedule with no answers is a husk carrying nothing but its suspension —
    // it says all it has to say in `suspended`.
    cards = box.scheduling.values.filter { it.log.isNotEmpty() }
        .associate { it.cardId to cardEntry(it) },
    suspended = box.scheduling.values.filter { it.suspended }.map { it.cardId }.sorted(),
    enqueued = box.enqueued,
    ownWords = box.ownWords.map { storedOwnWordDto(it) },
    reportedIssues = box.reportedIssues.values.sortedBy { it.cardId }.map {
        StoredReportDto(it.cardId, it.comment, it.learnerInput, it.reportedAt.epochSeconds)
    },
    lastExportAt = box.lastExportAt?.epochSeconds,
    today = box.consolidatedToday?.let { mapOf(it.day to it.count) } ?: emptyMap(),
)

private fun cardEntry(sched: CardScheduling): JsonArray = JsonArray(
    listOf(
        JsonPrimitive(sched.due?.epochSeconds ?: 0L),
        JsonArray(
            sched.log.map { JsonArray(listOf(JsonPrimitive(it.date.epochSeconds), JsonPrimitive(it.rating.value))) },
        ),
    ),
)

private fun storedOwnWordDto(word: OwnWord): StoredOwnWordDto = StoredOwnWordDto(
    id = word.id,
    kind = storedKindName(word.kind),
    emoji = word.emoji,
    texts = word.texts,
    comment = word.comment,
    addedAt = word.addedAt.takeIf { it != Instant.DISTANT_PAST }?.epochSeconds,
)

// Decoding

/** A document this build cannot make sense of. One bad box refuses the whole file. */
internal fun storeFail(message: String): Nothing = throw StoreFormatException(message)

private val targetCode = Regex("[a-z]{2,3}")

/** One language's file, version checked. */
internal fun decodeBoxFile(json: String): StoredBox {
    val obj = parseObject(json, "store")
    val version = (obj["schemaVersion"] as? JsonPrimitive)?.intOrNull
    if (version != STORE_SCHEMA_VERSION) {
        storeFail("unsupported schemaVersion $version (expected $STORE_SCHEMA_VERSION)")
    }
    return dto(obj, "store").toStored("store")
}

/** An export: every language it carries, each one checked. */
internal fun decodeStore(json: String): StoredBoxes {
    val obj = parseObject(json, "export")
    val version = (obj["schemaVersion"] as? JsonPrimitive)?.intOrNull
    if (version != STORE_SCHEMA_VERSION) {
        storeFail("unsupported schemaVersion $version (expected $STORE_SCHEMA_VERSION)")
    }
    val boxes = obj["boxes"] as? JsonObject ?: storeFail("export carries no boxes")
    return StoredBoxes(
        boxes.entries.associate { (target, element) ->
            if (!targetCode.matches(target)) storeFail("invalid target \"$target\"")
            val box = element as? JsonObject ?: storeFail("box $target is not a JSON object")
            target to dto(box, target).toStored(target)
        },
    )
}

private fun parseObject(json: String, where: String): JsonObject = try {
    StoreJson.json.parseToJsonElement(json) as? JsonObject
        ?: storeFail("$where is not a JSON object")
} catch (e: IllegalArgumentException) {
    storeFail("$where is not JSON: ${e.message}")
}

private fun dto(obj: JsonObject, where: String): StoredBoxDto = try {
    StoreJson.json.decodeFromJsonElement(StoredBoxDto.serializer(), obj)
} catch (e: IllegalArgumentException) {
    storeFail("$where: ${e.message}")
}

private fun JsonElement.asMap(): Map<String, JsonElement> =
    (this as? JsonObject)?.toMap() ?: emptyMap()

/** The schema a document declares, or null where it declares none. */
internal fun versionOf(json: String): Int? =
    (parseObject(json, "store")["schemaVersion"] as? JsonPrimitive)?.intOrNull

private fun StoredBoxDto.toStored(target: Language): StoredBox {
    val scheduler = FsrsScheduler(BoxConfig.product().fsrsParameters())
    val suspendedIds = suspended.toSet()
    val answered = cards.entries.associate { (cardId, entry) ->
        cardId to entry.toScheduling(cardId, target, cardId in suspendedIds, scheduler)
    }
    // A suspension with no answers behind it: the husk the box holds it as.
    val husks = suspendedIds.filter { it !in cards }.associateWith { id ->
        try {
            CardScheduling(cardId = id, suspended = true)
        } catch (e: IllegalArgumentException) {
            storeFail("$target: suspended id \"$id\" is not a card id (${e.message})")
        }
    }
    return StoredBox(
        scheduling = answered + husks,
        enqueued = enqueued,
        ownWords = ownWords.map { it.toDomain(target) },
        reportedIssues = reportedIssues.associate { it.cardId to it.toDomain(target) },
        lastExportAt = lastExportAt?.let { Instant.fromEpochSeconds(it) },
        consolidatedToday = today.entries.firstOrNull()?.let { DayTally(it.key, it.value) },
    )
}

private fun JsonArray.toScheduling(
    cardId: String,
    target: String,
    suspended: Boolean,
    scheduler: FsrsScheduler,
): CardScheduling {
    val where = "$target, card \"$cardId\""
    if (size != 2) storeFail("$where: expected [due, log], got $size entries")
    val due = Instant.fromEpochSeconds(seconds(this[0], where))
    val entries = (this[1] as? JsonArray ?: storeFail("$where: the log is not an array"))
        .map { it.toLogEntry(where) }
    if (entries.isEmpty()) storeFail("$where: a stored card carries at least one answer")
    return try {
        replayed(cardId, due, entries, suspended, scheduler)
    } catch (e: IllegalArgumentException) {
        storeFail("$where: ${e.message}")
    }
}

private fun JsonElement.toLogEntry(where: String): ReviewLogEntry {
    val pair = this as? JsonArray ?: storeFail("$where: a log entry is not an array")
    if (pair.size != 2) storeFail("$where: expected [date, rating], got ${pair.size} entries")
    val rating = seconds(pair[1], where).toInt()
    return ReviewLogEntry(
        date = Instant.fromEpochSeconds(seconds(pair[0], where)),
        rating = Rating.entries.firstOrNull { it.value == rating }
            ?: storeFail("$where: unknown rating $rating"),
    )
}

private fun seconds(element: JsonElement, where: String): Long =
    (element as? JsonPrimitive)?.content?.toLongOrNull()
        ?: storeFail("$where: \"$element\" is not a whole number")

private fun StoredOwnWordDto.toDomain(target: String): OwnWord {
    if (!OwnWords.owns(id)) {
        storeFail("$target: own word \"$id\" does not carry the ${OwnWords.ID_PREFIX} prefix")
    }
    // why: a bare remark is the one entry that legitimately has no text — it was never
    // meant to become a card. Anything with neither text nor comment says nothing at all.
    if (texts.isEmpty() && comment.isNullOrBlank()) {
        storeFail("$target: own word \"$id\" carries neither a text nor a comment")
    }
    return OwnWord(
        id = id,
        kind = storedKind(kind, id, target),
        emoji = emoji,
        texts = texts,
        comment = comment,
        addedAt = addedAt?.let { Instant.fromEpochSeconds(it) } ?: Instant.DISTANT_PAST,
    )
}

private fun StoredReportDto.toDomain(target: String): ReportedIssue {
    if (cardId.isEmpty()) storeFail("$target: a reported issue carries no card id")
    return ReportedIssue(
        cardId = cardId,
        comment = comment,
        learnerInput = learnerInput,
        reportedAt = Instant.fromEpochSeconds(reportedAt),
    )
}

internal fun storedKindName(kind: CardKind): String = when (kind) {
    CardKind.Noun -> "noun"
    CardKind.Verb -> "verb"
    CardKind.Adjective -> "adjective"
    CardKind.Phrase -> "phrase"
    CardKind.Idiom -> "idiom"
}

private fun storedKind(name: String, id: String, target: String): CardKind = when (name) {
    "noun" -> CardKind.Noun
    "verb" -> CardKind.Verb
    "adjective" -> CardKind.Adjective
    "phrase" -> CardKind.Phrase
    "idiom" -> CardKind.Idiom
    else -> storeFail("$target: own word \"$id\" has unknown kind \"$name\"")
}
