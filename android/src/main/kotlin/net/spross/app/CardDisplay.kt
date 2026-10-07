package net.spross.app

import net.spross.kern.model.Realization
import net.spross.kern.model.alternates

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
        alternates(realization, shown.toList())
            .takeIf { it.isNotEmpty() }
            ?.let { chrome.sessionGrammarAlso.format(it.joinToString(" / ")) }

    fun alsoLine(realization: Realization, chrome: Chrome, shown: String): String? =
        alsoLine(realization, chrome, listOf(shown))
}
