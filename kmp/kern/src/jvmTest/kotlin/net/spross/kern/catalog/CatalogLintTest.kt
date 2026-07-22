package net.spross.kern.catalog

import net.spross.kern.model.CardKind
import net.spross.kern.model.nfcNormalized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Permanent lint over the REAL catalog: every §8 content rule. Structural rules
 * (shape, unknown keys, reference resolution, slug uniqueness, orphan realizations)
 * are enforced by the parser itself — [catalogParsesClean] locks those in.
 */
class CatalogLintTest {
    private val catalog get() = RealCatalog.catalog
    private val slugPattern = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    private fun forEachRealization(action: (area: String, lang: String, slug: String, raw: RawRealization) -> Unit) {
        for (area in catalog.areas) {
            for ((lang, words) in area.realizations) {
                for ((slug, raw) in words) action(area.name, lang, slug, raw)
            }
        }
    }

    @Test
    fun catalogParsesClean() {
        assertEquals(12, catalog.areaNames.size)
        assertEquals(setOf("de", "en", "sw", "uk"), catalog.languages.keys)
        assertTrue(catalog.groups.isNotEmpty())
    }

    @Test
    fun slugAndAreaCharset() {
        for (area in catalog.areas) {
            assertTrue(slugPattern.matches(area.name), "bad area name: ${area.name}")
            for (concept in area.concepts) {
                assertTrue(slugPattern.matches(concept.slug), "bad slug: ${concept.id}")
            }
        }
    }

    @Test
    fun seedIndexUniqueAndStrictlyIncreasing() {
        val indices = catalog.areas.flatMap { it.concepts }.map { it.seedIndex }
        assertEquals(indices.sorted(), indices)
        assertEquals(indices.toSet().size, indices.size)
    }

    @Test
    fun wordsPrecedePhrasesWithinEachArea() {
        for (area in catalog.areas) {
            val firstPhrase = area.concepts.indexOfFirst { it.kind == CardKind.Phrase }
            if (firstPhrase < 0) continue
            val straggler = area.concepts.drop(firstPhrase).firstOrNull { it.kind != CardKind.Phrase }
            assertTrue(straggler == null, "${area.name}: word after phrase (${straggler?.slug})")
        }
    }

    @Test
    fun textAndAlternatesAreClean() {
        forEachRealization { area, lang, slug, raw ->
            val where = "$area/$lang.json $slug"
            val all = listOf(raw.text) + raw.synonyms + raw.variants
            for (entry in all) {
                assertTrue(entry.isNotBlank(), "$where: blank entry")
                assertTrue(entry.trim() == entry, "$where: untrimmed \"$entry\"")
                assertTrue(" / " !in entry, "$where: slash-joined \"$entry\"")
                assertTrue('|' !in entry && '\n' !in entry, "$where: bad char in \"$entry\"")
            }
            val alternates = raw.synonyms + raw.variants
            assertTrue(alternates.toSet().size == alternates.size, "$where: duplicate alternates")
            assertTrue(raw.text !in alternates, "$where: alternate equals text")
        }
    }

    // Rotation prompts cycle through text + synonyms — forms must stay distinct
    // under NFC (composed vs decomposed spellings of the same word would collide).
    @Test
    fun rotationFormsDistinctPerRealization() {
        forEachRealization { area, lang, slug, raw ->
            val forms = (listOf(raw.text) + raw.synonyms).map { nfcNormalized(it).trim() }
            assertEquals(forms.toSet().size, forms.size, "$area/$lang.json $slug: colliding forms")
        }
    }

    @Test
    fun emojiWellFormed() {
        for (area in catalog.areas) {
            for (concept in area.concepts) {
                val emoji = concept.emoji ?: continue
                assertTrue(emoji.isNotBlank(), "${concept.id}: blank emoji")
                assertTrue(emoji.length <= 12, "${concept.id}: oversized emoji \"$emoji\"")
                assertTrue(
                    emoji.all { it.code >= 0x2000 },
                    "${concept.id}: non-emoji character in \"$emoji\"",
                )
            }
        }
    }

    @Test
    fun conceptReferencesResolveSameArea() {
        for (area in catalog.areas) {
            for (concept in area.concepts) {
                for (component in concept.components) {
                    val target = area.conceptsBySlug[component]
                    assertTrue(target != null && target.kind != CardKind.Phrase, "${concept.id}: bad component $component")
                }
                concept.feminineOf?.let {
                    assertEquals(CardKind.Noun, area.conceptsBySlug[it]?.kind, "${concept.id}: bad feminineOf")
                }
            }
        }
    }

    @Test
    fun feminineConceptsAlwaysCarryTheDeForm() {
        for (area in catalog.areas) {
            val de = area.realizations["de"].orEmpty()
            for (concept in area.concepts) {
                if (concept.feminineOf != null) {
                    assertTrue(concept.slug in de, "${concept.id}: feminine without de realization")
                }
            }
        }
    }

    @Test
    fun titlesCoverEveryDeclaredLanguage() {
        val langs = catalog.languages.keys
        for (group in catalog.groups) {
            assertEquals(langs, group.titles.keys, "group ${group.id}: incomplete titles")
        }
        for (area in catalog.areas) {
            assertEquals(langs, area.titles.keys, "area ${area.name}: missing language files/titles")
        }
    }

    @Test
    fun notesKeyedByDeclaredLanguages() {
        forEachRealization { area, lang, slug, raw ->
            assertTrue(
                raw.notes.keys.all { it in catalog.languages },
                "$area/$lang.json $slug: unknown note key ${raw.notes.keys}",
            )
        }
    }

    @Test
    fun grammarValuesAreBareAndTrimmed() {
        forEachRealization { area, lang, slug, raw ->
            for ((key, value) in raw.grammar) {
                val where = "$area/$lang.json $slug.$key"
                assertTrue(value.isNotBlank() && value.trim() == value, "$where: bad value \"$value\"")
                assertTrue(!value.startsWith("Pl."), "$where: labeled value \"$value\"")
            }
        }
    }
}
