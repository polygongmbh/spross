package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import net.spross.kern.model.Language

/**
 * The launch rules: which sources may be offered, and what a device gets handed.
 * Catalogs here are generated because coverage turns on the 50-concept threshold —
 * the shared [Fixture] is deliberately below it.
 */
class CoveredSourcesTest {

    @Test
    fun coveredSourcesAreSortedAndExcludeLanguagesWithoutATarget() {
        // fr is declared but realizes nothing, so nothing joins from or to it.
        val catalog = catalog(declared = listOf("fr", "en", "de"), realized = listOf("de", "en"))
        assertEquals(listOf("de", "en"), catalog.coveredSources())
    }

    @Test
    fun coveredSourceExcludesALanguageThatIsTooThinToLearnFrom() {
        // sw realizes only 10 concepts: it joins, but under the 50-concept bar.
        val catalog = catalog(
            declared = listOf("de", "en", "sw"),
            realized = listOf("de", "en"),
            partial = mapOf("sw" to 10),
        )
        assertEquals(listOf("de", "en"), catalog.coveredSources())
        assertTrue(catalog.availableTargets("sw").isEmpty())
    }

    @Test
    fun deviceLanguageIsKeptWhenTheCatalogTeachesFromIt() {
        val catalog = catalog(declared = listOf("de", "en"), realized = listOf("de", "en"))
        assertEquals("de", catalog.defaultSource("de"))
    }

    @Test
    fun undeclaredDeviceLanguageFallsToEnglishInsteadOfThrowing() {
        val catalog = catalog(declared = listOf("de", "en"), realized = listOf("de", "en"))
        assertEquals("en", catalog.defaultSource("it"))
        assertEquals("en", catalog.defaultSource(""))
    }

    /** A declared language the catalog cannot teach from is no better than an unknown one. */
    @Test
    fun uncoveredDeclaredDeviceLanguageFallsToEnglish() {
        val catalog = catalog(declared = listOf("de", "en", "fr"), realized = listOf("de", "en"))
        assertEquals("en", catalog.defaultSource("fr"))
    }

    @Test
    fun withoutEnglishTheDefaultIsStillASourceWithTargets() {
        val catalog = catalog(declared = listOf("uk", "de", "sw"), realized = listOf("de", "sw"))
        assertEquals("de", catalog.defaultSource("it"))
        assertTrue(catalog.availableTargets(catalog.defaultSource("it")).isNotEmpty())
    }

    /** The strict query stays strict: an undeclared source is a caller bug, not an empty answer. */
    @Test
    fun availableTargetsStillRejectsAnUndeclaredSource() {
        val catalog = catalog(declared = listOf("de", "en"), realized = listOf("de", "en"))
        assertFailsWith<IllegalArgumentException> { catalog.availableTargets("it") }
    }

    private companion object {
        const val CONCEPTS = 60

        /**
         * A one-area catalog: [declared] languages in `languages.json` order, each of
         * [realized] carrying all [CONCEPTS] words, each of [partial] carrying that many.
         */
        fun catalog(
            declared: List<Language>,
            realized: List<Language>,
            partial: Map<Language, Int> = emptyMap(),
        ): Catalog {
            val languages = declared.joinToString(",", "{", "}") { code ->
                """"$code": { "name": "$code", "englishName": "$code", "flag": "🇺🇳" }"""
            }
            val files = mutableMapOf(
                "areas.json" to """[{ "group": "g", "titles": {}, "areas": [{ "area": "core", "emoji": "📦" }] }]""",
                "languages.json" to languages,
                "core/concepts.json" to (0 until CONCEPTS).joinToString(",", "[", "]") {
                    """{ "slug": "w$it", "kind": "noun" }"""
                },
            )
            for ((code, count) in realized.associateWith { CONCEPTS } + partial) {
                files["core/$code.json"] = (0 until count).joinToString(
                    separator = ",",
                    prefix = """{ "title": "Core", "words": {""",
                    postfix = "} }",
                ) { """"w$it": { "text": "$code-w$it" }""" }
            }
            return Catalog.load(MapCatalogSource(files))
        }
    }
}
