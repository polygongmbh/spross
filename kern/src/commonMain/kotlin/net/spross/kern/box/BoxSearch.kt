package net.spross.kern.box

import net.spross.kern.model.ACCENTED_VOWEL_BASE
import net.spross.kern.model.Card
import net.spross.kern.model.nfcNormalized

/** An area as the search sees it: its key and the heading the learner reads. */
data class SearchableArea(val area: String, val title: String)

/**
 * What one query found. Areas come first because matching one offers a whole shelf,
 * where a word offers itself; both lists are already ordered for display.
 */
data class BoxSearchResults(
    val areas: List<SearchableArea>,
    val cards: List<Card>,
) {
    val isEmpty: Boolean get() = areas.isEmpty() && cards.isEmpty()
}

/**
 * Browsing the box by typing, over the SAME cards the box holds — a card the current
 * profile does not join is not in `state.cards` and is therefore unfindable, exactly as
 * it is unlistable.
 *
 * Both sides are searched: the learner may remember the word they know or the word they
 * are learning, and either one should land. Comparison is NFC + lowercase, and a base
 * spelling (`u`, `ss`) reaches the accented text; a query typed with the diacritic
 * (`ü`, `ß`) reaches only that spelling, because the diacritic is part of what is being
 * learned and a search that erases it teaches the wrong thing about the language.
 */
object BoxSearch {
    /**
     * Cards one query may return. A query short enough to match half the box says the
     * learner is still typing, and a list that long is not read anyway.
     */
    const val CARD_LIMIT: Int = 60

    fun search(state: BoxState, areas: List<SearchableArea>, query: String): BoxSearchResults {
        val needle = fold(query)
        if (needle.isEmpty()) return BoxSearchResults(emptyList(), emptyList())
        return BoxSearchResults(
            areas = areas.filter { rank(it.title, needle) != null },
            cards = state.cards.values
                .mapNotNull { card -> cardRank(card, needle)?.let { it to card } }
                // why: rank first, then seed order — so an exact hit leads and everything
                // below it still reads in the order the box itself lists.
                .sortedWith(compareBy({ it.first }, { it.second.seedIndex }))
                .take(CARD_LIMIT)
                .map { it.second },
        )
    }

    /**
     * The card's best rank over every text it shows or accepts, `null` when nothing
     * matches. The canonical texts outrank the alternates: a word whose HEADWORD the
     * query hit is a better answer than one that merely lists it as a synonym.
     */
    private fun cardRank(card: Card, needle: String): Int? {
        val primary = listOf(card.target.text, card.source.text)
            .mapNotNull { rank(it, needle) }.minOrNull()
        if (primary != null) return primary
        val alternates = (
            card.target.synonyms + card.target.variants +
                card.source.synonyms + card.source.variants
            ).mapNotNull { rank(it, needle) }.minOrNull()
        return alternates?.let { it + ALTERNATE_PENALTY }
    }

    /** 0 = the whole text, 1 = its start, 2 = a word's start, 3 = somewhere inside. */
    private fun rank(hay: String, needle: String): Int? {
        val folded = fold(hay)
        return when {
            matchesWhole(folded, needle) -> 0
            matchesPrefix(folded, 0, needle) != null -> 1
            folded.split(' ').any { matchesPrefix(it, 0, needle) != null } -> 2
            matchesAnywhere(folded, needle) -> 3
            else -> null
        }
    }

    private fun matchesWhole(hay: String, needle: String): Boolean =
        matchesPrefix(hay, 0, needle) == hay.length

    // why: the matched span may be shorter in the haystack than in the query (`ss` reads
    // one `ß`), so an inside hit has to be tried from every offset rather than located.
    private fun matchesAnywhere(hay: String, needle: String): Boolean =
        hay.indices.any { matchesPrefix(hay, it, needle) != null }

    /** How far into [hay] the whole [needle] reads from [start], or `null` if it does not. */
    private fun matchesPrefix(hay: String, start: Int, needle: String): Int? {
        var read = 0
        var at = start
        while (read < needle.length) {
            if (at >= hay.length) return null
            val letter = hay[at]
            when {
                needle[read] == letter -> { read++; at++ }
                needle[read] == ACCENTED_VOWEL_BASE[letter] -> { read++; at++ }
                letter == 'ß' && needle.startsWith("ss", read) -> { read += 2; at++ }
                else -> return null
            }
        }
        return at
    }

    private fun fold(text: String): String = nfcNormalized(text).trim().lowercase()

    /** Wider than the whole rank scale, so no alternate ever outranks a headword. */
    private const val ALTERNATE_PENALTY = 4
}
