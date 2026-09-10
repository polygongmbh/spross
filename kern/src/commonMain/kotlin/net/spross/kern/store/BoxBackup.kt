package net.spross.kern.store

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import net.spross.kern.model.Language

/**
 * One file carrying every target's box document, for moving a learner's boxes to a fresh
 * install or another platform (`kern/docs/snapshots.md`).
 *
 * Restoring replaces each box the file carries and leaves every target it does not carry
 * alone. A file with even one unreadable box is refused whole, so a restore never lands half.
 */
object BoxBackup {
    const val FORMAT: String = "spross-box-backup"
    const val VERSION: Int = 1

    private val targetCode = Regex("[a-z]{2,3}")

    /** [documents] maps each target to its stored `box-<target>.json` text. */
    @Throws(StoreFormatException::class)
    fun encode(documents: Map<Language, String>): String {
        val boxes = documents.mapValues { (target, json) -> parse(json, "box $target") }
        val backup = JsonObject(
            mapOf(
                "format" to JsonPrimitive(FORMAT),
                "version" to JsonPrimitive(VERSION),
                "boxes" to JsonObject(boxes),
            ),
        )
        return StoreJson.encodeSorted(JsonElement.serializer(), backup)
    }

    /** Target → box document text, every one already proven to decode as that target's box. */
    @Throws(StoreFormatException::class)
    fun decode(json: String): Map<Language, String> {
        val backup = parse(json, "backup") as? JsonObject
            ?: throw StoreFormatException("backup is not a JSON object")
        if ((backup["format"] as? JsonPrimitive)?.content != FORMAT) {
            throw StoreFormatException("not a Spross box backup")
        }
        val version = (backup["version"] as? JsonPrimitive)?.intOrNull
        if (version != VERSION) throw StoreFormatException("unsupported backup version $version")
        val boxes = backup["boxes"] as? JsonObject
            ?: throw StoreFormatException("backup carries no boxes")
        return boxes.mapValues { (target, element) ->
            if (!targetCode.matches(target)) throw StoreFormatException("invalid target '$target'")
            if (element !is JsonObject) throw StoreFormatException("box $target is not a JSON object")
            val document = element.toString()
            val decoded = StoreCodec.decode(document)
            if (decoded.target != target) {
                throw StoreFormatException("box filed under $target holds ${decoded.target}")
            }
            document
        }
    }

    private fun parse(json: String, what: String): JsonElement = try {
        StoreJson.json.parseToJsonElement(json)
    } catch (e: SerializationException) {
        throw StoreFormatException("$what is not JSON: ${e.message}")
    }
}
