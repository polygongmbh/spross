package net.spross.app

import net.spross.kern.model.PluralForm
import net.spross.kern.model.Realization
import net.spross.kern.model.alternates
import net.spross.kern.model.pluralForm

/**
 * The WORDS this platform wraps around kern's two reveal rules.
 *
 * Which authored plural is a sentinel and which resolves against the word, and which forms
 * are left to offer once the ones on screen are taken out, are `model/DisplayText.kt`'s —
 * one definition for both apps, where these lines used to be written twice and drift.
 * The labels ("Pl. ", "= Pl.", "auch:") and the " / " between forms are chrome and stay here.
 */
object CardDisplay {

    /**
     * The realization's article, for the tint — de `grammar["gender"]` carries the
     * article itself ("der"/"die"/"das"), never a gender name.
     */
    fun article(realization: Realization): String? = realization.grammar["gender"]

    /** Labelled plural line for the TARGET side only (grammar is target-side, contract §2). */
    fun pluralLine(realization: Realization, chrome: Chrome): String? =
        when (val plural = pluralForm(realization)) {
            null -> null
            PluralForm.SameAsSingular -> chrome.pluralEquals
            PluralForm.PluralOnly -> chrome.pluralOnly
            is PluralForm.Form -> chrome.pluralPrefix + plural.text
        }

    /** "auch: …" — the word's family beyond every form already standing on screen. */
    fun alsoLine(realization: Realization, chrome: Chrome, shown: Collection<String>): String? =
        alternates(realization, shown.toList())
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" / ", prefix = "${chrome.alsoPrefix} ")

    fun alsoLine(realization: Realization, chrome: Chrome, shown: String): String? =
        alsoLine(realization, chrome, listOf(shown))
}
