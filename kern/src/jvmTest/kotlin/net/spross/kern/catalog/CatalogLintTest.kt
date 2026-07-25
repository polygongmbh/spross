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
        assertEquals(13, catalog.areaNames.size)
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
    fun languageMetadataWellFormed() {
        for ((code, info) in catalog.languages) {
            assertTrue(info.englishName.isNotBlank(), "$code: blank englishName")
            val codePoints = info.flag.codePoints().toArray()
            assertEquals(2, codePoints.size, "$code: flag \"${info.flag}\" is not one flag sequence")
            assertTrue(
                codePoints.all { it in 0x1F1E6..0x1F1FF },
                "$code: flag \"${info.flag}\" contains a non-regional-indicator",
            )
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

    /**
     * Prompt forms as the learner SEES them — text plus synonyms (both rotate as prompts),
     * NFC-folded. Case-SENSITIVE on purpose: `Husten`/`husten` and `jua`/`kujua` are real
     * visual distinctions that keep noun/verb homographs unambiguous.
     */
    private fun promptForms(raw: RawRealization): List<String> =
        (listOf(raw.text) + raw.synonyms).map { nfcNormalized(it).trim() }

    /** (lang, form) → concept ids sharing it, keeping only the genuine collisions. */
    private fun collisionClusters(): Map<Pair<String, String>, List<String>> {
        val byForm = mutableMapOf<Pair<String, String>, MutableList<String>>()
        forEachRealization { area, lang, slug, raw ->
            for (form in promptForms(raw)) {
                byForm.getOrPut(lang to form) { mutableListOf() } += "$area/$slug"
            }
        }
        return byForm.filterValues { it.size > 1 }
    }

    /**
     * A display-identical prompt INSIDE one area is unfixable at runtime: the engine's
     * disambiguator is the area label, which would be identical. Repick the word.
     */
    @Test
    fun noPromptCollisionWithinAnArea() {
        for ((key, ids) in collisionClusters()) {
            val (lang, form) = key
            val perArea = ids.groupBy { it.substringBefore('/') }.filterValues { it.size > 1 }
            assertTrue(perArea.isEmpty(), "$lang \"$form\": same-area collision ${perArea.values}")
        }
    }

    /**
     * One concept PAIR colliding in two languages is one meaning authored twice — unify it
     * (the `variantOf` ruling). Exception, when this fires: if de/en genuinely distinguish
     * the two and only both targets merge them, fix the imprecise realization instead
     * (uk relax/rest was `відпочивати` twice while de keeps entspannen/ausruhen apart).
     */
    @Test
    fun noConceptPairCollidesInTwoLanguages() {
        val langsByPair = mutableMapOf<Pair<String, String>, MutableSet<String>>()
        for ((key, ids) in collisionClusters()) {
            val sorted = ids.sorted()
            for (i in sorted.indices) {
                for (j in i + 1 until sorted.size) {
                    langsByPair.getOrPut(sorted[i] to sorted[j]) { mutableSetOf() } += key.first
                }
            }
        }
        val duplicated = langsByPair.filterValues { it.size > 1 }
        assertTrue(duplicated.isEmpty(), "same meaning authored twice: $duplicated")
    }

    /**
     * Cross-area, single-language collisions are legitimate target-language merges (Swahili
     * has one word where German has two) — tolerated at runtime, where the join sets
     * `promptAmbiguous` and the UI adds the area label to the produce prompt. Pinned so a
     * NEW one (adding `outside/river` beside `bedroom/pillow`, both sw `mto`) fails here
     * instead of silently shipping an unanswerable prompt.
     */
    @Test
    fun crossAreaPromptCollisionsAreKnown() {
        val actual = collisionClusters()
            .map { (key, ids) -> "${key.first} ${key.second}: ${ids.sorted().joinToString(", ")}" }
            .toSortedSet()
        assertEquals(
            sortedSetOf(
                "sw daftari: desk/notebook, school/exercise-book",
                "sw kuchukua: essentials/take, health/pick-up",
                "sw kuondoka: hall/set-off, outside/depart",
                "sw kupumzika: desk/take-break, health/rest, living/relax",
                "sw kuvaa: bedroom/get-dressed, hall/put-on",
            ),
            actual,
        )
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
