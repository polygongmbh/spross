package net.spross.kern.catalog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The alphabet rules only the REAL catalog can break — everything here is content lint
 * over the shipped `alphabet/` files (`kern/docs/catalog.md`). Parse-shape rules (unknown
 * keys, ipa-or-hint, reader keys ⊆ declared, ref resolution, id-on-duplicate-glyph) need
 * no test of their own: [RealCatalog.catalog] parses every shipped file, so a violation
 * fails every jvmTest that touches the catalog. `AlphabetFixtureTest` pins those rules on
 * synthetic JSON.
 *
 * NB `letters{}.matches == entry.name` (D2 for letters) is WAIVED: audio-infra's manifest
 * schema rejects `matches` on letter entries, so the name↔recording match stays a manual
 * listen-check (backlog).
 */
class AlphabetLintTest {
    private val catalog get() = RealCatalog.catalog

    /**
     * File presence is the registry, and the loader reads `alphabet/<lang>.json` only for
     * DECLARED languages — a stray file (an es.json landing before its languages.json
     * entry) would otherwise sit silently unread until someone wonders where the sheet is.
     */
    @Test
    fun everyAlphabetFileBelongsToADeclaredLanguageAndLoads() {
        val files = File(RealCatalog.root, "alphabet").listFiles().orEmpty().map { it.name }
        assertTrue(files.isNotEmpty(), "no alphabet files — the drill series shipped uk and de")
        for (name in files) {
            assertTrue(name.endsWith(".json"), "alphabet/$name: not a .json file")
            val lang = name.removeSuffix(".json")
            assertTrue(lang in catalog.languages, "alphabet/$name: undeclared language")
            assertNotNull(catalog.alphabet(lang), "alphabet/$name: declared but never loaded")
        }
    }

    /**
     * Two rules in one predicate: the slug must name a concept somewhere in the
     * catalog AND the alphabet's OWN language must realize it — otherwise the example is
     * dead for every reader. (No source language is required to realize it; the meaning
     * line degrades per reader, and that is the designed behavior.)
     */
    @Test
    fun everyExampleSlugIsRealizedByTheAlphabetsOwnLanguage() {
        forEachEntry { lang, entry ->
            val slug = entry.exampleSlug ?: return@forEachEntry
            assertNotNull(
                catalog.alphabetExample(entry, lang),
                "alphabet/$lang ${entry.ref}: \"$slug\" is not a concept $lang realizes",
            )
        }
    }

    /** A drill-true letter row without a name has nothing for a synthesizer to speak. */
    @Test
    fun everyDrillableLetterRowCarriesAName() {
        forEachEntry { lang, entry ->
            if (entry.drill && entry.kind == AlphabetKind.Letter) {
                assertNotNull(entry.name, "alphabet/$lang ${entry.ref}: drill-true letter without a name")
            }
        }
    }

    /**
     * The gap-row rules: the RESOLVED example (own-language realization, else
     * `exampleText`) exists and contains the glyph EXACTLY once. Zero leaves nothing to
     * blank; two or more lets first-occurrence blanking gap the wrong, position-bound
     * instance and teach the opposite of the entry. Kern filters the same predicate
     * defensively, so a violation here costs a pool entry — this lint is what makes it a
     * loud authoring error instead.
     */
    @Test
    fun everyDrillableGapRowCutsExactlyOneGap() {
        forEachEntry { lang, entry ->
            if (!entry.drill || entry.kind == AlphabetKind.Letter || entry.kind == AlphabetKind.Rule) {
                return@forEachEntry
            }
            val resolved = catalog.alphabetExample(entry, lang)?.text ?: entry.exampleText
            assertNotNull(resolved, "alphabet/$lang ${entry.ref}: gap row without an example")
            assertEquals(
                1,
                glyphOccurrences(resolved, entry.glyph),
                "alphabet/$lang ${entry.ref}: \"${entry.glyph}\" must occur exactly once in \"$resolved\"",
            )
            assertNotNull(entry.gapWord(resolved), "alphabet/$lang ${entry.ref}: no gap word derivable")
        }
    }

    /**
     * The letters-manifest collision rule: a `letters{}` key is a lowercase glyph, so it
     * can address an alphabet row only while exactly ONE row it could ever be played for
     * carries that glyph. The reachable rows are the NAMED ones — a letter recording is
     * only ever asked for through a row's `name` (the sheet's speaker, the drill's `Name` prompt), and a
     * nameless row is Word-prompted and never reaches the letters manifest at all. So de
     * `ch`×3 (none named) and the nameless `v-loan` beside the named `v-f` are no
     * ambiguity, while two NAMED rows on one glyph would be: the recording could not say
     * which of them it speaks.
     */
    @Test
    fun everyLetterRecordingAddressesExactlyOneNamedAlphabetRow() {
        for ((lang, manifest) in catalog.audio) {
            if (manifest.letters.isEmpty()) continue
            val alphabet = assertNotNull(
                catalog.alphabet(lang),
                "audio/$lang ships letter recordings but no alphabet is authored",
            )
            for (glyph in manifest.letters.keys) {
                val rows = alphabet.entries
                    .filter { it.name != null && it.glyph.lowercase() == glyph.lowercase() }
                assertEquals(
                    1,
                    rows.size,
                    "audio/$lang letter \"$glyph\": matches ${rows.size} NAMED alphabet rows",
                )
            }
        }
    }

    private fun forEachEntry(action: (lang: String, entry: AlphabetEntry) -> Unit) {
        val languages = catalog.languages.keys.filter { catalog.alphabet(it) != null }
        assertTrue(languages.isNotEmpty(), "no alphabets loaded")
        for (lang in languages) {
            for (entry in catalog.alphabet(lang)!!.entries) action(lang, entry)
        }
    }
}
