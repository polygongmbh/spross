package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The catalog sweep behind a gap question, over the SHIPPING alphabets (contract §2.2).
 *
 * The whole correctness argument of the sweep is that it only ever runs where the glyph
 * string identifies the row's sound on its own, so these are rules about WHICH rows may
 * mine and what a mined word has to be — never a pinned word list, which every catalog
 * edit would break for no reason.
 */
class AlphabetExamplePoolTest {
    private val catalog get() = RealCatalog.catalog
    private val languages get() = catalog.languages.keys.filter { catalog.alphabet(it) != null }

    /** Kern asks this of every candidate before it prompts; nothing may reach it and fail. */
    @Test
    fun everyMinedWordCutsExactlyOneGap() {
        forEachRow { lang, entry ->
            for (example in catalog.alphabetExamples(entry, lang)) {
                assertEquals(
                    1,
                    glyphOccurrences(example.text, entry.glyph),
                    "alphabet/$lang ${entry.ref}: \"${entry.glyph}\" is not alone in \"${example.text}\"",
                )
                assertNotNull(
                    entry.gapWord(example.text),
                    "alphabet/$lang ${entry.ref}: \"${example.text}\" cuts no gap",
                )
            }
        }
    }

    /**
     * A gap word is one WORD. The catalog realizes whole sentences too ("Nein.",
     * "Feierabend!"), and a blank inside one is a different exercise wearing this one's
     * copy — except where an author wrote the example by hand, which stands as authored.
     */
    @Test
    fun aMinedWordIsBareOfSpacesAndSentencePunctuation() {
        forEachRow { lang, entry ->
            for (example in catalog.alphabetExamples(entry, lang).drop(1)) {
                assertTrue(
                    isGappableWord(example.text),
                    "alphabet/$lang ${entry.ref}: swept in \"${example.text}\"",
                )
            }
        }
    }

    /** The author's word is never displaced by the sweep — it leads the pool it opens. */
    @Test
    fun theAuthoredExampleComesFirst() {
        forEachRow { lang, entry ->
            val authored = catalog.alphabetExample(entry, lang) ?: return@forEachRow
            val pool = catalog.alphabetExamples(entry, lang)
            assertEquals(
                authored.slug,
                pool.firstOrNull()?.slug,
                "alphabet/$lang ${entry.ref}: the authored example lost its place",
            )
            assertEquals(pool.map { it.slug }.distinct(), pool.map { it.slug }, "$lang ${entry.ref}: duplicate")
        }
    }

    /**
     * The bar itself: a row whose glyph does not decide its own sound gets ONE word, the
     * one a human chose. de `ch`×3 and `s`×2 are position-bound, es `gu`/`gü`/`qu` declare
     * an environment, and de `chs` opts out by hand because its only catalog hit is a
     * compound seam (`Sprechstunde`) rather than the /ks/ the row teaches.
     */
    @Test
    fun aRowThatCannotBeSweptKeepsItsOneAuthoredWord() {
        forEachRow { lang, entry ->
            if (catalog.alphabet(lang)!!.minesExamples(entry)) return@forEachRow
            assertTrue(
                catalog.alphabetExamples(entry, lang).size <= 1,
                "alphabet/$lang ${entry.ref}: swept despite being position-bound",
            )
        }
        val de = assertNotNull(catalog.alphabet("de"))
        for (ref in listOf("chs", "ch-ich", "ch-ach", "s-voiced", "d-final", "er")) {
            val entry = assertNotNull(de.entry(ref), "de $ref went missing")
            assertTrue(catalog.alphabetExamples(entry, "de").size <= 1, "de $ref was swept")
        }
    }

    /**
     * And the other half: the sweep has to actually reach the rows it exists for, or the
     * drill quietly goes back to asking the same word all evening with every gate green.
     */
    @Test
    fun thePlainDigraphsDrawFromTheWholeCatalog() {
        for ((lang, refs) in mapOf("de" to listOf("ei", "ie", "sch", "ss"), "es" to listOf("ll", "ch", "rr"))) {
            val alphabet = assertNotNull(catalog.alphabet(lang), "no $lang alphabet")
            for (ref in refs) {
                val entry = assertNotNull(alphabet.entry(ref), "$lang $ref went missing")
                assertTrue(
                    catalog.alphabetExamples(entry, lang).size >= 5,
                    "alphabet/$lang $ref: only ${catalog.alphabetExamples(entry, lang).size} words to gap",
                )
            }
        }
    }

    private fun forEachRow(action: (lang: String, entry: AlphabetEntry) -> Unit) {
        assertTrue(languages.isNotEmpty(), "no alphabets loaded")
        for (lang in languages) {
            for (entry in catalog.alphabet(lang)!!.entries) {
                if (entry.kind == AlphabetKind.Letter || entry.kind == AlphabetKind.Rule) continue
                action(lang, entry)
            }
        }
    }
}
