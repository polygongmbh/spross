package net.spross.kern.catalog

import net.spross.kern.model.CardKind
import net.spross.kern.model.nfcNormalized
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Permanent lint over the REAL catalog: every §8 content rule. Structural rules
 * (shape, unknown keys, reference resolution, slug uniqueness, orphan realizations)
 * are enforced by the parser itself — [catalogParsesClean] locks those in.
 * The §11 audio rules live beside this in [CatalogAudioLintTest].
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
        assertEquals(setOf("de", "en", "es", "sw", "uk"), catalog.languages.keys)
        assertTrue(catalog.groups.isNotEmpty())
        assertTrue(catalog.areaNames.isNotEmpty())
    }

    /**
     * Areas are enumerated from `areas.json` ALONE, so a folder that exists but is not
     * listed there is silently ignored — the one catalog mistake the parser cannot catch
     * (the reverse, a listed area with no files, already hard-fails on the missing read).
     * Relational on purpose: a pinned area count would only measure how recently someone
     * bumped it, and adding content must never require editing a number in this file.
     */
    @Test
    fun everyAreaFolderIsRegisteredInTheManifest() {
        val onDisk = RealCatalog.root.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "concepts.json").isFile }
            .map { it.name }
            .toSortedSet()
        assertTrue(onDisk.isNotEmpty(), "no area folders found under ${RealCatalog.root}")
        assertEquals(onDisk, catalog.areaNames.toSortedSet())
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

    /**
     * The slug IS the card id, so two areas sharing one would fuse two concepts into a
     * single FSRS schedule. The parser only guarantees uniqueness within an area; this is
     * the global upgrade, and it is what lets a concept move between areas without
     * resetting progress (`catalog/README.md`).
     */
    @Test
    fun slugsAreGloballyUnique() {
        val areasBySlug = mutableMapOf<String, MutableList<String>>()
        for (area in catalog.areas) {
            for (concept in area.concepts) areasBySlug.getOrPut(concept.slug) { mutableListOf() } += area.name
        }
        for ((slug, areas) in areasBySlug) {
            assertEquals(1, areas.size, "slug \"$slug\" is claimed by ${areas.sorted()}")
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

    /**
     * The area icon is catalog-owned display metadata, and the apps fall back to a
     * placeholder for an area they cannot resolve — so a manifest entry missing its emoji
     * would degrade silently in the UI instead of failing anywhere. Relational, never a
     * count: every area the manifest lists must carry a well-formed emoji, by construction.
     */
    @Test
    fun everyAreaHasAWellFormedEmoji() {
        for (area in catalog.areaNames) {
            val emoji = catalog.areaEmoji(area)
            assertTrue(emoji != null, "$area: no emoji in areas.json")
            assertTrue(emoji.isNotBlank(), "$area: blank emoji")
            assertTrue(emoji.length <= 12, "$area: oversized emoji \"$emoji\"")
            assertTrue(emoji.all { it.code >= 0x2000 }, "$area: non-emoji character in \"$emoji\"")
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

    /**
     * A subtitle is flavour, so an area may carry none — but half a set is worse than
     * none: the clause would stand in one reader's box and be a hole in the next. It is
     * also not the title again, in any form, and never the `·`-glued tail it replaced,
     * which is the shape the whole field exists to retire.
     */
    @Test
    fun subtitlesAreCompletePerAreaAndDistinctFromTheTitle() {
        for (area in catalog.areas) {
            if (area.subtitles.isEmpty()) continue
            assertEquals(area.titles.keys, area.subtitles.keys, "area ${area.name}: partial subtitles")
            for ((lang, subtitle) in area.subtitles) {
                val where = "${area.name}/$lang.json subtitle"
                assertTrue(subtitle.isNotBlank() && subtitle.trim() == subtitle, "$where: untrimmed \"$subtitle\"")
                assertTrue("·" !in subtitle, "$where: carries a \"·\" tail")
                val title = area.titles.getValue(lang)
                assertTrue(title !in subtitle, "$where: repeats the title \"$title\"")
            }
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
                // Reviewed 2026-07-31: es merges what de distinguishes by capitalization
                // alone (der Morgen / morgen); en/sw/uk all keep the two apart, so no
                // concept pair collides twice. The area disambiguates the produce prompt.
                "es mañana: bedroom/morning, essentials/tomorrow",
                // Reviewed 2026-07-31: `el tiempo` is both Zeit and Wetter. de/en/sw/uk
                // all split it; `clima` is das Klima in Spain, so there is no alternative.
                "es tiempo: essentials/time, nature/weather",
                // Reviewed 2026-07-25: the textbook homonym, and the only entry here that
                // is NOT a merge — sw `mto` is two unrelated senses (river, pillow),
                // not one word covering two German ones. Same treatment either way.
                // Re-pathed 2026-08-04: `outside` split into transport/city/nature and
                // `river` landed in `nature` — the same pair, renamed, not a new one.
                "sw mto: bedroom/pillow, nature/river",
                // Reviewed 2026-08-04: `ndege` is the only Swahili word for both bird and
                // aeroplane; de/en/es/uk all split them. The plane carries a de note so the
                // learner meets the second sense as a fact, not as a surprise.
                "sw ndege: nature/bird, transport/plane",
                // Reviewed 2026-08-04: `nyanya` is the ordinary word for grandmother and for
                // tomato alike, both of them the first word a learner needs in their area.
                // Repicking either would teach the rarer word for no gain.
                "sw nyanya: food/tomato, people/grandmother",
            ),
            actual,
        )
    }

    /**
     * Plural articles, per language that authors `gender` — not derivable, since German's
     * is homographic with the feminine singular and no shipped noun tells the uses apart.
     */
    private val pluralArticles = mapOf("de" to setOf("die"), "es" to setOf("los", "las"))

    /**
     * Bare values, no labels — plus the closed domain `gender` carries. It IS the article
     * the learner says, so it must be one the language declares, and on a `plural: "only"`
     * noun it must be the plural one: grading reads the value back and demotes an answer
     * whose PRESENT leading article disagrees, so a singular `el` on *auriculares* marks
     * the only right answer, `los auriculares`, a typo. That is what makes es
     * el/la/los/las the same rule as de's der/die/das rather than a de-shaped exception.
     */
    @Test
    fun grammarValuesAreWellFormed() {
        forEachRealization { area, lang, slug, raw ->
            for ((key, value) in raw.grammar) {
                val where = "$area/$lang.json $slug.$key"
                assertTrue(value.isNotBlank() && value.trim() == value, "$where: bad value \"$value\"")
                assertTrue(!value.startsWith("Pl."), "$where: labeled value \"$value\"")
            }
            val gender = raw.grammar["gender"] ?: return@forEachRealization
            val where = "$area/$lang.json $slug.gender"
            assertTrue(gender in catalog.languages.getValue(lang).articles, "$where: no declared $lang article")
            if (raw.grammar["plural"] == "only") {
                assertTrue(gender in pluralArticles[lang].orEmpty(), "$where: \"$gender\" is not plural")
            }
        }
    }
}
