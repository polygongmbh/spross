package net.spross.kern.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal fun parseError(path: String, message: String): Nothing =
    throw CatalogFormatException("$path: $message")

internal fun parseJson(path: String, text: String): JsonElement =
    try {
        Json.parseToJsonElement(text)
    } catch (e: Exception) {
        parseError(path, "invalid JSON (${e.message})")
    }

internal fun JsonElement.obj(path: String, context: String): JsonObject =
    this as? JsonObject ?: parseError(path, "$context: expected an object")

internal fun JsonElement.arr(path: String, context: String): JsonArray =
    this as? JsonArray ?: parseError(path, "$context: expected an array")

/** Exact string content — never trimmed (en `"to "` must survive parsing). */
internal fun JsonElement.str(path: String, context: String): String {
    val primitive = this as? JsonPrimitive
    if (primitive == null || !primitive.isString) parseError(path, "$context: expected a string")
    return primitive.content
}

internal fun JsonObject.requireString(path: String, context: String, key: String): String {
    val value = this[key] ?: parseError(path, "$context: missing \"$key\"")
    return value.str(path, "$context.$key")
}

internal fun JsonObject.optionalString(path: String, context: String, key: String): String? =
    this[key]?.str(path, "$context.$key")

internal fun JsonObject.stringList(path: String, context: String, key: String): List<String> =
    this[key]?.arr(path, "$context.$key")?.mapIndexed { i, el ->
        el.str(path, "$context.$key[$i]")
    } ?: emptyList()

internal fun JsonObject.stringMap(path: String, context: String, key: String): Map<String, String> =
    this[key]?.obj(path, "$context.$key")?.mapValues { (k, v) ->
        v.str(path, "$context.$key.$k")
    } ?: emptyMap()

/** Null where the key is absent; a quoted `"true"` is a typo, not a boolean, and fails. */
internal fun JsonObject.optionalBoolean(path: String, context: String, key: String): Boolean? {
    val value = this[key] ?: return null
    val primitive = value as? JsonPrimitive
    if (primitive == null || primitive.isString) parseError(path, "$context.$key: expected a boolean")
    return primitive.booleanOrNull ?: parseError(path, "$context.$key: expected a boolean")
}

/** Null where the key is absent; a quoted `"1.5"` is a typo, not a measurement, and fails. */
internal fun JsonObject.optionalDouble(path: String, context: String, key: String): Double? {
    val content = numberContent(path, context, key) ?: return null
    return content.toDoubleOrNull() ?: parseError(path, "$context.$key: expected a number")
}

/** Whole numbers only — a `120.0` in milliseconds is a generator that lost its rounding. */
internal fun JsonObject.optionalLong(path: String, context: String, key: String): Long? {
    val content = numberContent(path, context, key) ?: return null
    return content.toLongOrNull() ?: parseError(path, "$context.$key: expected a whole number")
}

private fun JsonObject.numberContent(path: String, context: String, key: String): String? {
    val value = this[key] ?: return null
    val primitive = value as? JsonPrimitive
    if (primitive == null || primitive.isString) parseError(path, "$context.$key: expected a number")
    return primitive.content
}

internal fun JsonObject.stringListMap(
    path: String,
    context: String,
    key: String,
): Map<String, List<String>> =
    this[key]?.obj(path, "$context.$key")?.mapValues { (k, v) ->
        v.arr(path, "$context.$key.$k").mapIndexed { i, el -> el.str(path, "$context.$key.$k[$i]") }
    } ?: emptyMap()

internal fun JsonObject.rejectUnknownKeys(path: String, context: String, known: Set<String>) {
    val unknown = keys - known
    if (unknown.isNotEmpty()) parseError(path, "$context: unknown keys $unknown")
}
