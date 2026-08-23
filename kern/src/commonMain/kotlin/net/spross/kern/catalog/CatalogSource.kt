package net.spross.kern.catalog

/** Reads catalog files by path relative to the catalog root (`"areas.json"`, `"areas/kitchen/de.json"`). */
interface CatalogSource {
    /** File content, or null when the file does not exist. */
    fun read(path: String): String?
}

/** Raised on malformed catalog content; the message carries the offending path. */
class CatalogFormatException internal constructor(message: String) :
    IllegalArgumentException(message)
