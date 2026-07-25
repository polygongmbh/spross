package net.spross.app

import net.spross.kern.model.Realization

/** Pure display helpers for card rendering (design.md "Presentation model in the UI"). */
object CardDisplay {

    /** grammar["gender"] when present — the UI maps it to the article tint. */
    fun gender(realization: Realization): String? = realization.grammar["gender"]

    /**
     * Labelled plural line for the TARGET side only: every real form carries the
     * localized "Pl. " prefix, with a suffix resolved against the word
     * ("-nen" → "Pl. Lehrerinnen"); "=" / "only" sentinels via chrome strings.
     */
    fun pluralLine(realization: Realization, chrome: Chrome): String? {
        val plural = realization.grammar["plural"] ?: return null
        return when {
            plural == "=" -> chrome.pluralEquals
            plural == "only" -> chrome.pluralOnly
            plural.startsWith("-") -> chrome.pluralPrefix + realization.text + plural.drop(1)
            else -> chrome.pluralPrefix + plural
        }
    }

    /** Reveal family: canonical text plus "auch: …" alternates when synonyms exist. */
    fun alsoLine(realization: Realization, chrome: Chrome): String? =
        realization.synonyms.takeIf { it.isNotEmpty() }
            ?.joinToString(" / ", prefix = "${chrome.alsoPrefix} ")
}
