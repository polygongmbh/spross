package net.spross.app

import net.spross.kern.model.Realization

/**
 * Pure display helpers for card rendering (design.md "Presentation model in the UI").
 *
 * Plural resolution and the alternates rule are still written twice — here and in
 * `App/Sources/Model/DisplayText.swift`. They belong in kern beside `articleGender`
 * (docs/portability.md, "Smaller, mostly cheap"); until that move they stay in step
 * by hand, and the fixes below are what drifting apart already cost.
 */
object CardDisplay {

    /**
     * The realization's article, for the tint — de `grammar["gender"]` carries the
     * article itself ("der"/"die"/"das"), never a gender name.
     */
    fun article(realization: Realization): String? = realization.grammar["gender"]

    /**
     * Labelled plural line for the TARGET side only: every real form carries the
     * localized "Pl. " prefix, with a suffix resolved against the word
     * ("-nen" → "Pl. Lehrerinnen"); "=" / "only" sentinels via chrome strings.
     *
     * An authored-but-empty value is not a form: it would render a bare label.
     */
    fun pluralLine(realization: Realization, chrome: Chrome): String? {
        val plural = realization.grammar["plural"]?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            plural == "=" -> chrome.pluralEquals
            plural == "only" -> chrome.pluralOnly
            plural.startsWith("-") -> chrome.pluralPrefix + realization.text + plural.drop(1)
            else -> chrome.pluralPrefix + plural
        }
    }

    /**
     * "auch: …" — the word's remaining family: its canonical form plus its synonyms,
     * minus every form already standing on screen.
     *
     * The exclusion is the whole point of the line. A recognition prompt rotates a
     * synonym in, so without it the reveal offers the learner the very word they are
     * looking at as though it were another one.
     */
    fun alsoLine(realization: Realization, chrome: Chrome, shown: Collection<String>): String? {
        val family = (listOf(realization.text) + realization.synonyms).filterNot { it in shown }
        return family.takeIf { it.isNotEmpty() }
            ?.joinToString(" / ", prefix = "${chrome.alsoPrefix} ")
    }

    fun alsoLine(realization: Realization, chrome: Chrome, shown: String): String? =
        alsoLine(realization, chrome, listOf(shown))
}
