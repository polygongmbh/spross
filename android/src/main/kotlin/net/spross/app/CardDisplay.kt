package net.spross.app

import net.spross.kern.model.Realization

/** Pure display helpers for card rendering (design.md "Presentation model in the UI"). */
object CardDisplay {

    /** grammar["gender"] when present — the UI maps it to the article tint. */
    fun gender(realization: Realization): String? = realization.grammar["gender"]

    /**
     * Dictionary-style plural line for the TARGET side only:
     * "-nen" → "Lehrerin, -nen"; "=" / "only" sentinels via chrome strings;
     * a full plural word gets the localized "Pl.: " prefix.
     */
    fun pluralLine(realization: Realization, chrome: Chrome): String? {
        val plural = realization.grammar["plural"] ?: return null
        return when {
            plural == "=" -> chrome.pluralEquals
            plural == "only" -> chrome.pluralOnly
            plural.startsWith("-") -> "${realization.text}, $plural"
            else -> chrome.pluralPrefix + plural
        }
    }

    /** Reveal family: canonical text plus "auch: …" alternates when synonyms exist. */
    fun alsoLine(realization: Realization, chrome: Chrome): String? =
        realization.synonyms.takeIf { it.isNotEmpty() }
            ?.joinToString(" / ", prefix = "${chrome.alsoPrefix} ")
}
