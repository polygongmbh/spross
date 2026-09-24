package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

/** The catalog sweep reads a word by its alphabet's own rows, the longest glyph first. */
class AlphabetGraphemeTest {
    private fun pool(entries: String): List<String> {
        val catalog = AlphabetFixture.deEntries(entries)
        val row = catalog.alphabet("de")?.entry("au") ?: throw AssertionError("no au row")
        return catalog.alphabetExamples(row, "de").map { it.text }
    }

    private val au = """{ "glyph": "au", "kind": "digraph", "ipa": "aʊ" }"""

    @Test
    fun aGlyphStandingAloneIsSwept() {
        assertEquals(listOf("Maus"), pool(au))
    }

    /** "Maus" read as m-aus has no au in it, however the letters line up. */
    @Test
    fun aGlyphNestedInALongerRowsGlyphIsNot() {
        assertEquals(emptyList(), pool("""$au, { "glyph": "aus", "kind": "digraph", "ipa": "aʊs" }"""))
    }

    @Test
    fun theLongerRowTakesTheWordItStandsAloneIn() {
        val alphabet = AlphabetFixture.deEntries("""$au, { "glyph": "eau", "kind": "digraph", "ipa": "o" }""").alphabet("de")!!
        assertEquals(0, alphabet.graphemeOccurrences("bateau", "au"))
        assertEquals(1, alphabet.graphemeOccurrences("bateau", "eau"))
        assertEquals(1, alphabet.graphemeOccurrences("Restaurant", "au"))
    }
}
