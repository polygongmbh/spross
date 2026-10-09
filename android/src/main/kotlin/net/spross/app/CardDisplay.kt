package net.spross.app

import net.spross.kern.model.Alternate
import net.spross.kern.model.FormTag
import net.spross.kern.model.Realization
import net.spross.kern.model.alternates
import net.spross.kern.model.formGlyph

/**
 * The WORDS this platform wraps around kern's reveal rules.
 *
 * Which forms are left to offer once the ones on screen are taken out is
 * `model/DisplayText.kt`'s — one definition for both apps.
 * The "auch:" label and the " / " between forms are chrome and stay here;
 * a question card words its own lines (`QuestionCardWords.kt`).
 */
object CardDisplay {

    /**
     * The realization's article, for the tint — de `grammar["gender"]` carries the
     * article itself ("der"/"die"/"das"), never a gender name.
     */
    fun article(realization: Realization): String? = realization.grammar["gender"]

    /** "auch: …" — the word's family beyond every form already standing on screen. */
    fun alsoLine(realization: Realization, chrome: Chrome, shown: Collection<String>): String? =
        alsoLine(alternates(realization, shown.toList(), realization.forms), chrome)

    /** "auch: die Lehrerin ♀ / …" — each form with the marker that says which one it is. */
    fun alsoLine(family: List<Alternate>, chrome: Chrome): String? =
        family.takeIf { it.isNotEmpty() }
            ?.joinToString(" / ") { alternate -> alternate.marker?.let { "${alternate.text} ${marker(it, chrome)}" } ?: alternate.text }
            ?.let { chrome.sessionGrammarAlso.format(it) }

    /** A form tag as the badge reads it: gender as its glyph, the rest as the grammar's abbreviation. */
    fun marker(tag: FormTag, chrome: Chrome): String =
        tag.parts.joinToString(" ") { part -> formGlyph(part) ?: abbreviation(part, chrome) }

    /** [marker] as a screen reader says it: the gender glyphs by name. */
    fun markerSpoken(tag: FormTag, chrome: Chrome): String = tag.parts.joinToString(" ") { part ->
        when (part) {
            "f" -> chrome.a11yGlyphFeminineForm
            "m" -> chrome.a11yGlyphMasculineForm
            "n" -> chrome.a11yGlyphNeuterForm
            else -> abbreviation(part, chrome)
        }
    }

    private fun abbreviation(part: String, chrome: Chrome): String =
        when (part) {
            "pl" -> chrome.formMarkerPl
            "nom" -> chrome.formMarkerNom
            "gen" -> chrome.formMarkerGen
            "dat" -> chrome.formMarkerDat
            "acc" -> chrome.formMarkerAcc
            "ins" -> chrome.formMarkerIns
            "loc" -> chrome.formMarkerLoc
            "voc" -> chrome.formMarkerVoc
            else -> chrome.formMarkerClass.format(part)
        }

    fun alsoLine(realization: Realization, chrome: Chrome, shown: String): String? =
        alsoLine(realization, chrome, listOf(shown))
}
