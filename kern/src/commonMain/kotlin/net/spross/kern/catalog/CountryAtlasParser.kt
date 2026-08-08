package net.spross.kern.catalog

import kotlinx.serialization.json.JsonObject
import net.spross.kern.model.Language

/**
 * `catalog/countries/` → [CountryAtlas] and [CountryName], on [AlphabetParser]'s
 * conventions: unknown keys rejected, every failure a [CatalogFormatException] naming the
 * file and the row.
 *
 * The manifest is checked for the things no later lint could repair — a country naming a
 * language the manifest never declares, a duplicate slug, a tier outside the authored
 * range. What only CONTENT can break (a country nobody realizes, a language no table
 * names) is `CountryAtlasLintTest`'s, because the parser sees one file at a time.
 */
internal object CountryAtlasParser {
    private val SLUG_PATTERN = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
    private val CODE_PATTERN = Regex("^[a-z]{2,3}$")
    private val NAME_KEYS = setOf("text", "variants", "grammar", "nationality", "notes")

    /** The authored range; tier 1 is derived per profile and must never stand in a file. */
    private const val MIN_TIER = 2
    private const val MAX_TIER = 4

    fun parseAtlas(path: String, text: String): CountryAtlas {
        val root = parseJson(path, text).obj(path, "root")
        root.rejectUnknownKeys(path, "root", setOf("languages", "countries"))
        val languageRows = root["languages"]?.arr(path, "languages") ?: parseError(path, "missing \"languages\"")
        val languages = languageRows.mapIndexed { i, el ->
            val where = "languages[$i]"
            val o = el.obj(path, where)
            o.rejectUnknownKeys(path, where, setOf("code", "tier"))
            val code = o.requireString(path, where, "code")
            if (!CODE_PATTERN.matches(code)) parseError(path, "$where: bad code \"$code\"")
            AtlasLanguage(code, tier(path, "$where ($code)", o))
        }
        requireDistinct(path, "language", languages.map { it.code })
        val codes = languages.map { it.code }.toSet()

        val countryRows = root["countries"]?.arr(path, "countries") ?: parseError(path, "missing \"countries\"")
        if (countryRows.isEmpty()) parseError(path, "empty countries")
        val countries = countryRows.mapIndexed { i, el ->
            val where = "countries[$i]"
            val o = el.obj(path, where)
            o.rejectUnknownKeys(path, where, setOf("slug", "flag", "languages", "tier"))
            val slug = o.requireString(path, where, "slug")
            if (!SLUG_PATTERN.matches(slug)) parseError(path, "$where: bad slug \"$slug\"")
            val at = "$where ($slug)"
            val flag = o.requireString(path, at, "flag")
            if (!CatalogParser.isEmojiFlagSequence(flag)) parseError(path, "$at: flag must be one emoji flag sequence")
            val spoken = o.stringList(path, at, "languages")
            if (spoken.isEmpty()) parseError(path, "$at: no languages")
            for (code in spoken) {
                if (code !in codes) parseError(path, "$at: undeclared language \"$code\"")
            }
            requireDistinct(path, "$at language", spoken)
            AtlasCountry(slug, flag, spoken, tier(path, at, o))
        }
        requireDistinct(path, "country", countries.map { it.slug })
        return CountryAtlas(languages, countries)
    }

    /**
     * `catalog/countries/<lang>.json` → slug → name. [known] bounds the keys: a slug the
     * manifest never lists would be a file nobody reads, and [declared] bounds the note
     * readers exactly as a realization's do.
     */
    fun parseNames(
        path: String,
        text: String,
        known: Set<String>,
        declared: Set<Language>,
    ): Map<String, CountryName> {
        val root = parseJson(path, text).obj(path, "root")
        return root.entries.associate { (slug, el) ->
            if (slug !in known) parseError(path, "\"$slug\" is not a country the atlas lists")
            val o = el.obj(path, slug)
            o.rejectUnknownKeys(path, slug, NAME_KEYS)
            val notes = o.stringMap(path, slug, "notes")
            for ((reader, note) in notes) {
                if (reader !in declared) parseError(path, "$slug: note for undeclared language \"$reader\"")
                if (note.isBlank()) parseError(path, "$slug: blank note.$reader")
            }
            val nationality = o["nationality"]?.obj(path, "$slug.nationality")
                ?: parseError(path, "$slug: missing \"nationality\"")
            nationality.rejectUnknownKeys(path, "$slug.nationality", setOf("text", "variants"))
            slug to CountryName(
                text = o.trimmedString(path, slug, "text"),
                variants = variants(path, slug, o),
                grammar = o.stringMap(path, slug, "grammar"),
                nationality = NationalityName(
                    text = nationality.trimmedString(path, "$slug.nationality", "text"),
                    variants = variants(path, "$slug.nationality", nationality),
                ),
                notes = notes,
            )
        }
    }

    private fun variants(path: String, where: String, o: JsonObject): List<String> =
        o.stringList(path, where, "variants").onEach {
            if (it.isBlank() || it.trim() != it) parseError(path, "$where: bad variant \"$it\"")
        }

    private fun tier(path: String, where: String, o: JsonObject): Int {
        val tier = o.optionalLong(path, where, "tier") ?: parseError(path, "$where: missing \"tier\"")
        if (tier !in MIN_TIER.toLong()..MAX_TIER.toLong()) {
            parseError(path, "$where: tier $tier outside $MIN_TIER..$MAX_TIER")
        }
        return tier.toInt()
    }

    private fun requireDistinct(path: String, what: String, keys: List<String>) {
        keys.groupingBy { it }.eachCount().forEach { (key, count) ->
            if (count > 1) parseError(path, "duplicate $what \"$key\"")
        }
    }
}
