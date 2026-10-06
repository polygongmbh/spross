package net.spross.kern.catalog

import net.spross.kern.model.CardKind
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The content rules over `opposites/pairs.json` the parser cannot see one file at a time:
 * a pair is two WORDS of one kind, since a phrase has no opposite to type and a verb's
 * opposite is never an adjective.
 */
class OppositePairLintTest {
    private val catalog get() = RealCatalog.catalog

    private fun kind(slug: String): CardKind =
        catalog.areas.firstNotNullOf { area -> area.concepts.firstOrNull { it.slug == slug }?.kind }

    @Test
    fun aPairIsTwoWordsOfOneKind() {
        assertTrue(catalog.oppositePairs.isNotEmpty(), "opposites/pairs.json did not load")
        for (pair in catalog.oppositePairs) {
            val kinds = setOf(kind(pair.first), kind(pair.second))
            assertTrue(kinds.size == 1, "${pair.first}/${pair.second}: kinds differ $kinds")
            assertTrue(kinds.single() !in setOf(CardKind.Phrase, CardKind.Idiom), "${pair.first}: not a word")
        }
    }
}
