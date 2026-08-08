package net.spross.kern.catalog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Permanent lint over the REAL `catalog/languages/`, beside [CatalogLintTest] the way
 * [CatalogFrameLintTest] and [AlphabetLintTest] sit beside it. Parse-shape rules (unknown
 * keys, undeclared codes, blank fields) hard-fail the load and need no test of their own;
 * what only content can break is TOTALITY — a pair whose table lacks the other language
 * silently loses every sentence that names it.
 */
class CatalogLanguageNamesLintTest {
    private val catalog get() = RealCatalog.catalog

    private fun forEachName(action: (reader: String, named: String, name: LanguageName) -> Unit) {
        for ((reader, table) in catalog.languageNames) {
            for ((named, name) in table) action(reader, named, name)
        }
    }

    /**
     * File presence is the registry and the loader reads `languages/<lang>.json` for
     * DECLARED languages only — a stray file would otherwise sit unread until someone
     * wonders why the phrases dropped out of that pair.
     */
    @Test
    fun everyTableFileBelongsToADeclaredLanguageAndLoads() {
        val files = File(RealCatalog.root, "languages").listFiles().orEmpty().map { it.name }
        assertTrue(files.isNotEmpty(), "no language-name tables under catalog/languages/")
        for (name in files) {
            assertTrue(name.endsWith(".json"), "languages/$name: not a .json file")
            val lang = name.removeSuffix(".json")
            assertTrue(lang in catalog.languages, "languages/$name: undeclared language")
            assertNotNull(catalog.languageNames[lang], "languages/$name: declared but never loaded")
        }
    }

    /**
     * Every declared language names every declared language, ITSELF included: a de→sw
     * learner reads "Suaheli" on the German side and "Kiswahili" on the Swahili one, so
     * the self entry is as load-bearing as the foreign ones.
     *
     * Containment, not equality: the table also carries every language the country atlas
     * knows, which reaches far past the five the app teaches from.
     */
    @Test
    fun everyLanguageNamesEveryLanguageIncludingItself() {
        val declared = catalog.languages.keys
        for (reader in declared) {
            val table = catalog.languageNames[reader]
            assertNotNull(table, "languages/$reader.json: missing")
            assertEquals(
                emptySet(),
                declared - table.keys,
                "languages/$reader.json: incomplete table",
            )
        }
    }

    /**
     * The atlas half of the same totality: the drill names a language in BOTH directions,
     * so a code the manifest lists and one table misses is a row that silently leaves the
     * drill for every pair that reader is on.
     */
    @Test
    fun everyLanguageTheAtlasKnowsIsNamedByEveryReader() {
        val atlas = catalog.countryAtlas?.languages?.map { it.code }.orEmpty().toSet()
        assertTrue(atlas.isNotEmpty(), "no country atlas — the drill series shipped one")
        for (reader in catalog.languages.keys) {
            val table = catalog.languageNames[reader].orEmpty()
            assertEquals(
                emptySet(),
                atlas - table.keys,
                "languages/$reader.json: atlas languages unnamed",
            )
        }
    }

    /** Roles, not renderings: a blank or untrimmed form would land mid-sentence as it stands. */
    @Test
    fun everyFormIsTrimmedAndNonBlank() {
        forEachName { reader, named, name ->
            val where = "languages/$reader.json $named"
            val forms = listOfNotNull(name.name, name.inForm, name.speak, name.learn) + name.variants
            for (form in forms) {
                assertTrue(form.isNotBlank() && form.trim() == form, "$where: untrimmed \"$form\"")
                assertTrue('|' !in form && '\n' !in form, "$where: bad char in \"$form\"")
            }
            assertTrue(name.name !in name.variants, "$where: variant repeats the name")
            assertEquals(name.variants.toSet().size, name.variants.size, "$where: duplicate variants")
        }
    }

    /** No fallback exists for the adverbial: a missing `in` would drop the "how do you say" phrase. */
    @Test
    fun everyEntryCarriesTheInForm() {
        forEachName { reader, named, name ->
            assertTrue(name.inForm.isNotBlank(), "languages/$reader.json $named: no \"in\" form")
        }
    }

    @Test
    fun notesAreKeyedByDeclaredReaders() {
        forEachName { reader, named, name ->
            assertTrue(
                name.notes.keys.all { it in catalog.languages },
                "languages/$reader.json $named: unknown note key ${name.notes.keys}",
            )
        }
    }
}
