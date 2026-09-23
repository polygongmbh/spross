package net.spross.app

import net.spross.kern.model.ClosingNote
import net.spross.kern.model.PluralForm
import net.spross.kern.model.Realization
import net.spross.kern.model.alternates
import net.spross.kern.model.closingNote
import net.spross.kern.model.pluralForm

/**
 * The WORDS this platform wraps around kern's reveal rules.
 *
 * Which authored plural is a sentinel and which resolves against the word, which forms
 * are left to offer once the ones on screen are taken out, and which line closes the card
 * are `model/DisplayText.kt`'s — one definition for both apps.
 * The labels ("Pl. ", "= Pl.", "auch:", "bedeutet auch:") and the " / " between forms
 * are chrome and stay here.
 */
object CardDisplay {

    /**
     * The realization's article, for the tint — de `grammar["gender"]` carries the
     * article itself ("der"/"die"/"das"), never a gender name.
     */
    fun article(realization: Realization): String? = realization.grammar["gender"]

    /** Labeled plural line for the TARGET side only (grammar is target-side, contract §2). */
    fun pluralLine(realization: Realization, chrome: Chrome): String? =
        when (val plural = pluralForm(realization)) {
            null -> null
            PluralForm.SameAsSingular -> chrome.sessionGrammarPluralEquals
            PluralForm.PluralOnly -> chrome.sessionGrammarPluralOnly
            is PluralForm.Form -> chrome.sessionGrammarPlural.format(plural.text)
        }

    /** "auch: …" — the word's family beyond every form already standing on screen. */
    fun alsoLine(realization: Realization, chrome: Chrome, shown: Collection<String>): String? =
        alternates(realization, shown.toList())
            .takeIf { it.isNotEmpty() }
            ?.let { chrome.sessionGrammarAlso.format(it.joinToString(" / ")) }

    fun alsoLine(realization: Realization, chrome: Chrome, shown: String): String? =
        alsoLine(realization, chrome, listOf(shown))

    /**
     * The card's LAST line, which it grows only once it has stopped asking.
     * Which line that is, is kern's `closingNote`; the "bedeutet auch:" label is chrome.
     */
    fun closingNote(
        realization: Realization,
        alsoMeans: List<String>,
        chrome: Chrome,
        revealed: Boolean,
    ): String? {
        if (!revealed) return null
        return when (val note = closingNote(realization, alsoMeans)) {
            null -> null
            is ClosingNote.Own -> note.text
            is ClosingNote.AlsoMeans -> chrome.sessionMeansAlso.format(note.meanings.joinToString(" / "))
        }
    }
}
