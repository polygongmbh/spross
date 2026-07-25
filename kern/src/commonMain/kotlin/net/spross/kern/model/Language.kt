package net.spross.kern.model

/** Language code from `catalog/languages.json` — an open set, deliberately not an enum. */
typealias Language = String

/** Per-language metadata from `catalog/languages.json`. */
data class LanguageInfo(
    val code: Language,
    /** The language's own name for itself ("Deutsch", "Українська") — pickers use this. */
    val name: String,
    /** English exonym ("German", "Ukrainian"). */
    val englishName: String,
    /** Exactly one emoji flag sequence (sw uses 🇹🇿, the v1 choice). */
    val flag: String,
    /**
     * Infinitive citation prefixes (en `"to "`, sw `"ku"`/`"kw"`).
     * A leading occurrence of any entry is optional when grading verb input.
     * Entries preserve authored whitespace (`"to "` keeps its trailing space).
     */
    val optionalVerbPrefixes: List<String> = emptyList(),
    /** The language's articles; ONE leading listed article is optional when grading. */
    val articles: List<String> = emptyList(),
)
