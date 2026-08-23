package net.spross.kern.catalog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Permanent lint over the REAL `catalog/countries/`, beside [AlphabetLintTest] and
 * [CatalogLanguageNamesLintTest]. Parse-shape rules (unknown keys, slug/code shape,
 * duplicate rows, tiers outside 2..4, a country naming an undeclared language) hard-fail
 * the load and need no test of their own — [RealCatalog.catalog] parses every shipped file.
 *
 * What is left here is what only CONTENT can break: totality across the five realization
 * files, the language table the drill grades against, and the disjointness that keeps a
 * country slug from ever colliding with a concept or a frame.
 */
class CountryAtlasLintTest {
    private val catalog get() = RealCatalog.catalog
    private val atlas get() = assertNotNull(catalog.countryAtlas, "no catalog/countries/atlas.json")

    private fun forEachRealization(action: (lang: String, slug: String, name: CountryName) -> Unit) {
        for ((lang, names) in catalog.countryNames) {
            for ((slug, name) in names) action(lang, slug, name)
        }
    }

    /** File presence is the registry: a stray file would sit unread until someone wondered. */
    @Test
    fun everyCountryFileBelongsToADeclaredLanguageAndLoads() {
        val files = File(RealCatalog.root, "countries").listFiles().orEmpty().map { it.name }
        assertTrue(files.isNotEmpty(), "no country files under catalog/countries/")
        for (name in files.filter { it != "atlas.json" }) {
            assertTrue(name.endsWith(".json"), "countries/$name: not a .json file")
            val lang = name.removeSuffix(".json")
            assertTrue(lang in catalog.languages, "countries/$name: undeclared language")
            assertNotNull(catalog.countryNames[lang], "countries/$name: declared but never loaded")
        }
    }

    /**
     * The drill asks in one language and grades in another, so a country ONE file misses is
     * a row that vanishes from every pair that file is on — the failure totality exists to
     * make loud.
     */
    @Test
    fun everyCountryIsRealizedInEveryDeclaredLanguage() {
        val slugs = atlas.countries.map { it.slug }.toSet()
        for (lang in catalog.languages.keys) {
            val names = catalog.countryNames[lang]
            assertNotNull(names, "countries/$lang.json: missing")
            assertEquals(emptySet(), slugs - names.keys, "countries/$lang.json: unrealized countries")
        }
    }

    /**
     * The atlas keeps no language names of its own — they come from `catalog/language-names/`,
     * so a manifest code no table names is a language the drill can never ask about.
     */
    @Test
    fun everyManifestLanguageIsNamedByEveryDeclaredLanguage() {
        for (row in atlas.languages) {
            for (reader in catalog.languages.keys) {
                assertNotNull(
                    catalog.languageName(reader, row.code),
                    "language-names/$reader.json: no name for atlas language \"${row.code}\"",
                )
            }
        }
    }

    /** A language nobody speaks anywhere is vocabulary with no country to hang it on. */
    @Test
    fun everyManifestLanguageIsSpokenSomewhere() {
        val spoken = atlas.countries.flatMap { it.languages }.toSet()
        for (row in atlas.languages) {
            assertTrue(row.code in spoken, "atlas.json: \"${row.code}\" is spoken in no listed country")
        }
    }

    /**
     * Rung 1 is the learner's OWN languages, derived from the profile — a drill for a pair
     * the app teaches only opens if both sit in the manifest, at its innermost tier.
     */
    @Test
    fun everyAppLanguageEntersAtTierTwo() {
        val tiers = atlas.languages.associate { it.code to it.tier }
        for (lang in catalog.languages.keys) {
            // Reviewed 2026-08-15: eo has no country, so it has no atlas row at all —
            // a manifest language with no country never surfaces, and every eo pair
            // still opens through the partner language's own tier-1 countries.
            if (lang == "eo") continue
            assertEquals(2, tiers[lang], "atlas.json: app language \"$lang\" is not a tier-2 entry")
        }
    }

    /**
     * Slugs are one flat namespace across the catalog. Nothing joins a country to a concept
     * today, but a collision would make every later lookup ambiguous in a way no error
     * reports — cheaper to forbid than to diagnose.
     */
    @Test
    fun countrySlugsCollideWithNoConceptOrFrame() {
        val concepts = catalog.areas.flatMap { area -> area.concepts.map { it.slug } }.toSet()
        val frames = catalog.frames.map { it.slug }.toSet()
        for (country in atlas.countries) {
            assertTrue(country.slug !in concepts, "atlas.json: \"${country.slug}\" is also a concept slug")
            assertTrue(country.slug !in frames, "atlas.json: \"${country.slug}\" is also a frame slug")
        }
    }

    /** Every row carries the triple, and its flag is the language-neutral half of it. */
    @Test
    fun everyCountryCarriesAFlagAndANationality() {
        for (country in atlas.countries) {
            assertTrue(country.flag.isNotBlank(), "atlas.json: ${country.slug} has no flag")
        }
        forEachRealization { lang, slug, name ->
            assertTrue(
                name.nationality.text.isNotBlank(),
                "countries/$lang.json $slug: no nationality",
            )
        }
    }

    /** Roles, not renderings: an untrimmed form lands on screen exactly as it stands. */
    @Test
    fun everyFormIsTrimmedNonBlankAndNeverEchoesItsText() {
        forEachRealization { lang, slug, name ->
            val where = "countries/$lang.json $slug"
            val groups = listOf(
                "name" to (listOf(name.text) to name.variants),
                "nationality" to (listOf(name.nationality.text) to name.nationality.variants),
            )
            for ((what, forms) in groups) {
                val (texts, variants) = forms
                for (form in texts + variants) {
                    assertTrue(form.isNotBlank() && form.trim() == form, "$where: untrimmed $what \"$form\"")
                    assertTrue('|' !in form && '\n' !in form, "$where: bad char in $what \"$form\"")
                }
                assertTrue(texts.single() !in variants, "$where: $what variant repeats the text")
                assertEquals(variants.toSet().size, variants.size, "$where: duplicate $what variants")
            }
        }
    }

    /** Notes are keyed by whoever is READING, exactly as a realization's are. */
    @Test
    fun notesAreKeyedByDeclaredReaders() {
        forEachRealization { lang, slug, name ->
            assertTrue(
                name.notes.keys.all { it in catalog.languages },
                "countries/$lang.json $slug: unknown note key ${name.notes.keys}",
            )
        }
    }

    /** Every ordered app pair joins something — the drill's own availability question. */
    @Test
    fun everyOrderedPairJoinsAnAtlas() {
        for (source in catalog.languages.keys) {
            for (target in catalog.languages.keys) {
                if (source == target) continue
                val content = catalog.countryDrillContent(source, target)
                assertNotNull(content, "$source→$target: no atlas")
                assertTrue(
                    content.countries.any { it.tier == 1 },
                    "$source→$target: no country carries the profile's own languages",
                )
            }
        }
    }
}
