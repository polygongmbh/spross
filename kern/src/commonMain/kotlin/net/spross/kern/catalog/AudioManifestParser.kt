package net.spross.kern.catalog

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import net.spross.kern.model.Language

internal object AudioManifestParser {
    private val WORD_KEYS =
        setOf("file", "matches", "license", "author", "source", "sha256",
              "gain", "cap", "gainPhone", "capPhone", "lead", "snr")
    private val LETTER_KEYS = WORD_KEYS - "matches"
    private val ARTICLE_KEYS = WORD_KEYS + "word"

    /** Five seconds of dead air is not a lead-in, it is the wrong recording. */
    private const val LEAD_LIMIT_MS = 5_000L

    fun parse(path: String, text: String, expectedLanguage: Language): AudioManifest {
        val root = parseJson(path, text).obj(path, "root")
        root.rejectUnknownKeys(
            path, "root",
            setOf("language", "authors", "licenses", "words", "letters", "texts", "articles",
                  "calendar", "countries"),
        )
        val language = root.requireString(path, "root", "language")
        if (language != expectedLanguage) {
            parseError(path, "declares language \"$language\", expected \"$expectedLanguage\"")
        }
        val credits = Credits(
            authors = root.stringMap(path, "root", "authors"),
            licenses = root["licenses"]?.obj(path, "licenses")
                ?.mapValues { (name, deed) -> deed.deedUrl(path, "licenses.$name") }
                ?: emptyMap(),
        )
        return AudioManifest(
            language = language,
            words = section(path, root, "words", WORD_KEYS, credits),
            letters = section(path, root, "letters", LETTER_KEYS, credits),
            texts = section(path, root, "texts", WORD_KEYS, credits),
            articles = section(path, root, "articles", ARTICLE_KEYS, credits),
            calendar = section(path, root, "calendar", WORD_KEYS, credits),
            countries = section(path, root, "countries", WORD_KEYS, credits),
        )
    }

    /**
     * The manifest's two root maps. A license is a property of the SPEAKER — across every
     * shipped pack fourteen entries out of 5828 depart from their own author's — so it is
     * authored once per author rather than once per file, and its deed once per license.
     * An entry's own `license` overrides [authors] for exactly those departures; there is
     * deliberately no default AUTHOR, because a missing key would then read as a credit
     * to whoever recorded the most, and a misattribution by omission is the one thing a
     * BY notice may not do.
     */
    private class Credits(val authors: Map<String, String>, val licenses: Map<String, String?>) {
        fun license(path: String, context: String, own: String?, author: String): String {
            // why: the author is looked up even where the entry licenses itself — every
            // credited speaker belongs in `authors`, and an entry naming one who is not
            // there is a manifest whose credit map no longer describes its own contents.
            val usual = authors[author]
                ?: parseError(path, "$context: author \"$author\" is not in \"authors\"")
            val license = own ?: usual
            if (license !in licenses) {
                parseError(path, "$context: license \"$license\" is not in \"licenses\"")
            }
            return license
        }

        /** Null exactly where the license has no deed to link — a public-domain file. */
        fun deed(license: String): String? = licenses[license]
    }

    /** A deed is a URL or JSON `null` — public domain is the one license with none to link. */
    private fun JsonElement.deedUrl(path: String, context: String): String? =
        if (this is JsonNull) null else str(path, context)

    /**
     * Parses one keyed section; `matches` and `word` are each required exactly where the
     * key set allows them — the letters speak a name rather than a form, and only an
     * article entry carries the bare word inside what it says.
     */
    private fun section(
        path: String,
        root: JsonObject,
        key: String,
        known: Set<String>,
        credits: Credits,
    ): Map<String, AudioRecording> {
        val section = root[key]?.obj(path, key) ?: return emptyMap()
        return section.entries.associate { (id, element) ->
            val context = "$key.$id"
            val entry = element.obj(path, context)
            entry.rejectUnknownKeys(path, context, known)
            val author = entry.requireNonBlank(path, context, "author")
            val license = credits.license(
                path, context, entry.optionalString(path, context, "license"), author,
            )
            id to AudioRecording(
                file = entry.requireNonBlank(path, context, "file"),
                matches = if ("matches" in known) entry.requireNonBlank(path, context, "matches") else null,
                word = if ("word" in known) entry.requireNonBlank(path, context, "word") else null,
                license = license,
                licenseUrl = credits.deed(license),
                author = author,
                source = entry.requireNonBlank(path, context, "source"),
                sha256 = entry.requireNonBlank(path, context, "sha256"),
                gain = entry.gain(path, context, "gain"),
                gainPhone = entry.optionalGain(path, context, "gainPhone"),
                cap = entry.optionalCap(path, context, "cap") ?: 0.0,
                capPhone = entry.optionalCap(path, context, "capPhone"),
                leadMs = entry.leadMs(path, context),
                snr = entry.optionalDouble(path, context, "snr") ?: 0.0,
            )
        }
    }

    private fun JsonObject.requireNonBlank(path: String, context: String, key: String): String =
        requireString(path, context, key).also {
            if (it.isBlank()) parseError(path, "$context: blank \"$key\"")
        }

    /**
     * Absent means the recording already sits at the analysis target.
     * The bound is playback's own ([Playback.GAIN_LIMIT_DB]): rejecting here and clamping
     * there are one rule about what a measurement may claim.
     */
    private fun JsonObject.gain(path: String, context: String, key: String): Double =
        optionalGain(path, context, key) ?: 0.0

    /** [gain]'s optional twin — `gainPhone` is absent exactly where no phone plane was measured. */
    private fun JsonObject.optionalGain(path: String, context: String, key: String): Double? {
        val gain = optionalDouble(path, context, key) ?: return null
        val limit = Playback.GAIN_LIMIT_DB
        if (gain !in -limit..limit) {
            parseError(path, "$context: $key $gain dB is outside ±$limit")
        }
        return gain
    }

    /**
     * Absent means the ceiling held nothing back. A deficit is the distance between two
     * gains and never a gain itself, so it is bounded by the pair rather than by
     * [Playback.GAIN_LIMIT_DB] alone — and it can only ever be positive: a cap that lifted
     * a recording would be the ceiling handing out headroom no file has.
     */
    private fun JsonObject.optionalCap(path: String, context: String, key: String): Double? {
        val cap = optionalDouble(path, context, key) ?: return null
        if (cap < 0.0 || cap > 2 * Playback.GAIN_LIMIT_DB) {
            parseError(path, "$context: $key $cap dB is outside 0..${2 * Playback.GAIN_LIMIT_DB}")
        }
        return cap
    }

    /** Absent means the recording starts speaking at once. */
    private fun JsonObject.leadMs(path: String, context: String): Long {
        val lead = optionalLong(path, context, "lead") ?: return 0
        if (lead !in 0..LEAD_LIMIT_MS) {
            parseError(path, "$context: lead $lead ms is outside 0..$LEAD_LIMIT_MS")
        }
        return lead
    }
}
