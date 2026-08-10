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
 * Both language sides are required, because a word is only studiable as a pair; the
 * picture is not, and an empty one is no picture rather than an empty string. The id is
 * minted from the LEARNED side so it survives a switch of the language the learner speaks.
 */
data class OwnWordDraft(
    val known: String = "",
    val learning: String = "",
    val emoji: String = "",
) {
    val isComplete: Boolean get() = known.isNotBlank() && learning.isNotBlank()

    /**
     * The word as the box would take it in, or null while a side is still missing.
     * [taken] are the ids already in use — two words that fold alike count up rather than collide.
     */
    fun word(source: Language, target: Language, taken: Set<String>): OwnWord? {
        if (!isComplete) return null
        val learnt = learning.trim()
        return OwnWord(
            id = OwnWords.mint(learnt, taken),
            kind = OwnWords.DEFAULT_KIND,
            emoji = emoji.trim().ifEmpty { null },
            texts = mapOf(source to known.trim(), target to learnt),
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
