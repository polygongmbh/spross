package net.spross.kern.store

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A persisted box could not be decoded (corruption or schema drift). */
class StoreFormatException(message: String) : Exception(message)

/** A box off disk, and whether reading it converted a v1 document ([StoreCodec.load]). */
data class LoadedBox(val box: StoredBox, val converted: Boolean)

/**
 * The narrow public store facade (`kern/docs/snapshots.md`): ONE document per TARGET
 * language, since only one is ever active and a save should touch only what moved.
 *
 * Encoding is deterministic — sorted keys, seconds for every timestamp — so identical boxes
 * produce identical bytes. Decoding replays each card's log and refuses the whole file over
 * any single card it cannot read, because a box that lands half is worse than one that does
 * not land.
 */
object StoreCodec {
    const val SCHEMA_VERSION: Int = STORE_SCHEMA_VERSION

    fun encode(box: StoredBox): String = encodeBoxFile(box)

    @Throws(StoreFormatException::class)
    fun decode(json: String): StoredBox = decodeBoxFile(json)

    /**
     * One language's box as a launch should hold it: a v2 document as written, a v1 one
     * converted on the spot ([LegacyStore]). The file keeps its name either way, so a
     * conversion is a rewrite and never a move — the caller writes back what it is told
     * was converted, and an interrupted migration simply runs again.
     */
    @Throws(StoreFormatException::class)
    fun load(json: String): LoadedBox = when (versionOf(json)) {
        STORE_SCHEMA_VERSION -> LoadedBox(decode(json), converted = false)
        LEGACY_SCHEMA_VERSION -> LoadedBox(LegacyStore.convert(json), converted = true)
        else -> throw StoreFormatException("unsupported schemaVersion ${versionOf(json)}")
    }
}

/** Shared JSON flavor for the store document and both snapshot documents. */
internal object StoreJson {
    // why: omitted nulls keep documents compact.
    // why: a key this build no longer knows is dropped rather than failing the whole
    // document — defaulting a renamed key only covers its ABSENCE, so without this a
    // box written before the rename still carries the old key and refuses to load.
    val json: Json = Json { explicitNulls = false; ignoreUnknownKeys = true }

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
