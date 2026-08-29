package net.spross.app.ui

import net.spross.app.Chrome
import net.spross.kern.box.OwnWord
import net.spross.kern.box.OwnWords
import net.spross.kern.box.SearchableArea
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.model.Language

/**
 * How the browser names one shelf to this reader.
 *
 * Catalog areas name themselves in the SOURCE language — that is content, authored per
 * area — while the learner's own shelf has no catalog entry at all and takes its name
 * from the chrome. The three lookups arrive as functions so the rule can be read (and
 * pinned) without a parsed catalog behind it.
 */
class AreaNaming(
    private val chrome: Chrome,
    private val catalogTitle: (String) -> String?,
    private val catalogSubtitle: (String) -> String?,
    private val catalogEmoji: (String) -> String?,
) {
    /** An area the catalog cannot name falls back to its own key — a visible content bug, not a blank. */
    fun title(area: String): String =
        if (area == OwnWords.AREA) chrome.ownWordsTitle else catalogTitle(area) ?: area

    fun subtitle(area: String): String? =
        if (area == OwnWords.AREA) chrome.ownWordsExplainer else catalogSubtitle(area)

    fun emoji(area: String): String =
        if (area == OwnWords.AREA) OwnWords.EMOJI else catalogEmoji(area) ?: FALLBACK_EMOJI

    /**
     * The areas as the search matches them: on the heading the learner READ, never on the
     * key underneath it — nobody types "own" looking for their own words.
     */
    fun searchable(areas: List<String>): List<SearchableArea> =
        areas.map { SearchableArea(it, title(it)) }

    private companion object {
        const val FALLBACK_EMOJI = "📦"
    }
}

/**
 * A word being written down, before it is one.
 *
 * ONE side is enough to take it in: that is a SUGGESTION, which joins no card and is never
 * asked until the other half arrives ([OwnWord]). The picture is optional either way, and
 * an empty one is no picture rather than an empty string.
 */
data class OwnWordDraft(
    val known: String = "",
    val learning: String = "",
    val emoji: String = "",
) {
    /** Both sides written: a studiable word rather than a suggestion. */
    val isPair: Boolean get() = known.isNotBlank() && learning.isNotBlank()

    /** One side is enough to take the word in — the other is what makes it studiable. */
    val hasAnything: Boolean get() = known.isNotBlank() || learning.isNotBlank()

    /**
     * The word as the box would take it in, or null while both sides are still blank.
     * [taken] are the ids already in use — two words that fold alike count up rather than collide.
     */
    fun word(source: Language, target: Language, taken: Set<String>): OwnWord? {
        if (!hasAnything) return null
        val knownText = known.trim()
        val learnt = learning.trim()
        // why: the id is minted from the LEARNED side — it is the one that stays put while
        // the known language is free to change under a source switch. A word written only
        // in the known language has nothing else to be named after.
        return OwnWords.write(
            id = OwnWords.mint(learnt.ifEmpty { knownText }, taken),
            kind = OwnWords.DEFAULT_KIND,
            emoji = emoji.trim().ifEmpty { null },
            texts = buildMap {
                if (knownText.isNotEmpty()) put(source, knownText)
                if (learnt.isNotEmpty()) put(target, learnt)
            },
        )
    }
}

/**
 * The pair a picker tap lands on, or null when it lands on the pair already in force.
 *
 * [LanguageChoices] has already decided whether the tap swapped the sides or moved one of
 * them; this is only the guard in front of re-joining the box, which is neither free nor
 * silent — a tap on the row already selected must not rebuild it.
 */
fun appliedPair(
    next: LanguageChoices.Selection,
    current: LanguageChoices.Selection,
): Pair<Language, Language>? {
    val target = next.target ?: return null
    if (next.source == current.source && target == current.target) return null
    return next.source to target
}
