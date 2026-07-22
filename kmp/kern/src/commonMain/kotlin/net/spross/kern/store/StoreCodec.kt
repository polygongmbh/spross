package net.spross.kern.store

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import net.spross.kern.box.BoxState
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.DayStats
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Language
import net.spross.kern.model.UnitScheduling

/** A persisted box document could not be decoded (corruption or schema drift). */
class StoreFormatException(message: String) : Exception(message)

/**
 * The persisted aggregate as decoded from disk: [BoxState] minus the derived catalog
 * join. [source] is the profile's last-used source language; the app may re-join with
 * a different one (schedules are source-agnostic).
 */
data class DecodedBox(
    val target: Language,
    val source: Language,
    val config: BoxConfig,
    val scheduling: Map<String, UnitScheduling>,
    val enqueued: List<String>,
    val newIntroduced: Map<String, Int>,
    val dailyStats: Map<String, DayStats>,
) {
    /** Re-join hook: attach a fresh catalog join to obtain a live [BoxState]. */
    fun join(cards: List<Card>, joinStamp: JoinStamp): BoxState = BoxState(
        config = config,
        cards = cards.associateBy { it.id },
        joinStamp = joinStamp,
        scheduling = scheduling,
        enqueued = enqueued,
        newIntroduced = newIntroduced,
        dailyStats = dailyStats,
    )
}

/**
 * The narrow public store facade (contract §7): one JSON document per TARGET language.
 * Encoding is deterministic — sorted keys, ISO-8601 UTC dates — so identical states
 * produce identical bytes; decoding validates schema version, unit-key derivation,
 * and the phase/memory/due invariant.
 */
object StoreCodec {
    const val SCHEMA_VERSION: Int = 1

    fun encode(state: BoxState): String =
        StoreJson.encodeSorted(BoxDocument.serializer(), boxDocument(state))

    @Throws(StoreFormatException::class)
    fun decode(json: String): DecodedBox {
        val document = try {
            StoreJson.json.decodeFromString(BoxDocument.serializer(), json)
        } catch (e: IllegalArgumentException) {
            throw StoreFormatException("invalid box document: ${e.message}")
        }
        return document.toDecoded()
    }
}

/** Shared JSON flavor for the store document and both snapshot documents. */
internal object StoreJson {
    // why: omitted nulls keep documents compact and mirror v1's Swift Codable output.
    val json: Json = Json { explicitNulls = false }

    /** Deterministic bytes: every JSON object's keys are sorted before writing. */
    fun <T> encodeSorted(strategy: SerializationStrategy<T>, value: T): String =
        json.encodeToJsonElement(strategy, value).sortedKeys().toString()

    private fun JsonElement.sortedKeys(): JsonElement = when (this) {
        is JsonObject -> JsonObject(
            entries.sortedBy { it.key }.associate { (key, value) -> key to value.sortedKeys() },
        )
        is JsonArray -> JsonArray(map { it.sortedKeys() })
        else -> this
    }
}
