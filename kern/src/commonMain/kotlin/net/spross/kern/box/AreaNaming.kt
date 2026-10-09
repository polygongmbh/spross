package net.spross.kern.box

import net.spross.kern.catalog.Catalog
import net.spross.kern.model.Language

/**
 * How the browser names one shelf to this reader: its title, flavor line and picture.
 *
 * Catalog areas name themselves in the SOURCE language — content, authored per area —
 * while the learner's own shelf has no catalog entry and takes its name from the chrome,
 * which the platform resolves from its string table and hands in as [ownTitle] / [ownSubtitle].
 * Every shelf is resolved in one pass on first ask, since the browser asks all three per
 * shelf and the Trees picture asks the emoji again per tree.
 */
class AreaNaming(
    private val catalog: Catalog?,
    private val source: Language?,
    private val ownTitle: String,
    private val ownSubtitle: String?,
) {
    private val titles: Map<String, String> by lazy { resolved { area, lang -> catalog?.areaTitle(area, lang) } }
    private val subtitles: Map<String, String> by lazy { resolved { area, lang -> catalog?.areaSubtitle(area, lang) } }
    private val emojis: Map<String, String> by lazy {
        catalog?.areaNames?.mapNotNull { area -> catalog.areaEmoji(area)?.let { area to it } }?.toMap().orEmpty()
    }

    /** An area the catalog cannot name reads as its own key, capitalized — a visible content bug, not a blank. */
    fun title(area: String): String =
        if (area == OwnWords.AREA) ownTitle else titles[area] ?: fallbackTitle(area)

    /** Optional content: null is the ordinary answer for an area that authors none. */
    fun subtitle(area: String): String? =
        if (area == OwnWords.AREA) ownSubtitle else subtitles[area]

    fun emoji(area: String): String =
        if (area == OwnWords.AREA) OwnWords.EMOJI else emojis[area] ?: FALLBACK_EMOJI

    /**
     * The areas as the search matches them: on the heading the learner READ, never on the
     * key underneath it — nobody types "own" looking for their own words.
     */
    fun searchable(areas: List<String>): List<SearchableArea> =
        areas.map { SearchableArea(it, title(it)) }

    private fun resolved(lookup: (String, Language) -> String?): Map<String, String> {
        val lang = source ?: return emptyMap()
        return catalog?.areaNames?.mapNotNull { area -> lookup(area, lang)?.let { area to it } }?.toMap().orEmpty()
    }

    private companion object {
        const val FALLBACK_EMOJI = "📦"

        fun fallbackTitle(area: String): String =
            area.split(' ').joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
    }
}
