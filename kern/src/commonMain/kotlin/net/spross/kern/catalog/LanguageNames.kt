package net.spross.kern.catalog

import net.spross.kern.model.Language

/**
 * What one language calls another, in the grammatical forms a sentence needs.
 * Authored in `catalog/languages/<lang>.json`, keyed by the language being NAMED.
 *
 * The fields name grammatical roles, never renderings: [inForm] is the "in X" adverbial
 * WITH whatever adposition the language uses (de "auf Deutsch", sw "kwa Kijerumani",
 * uk instrumental "німецькою"), so a sentence carrying [LanguageMarker.In] supplies none
 * of its own. [speak] and [learn] are the verb-object forms; a language whose object
 * looks like the citation form authors neither.
 */
data class LanguageName(
    /** Citation form — what the language is CALLED, and [LanguageMarker.Name]'s value. */
    val name: String,
    /** The "in X" adverbial, adposition included. */
    val inForm: String,
    /** Object of "to speak"; null means [name] already is it. */
    val speak: String? = null,
    /** Object of "to learn"; null means [name] already is it. */
    val learn: String? = null,
    /** Accept-only alternates (de "Kisuaheli" beside "Suaheli"), never displayed. */
    val variants: List<String> = emptyList(),
    /** Keyed by explanation language, exactly as a realization's notes are. */
    val notes: Map<Language, String> = emptyMap(),
) {
    fun form(marker: LanguageMarker): String = when (marker) {
        LanguageMarker.Name -> name
        LanguageMarker.In -> inForm
        LanguageMarker.Speak -> speak ?: name
        LanguageMarker.Learn -> learn ?: name
    }
}

/**
 * The forms an authored string may ask a language name for. The marker's PRESENCE is the
 * declaration — no concept or frame field says a text is language-dependent, and every
 * marker names the profile's target language.
 */
enum class LanguageMarker(val marker: String) {
    Name("{language}"),
    In("{language-in}"),
    Speak("{language-speak}"),
    Learn("{language-learn}"),
}

/** Marker recognition and resolution over authored strings. */
object LanguageNames {
    /** Every marker opens with this, so a bare occurrence is already a typo worth catching. */
    const val PREFIX: String = "{language"

    fun hasLanguageMarker(text: String): Boolean = PREFIX in text

    /** [text] with its marker replaced by [name]'s form; unchanged where it carries none. */
    fun resolve(text: String, name: LanguageName): String {
        val at = text.indexOf(PREFIX)
        if (at < 0) return text
        val marker = markerAt(text, at) ?: return text
        return text.substring(0, at) + name.form(marker) + text.substring(at + marker.marker.length)
    }

    /**
     * Why [text] cannot carry the marker it carries, or null when it is well formed. Three
     * rules, each unfixable once shipped: ONE marker per string (a second has no second
     * language to name), a KNOWN form (an unknown one would ship verbatim to the learner),
     * and never string-INITIAL — nothing re-capitalizes what a marker inserts.
     */
    internal fun markerError(text: String): String? {
        val at = text.indexOf(PREFIX)
        if (at < 0) return null
        val marker = markerAt(text, at) ?: return "unknown language marker in \"$text\""
        if (at == 0) return "language marker opens \"$text\""
        if (hasLanguageMarker(text.substring(at + marker.marker.length))) {
            return "second language marker in \"$text\""
        }
        return null
    }

    private fun markerAt(text: String, at: Int): LanguageMarker? =
        LanguageMarker.entries.firstOrNull { text.startsWith(it.marker, at) }
}
