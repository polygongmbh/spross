package net.spross.kern.catalog

import net.spross.kern.model.CardKind
import net.spross.kern.model.nfcNormalized
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Permanent lint over the REAL catalog: every content rule (`kern/docs/catalog.md`).
 * Structural rules (shape, unknown keys, reference resolution, slug uniqueness,
 * orphan realizations) are enforced by the parser itself — [catalogParsesClean] locks those in.
 * The audio rules (`kern/docs/audio.md`) live beside this in [CatalogAudioLintTest].
 */
class CatalogLintTest {
    private val catalog get() = RealCatalog.catalog
    private val areasRoot get() = File(RealCatalog.root, "areas")
    private val slugPattern = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    /** The one word each language adds to soften a request — see [alternatesDoNotAddOrDropPolitenessParticles]. */
    private val politenessParticle = mapOf(
        "de" to Regex("\\bbitte\\b", RegexOption.IGNORE_CASE),
        "en" to Regex("\\bplease\\b", RegexOption.IGNORE_CASE),
        "eo" to Regex("\\bbonvolu\\b|\\bmi petas\\b", RegexOption.IGNORE_CASE),
        "es" to Regex("\\bpor favor\\b", RegexOption.IGNORE_CASE),
        "fr" to Regex("\\bs[’']il (te|vous) plaît\\b", RegexOption.IGNORE_CASE),
        "it" to Regex("\\bper (favore|piacere|cortesia)\\b", RegexOption.IGNORE_CASE),
        "sw" to Regex("\\btafadhali\\b", RegexOption.IGNORE_CASE),
        "uk" to Regex("\\bбудь ласка\\b", RegexOption.IGNORE_CASE),
    )

    private fun forEachRealization(action: (area: String, lang: String, slug: String, raw: RawRealization) -> Unit) {
        for (area in catalog.areas) {
            for ((lang, words) in area.realizations) {
                for ((slug, raw) in words) action(area.name, lang, slug, raw)
            }
        }
    }

    @Test
    fun catalogParsesClean() {
        assertEquals(setOf("de", "en", "eo", "es", "fr", "it", "sw", "uk"), catalog.languages.keys)
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
        val onDisk = areasRoot.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "concepts.json").isFile }
            .map { it.name }
            .toSortedSet()
        assertTrue(onDisk.isNotEmpty(), "no area folders found under $areasRoot")
        assertEquals(onDisk, catalog.areaNames.toSortedSet())
    }

    /**
     * The other half of the layout rule: `areas/` holds the card areas and nothing else
     * does, so a `concepts.json` anywhere but there is an area the loader will never read
     * (`catalog/README.md`). Everything outside is a registry with its own reader.
     */
    @Test
    fun noConceptsFileLivesOutsideAreas() {
        val stray = RealCatalog.root.walkTopDown()
            .onEnter { it.name != "audio" }
            .filter { it.name == "concepts.json" && it.parentFile.parentFile != areasRoot }
            .map { it.relativeTo(RealCatalog.root).path }
            .toSortedSet()
        assertEquals(emptySet(), stray, "concepts.json outside catalog/areas/")
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
    fun wordsPrecedeTheirPhrasesWithinEachArea() {
        for (area in catalog.areas) {
            val firstBuilt = area.concepts.indexOfFirst {
                it.kind == CardKind.Phrase && it.components.isNotEmpty()
            }
            if (firstBuilt < 0) continue
            val straggler = area.concepts.drop(firstBuilt).firstOrNull { it.kind != CardKind.Phrase }
            assertTrue(straggler == null, "${area.name}: word after built phrase (${straggler?.slug})")
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

    /**
     * A register variant swaps the address form and nothing else: a du-form that also
     * gains a "bitte" the Sie-form never had is a second sentence, and the slug
     * (`can-you-repeat-that`, no "please") then names neither of them. Parity in both
     * directions, because the drift went that way in five German entries at once.
     */
    @Test
    fun alternatesDoNotAddOrDropPolitenessParticles() {
        forEachRealization { area, lang, slug, raw ->
            val particle = politenessParticle[lang] ?: return@forEachRealization
            val inText = particle.containsMatchIn(raw.text)
            for (alternate in raw.synonyms + raw.variants) {
                assertEquals(
                    inText,
                    particle.containsMatchIn(alternate),
                    "$area/$lang.json $slug: \"$alternate\" disagrees with \"${raw.text}\" on the politeness particle",
                )
            }
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
            if (code == "eo") {
                // Esperanto has no country, so no flag emoji exists; the community's badge
                // is the green heart — the one language allowed a single non-RIS emoji.
                assertEquals(listOf(0x1F49A), codePoints.toList(), "eo: badge must be the green heart")
                continue
            }
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
     * A subtitle is flavor, so an area may carry none — but half a set is worse than
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

    /**
     * `{language…}` resolves in realization and frame forms and NOWHERE else: one in a note,
     * a grammar value, a heading or a name table would ship to the learner verbatim. Where it
     * does resolve, it has to be one of the four forms, exactly once, and never string-initial
     * — `markerError` is the same predicate the parser applies, pinned here over every file.
     */
    @Test
    fun languageMarkersOnlyAppearWhereTheyResolve() {
        fun unmarked(where: String, text: String) =
            assertTrue(!LanguageNames.hasLanguageMarker(text), "$where: language marker in \"$text\"")

        for (area in catalog.areas) {
            for ((lang, title) in area.titles) unmarked("${area.name}/$lang.json title", title)
            for ((lang, subtitle) in area.subtitles) unmarked("${area.name}/$lang.json subtitle", subtitle)
        }
        forEachRealization { area, lang, slug, raw ->
            val where = "$area/$lang.json $slug"
            for (form in listOf(raw.text) + raw.synonyms + raw.variants) {
                assertTrue(LanguageNames.markerError(form) == null, "$where: ${LanguageNames.markerError(form)}")
            }
            for ((key, value) in raw.grammar) unmarked("$where.$key", value)
            for ((reader, note) in raw.notes) unmarked("$where.notes.$reader", note)
        }
        for ((lang, frames) in catalog.frameRealizations) {
            for ((slug, frame) in frames) {
                val where = "drills/$lang.json $slug"
                for (form in listOf(frame.text) + frame.variants) {
                    assertTrue(LanguageNames.markerError(form) == null, "$where: ${LanguageNames.markerError(form)}")
                }
                for ((reader, note) in frame.notes) unmarked("$where.notes.$reader", note)
                frame.count?.let { unmarked("$where.count", "${it.one} ${it.few} ${it.many}") }
            }
        }
        for ((reader, table) in catalog.languageNames) {
            for ((named, name) in table) {
                val where = "language-names/$reader.json $named"
                for (form in listOf(name.name, name.inForm) + name.variants) unmarked(where, form)
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
        // Reviewed 2026-08-15: weather/time is the Romance family merge (es tiempo,
        // fr temps, it tempo) — one linguistic fact per language, each pinned in
        // [crossAreaPromptCollisionsAreKnown], not the same meaning authored twice.
        // Re-realizing weather as meteo/météo would teach the forecast, not the weather.
        val reviewedPairs = mapOf(
            ("nature/weather" to "time/time") to setOf("es", "fr", "it"),
        )
        val duplicated = langsByPair
            .mapValues { (pair, langs) -> langs - reviewedPairs[pair].orEmpty() }
            .filterValues { it.size > 1 }
        assertTrue(duplicated.isEmpty(), "same meaning authored twice: $duplicated")
    }

    /**
     * Cross-area, single-language collisions are legitimate target-language merges (Swahili
     * has one word where German has two) — tolerated at runtime, where the join sets
     * `promptAmbiguous` and the UI adds the area label to the produce prompt. Pinned so a
     * NEW one (adding `nature/river` beside `bedroom/pillow`, both sw `mto`) fails here
     * instead of silently shipping an unanswerable prompt.
     */
    @Test
    fun crossAreaPromptCollisionsAreKnown() {
        val actual = collisionClusters()
            .map { (key, ids) -> "${key.first} ${key.second}: ${ids.sorted().joinToString(", ")}" }
            .toSortedSet()
        assertEquals(
            sortedSetOf(
                // Reviewed 2026-08-23: `cold` is the illness AND the adjective — English has
                // one word where de/eo/es/fr/it/sw/uk all split it (Erkältung/kalt,
                // malvarmumo/malvarma, resfriado/frío, rhume/froid, raffreddore/freddo,
                // mafua/baridi, застуда/холодний). qualities/cold-adj exists because `hot`
                // needs its polar partner, and health/cold is the illness a learner asks the
                // doctor about; the adjective carries the de note naming the second sense,
                // the ndege treatment.
                "en cold: health/cold, qualities/cold-adj",
                // Reviewed 2026-07-31: `el tiempo` is both Zeit and Wetter. de/en/sw/uk
                // all split it; `clima` is das Klima in Spain, so there is no alternative.
                // Reviewed 2026-08-23: `la dirección` is the direction AND the postal
                // address — de/en/eo/fr/it/sw/uk all split the pair (Richtung/Anschrift,
                // direkto/adreso, direction/adresse, direzione/indirizzo). `el sentido`
                // names only the direction of travel and `el rumbo` a heading, so there is
                // no honest alternative; directions/direction carries the de note naming
                // the second sense, the ndege treatment.
                "es dirección: admin/address, directions/direction",
                "es tiempo: nature/weather, time/time",
                // Reviewed 2026-08-15: `le tableau` is the picture on the wall AND the
                // classroom board — genuine French polysemy, one word both areas need
                // as their first pick. Repicking picture as `cadre` would teach the
                // frame; the living/picture card carries a de note naming the second
                // sense, the ndege treatment. Every other language splits the pair,
                // so it stays one-language.
                // Reviewed 2026-08-23: `l'entrée` is the way into a building AND the
                // hallway you step into (and the starter on a menu) — de and en split it
                // (Eingang/Flur, entrance/hallway) and Italian does too, once
                // directions/entrance is `entrata` against hall/hallway `ingresso`, so this
                // stays one-language. `le hall` is a lobby and `le vestibule` is dated, so
                // repicking would teach the wrong register; the card carries the de note.
                "fr entrée: directions/entrance, hall/hallway",
                // Reviewed 2026-08-23: `frais` is fresh AND, in the plural, the fees —
                // de/en/eo/es/it/sw/uk all split the pair (frisch/Gebühr, fresco/tasa,
                // fresco/spesa). There is no second word for fresh in French, and `les
                // frais` is what an office actually charges, so both stay; market/fresh
                // carries the de note naming the second sense, the ndege treatment.
                "fr frais: admin/fee, market/fresh",
                "fr tableau: living/picture, school/board",
                // Reviewed 2026-08-15: `le temps` is Zeit and Wetter alike — the same
                // Romance merge `es tiempo` and `it tempo` pin here; `la météo` names
                // the forecast and `le climat` the climate, so there is no honest
                // alternative. The concept pair is allowlisted in
                // [noConceptPairCollidesInTwoLanguages] as the reviewed family-wide merge.
                "fr temps: nature/weather, time/time",
                // Reviewed 2026-08-23: `molto` is both viel and sehr — Italian has one word
                // where de/en/eo/es/fr/sw/uk all split the quantity from the intensifier
                // (viel/sehr, mucho/muy, beaucoup/très, -ingi/sana). `assai` is the only
                // alternative for sehr and is literary, so repicking would teach a register
                // nobody speaks. degree/very and qualities/much stay two concepts because
                // every other language needs them to be; qualities/much's own it note
                // already names the second sense.
                "it molto: degree/very, qualities/much",
                // Reviewed 2026-08-15: `perché` is warum and weil in one word — the
                // interrogative and the causal conjunction genuinely merge in Italian
                // (Perché non vieni? — Perché piove.). Every other language splits them,
                // so the pair stays one-language; repicking poiché/siccome for `because`
                // would teach a formal register no one answers a question in.
                "it perché: connectors/because, questions/why",
                // Reviewed 2026-08-15: `il tempo` is Zeit and Wetter alike — the same
                // Romance merge `es tiempo` pins above; `meteo` names the forecast and
                // `clima` the climate, so there is no honest alternative. The concept
                // pair is allowlisted in [noConceptPairCollidesInTwoLanguages] as the
                // reviewed family-wide merge.
                "it tempo: nature/weather, time/time",
                // Reviewed 2026-07-25: the textbook homonym, and the only entry here that
                // is NOT a merge — sw `mto` is two unrelated senses (river, pillow),
                // not one word covering two German ones. Same treatment either way.
                // Re-pathed 2026-08-04: `outside` split into transport/city/nature and
                // `river` landed in `nature` — the same pair, renamed, not a new one.
                // Reviewed 2026-08-23: `rahisi` is cheap AND easy — de/en/eo/es/fr/it/uk all
                // split the pair (billig/einfach, barato/fácil, economico/facile). `nafuu`
                // is the relief of a better price rather than a low one, so repicking would
                // teach the wrong word; money/cheap carries the de note naming the second
                // sense, the ndege treatment.
                "sw rahisi: money/cheap, qualities/easy",
                "sw mto: bedroom/pillow, nature/river",
                // Reviewed 2026-08-04: sw `mwezi` is moon and month, exactly as uk `місяць`
                // is — so the moon is authored without uk, which keeps this to one language
                // and pinnable instead of the unfixable two-language pair.
                "sw mwezi: nature/moon, time/month",
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
    private val pluralArticles = mapOf(
        "de" to setOf("die"),
        "es" to setOf("los", "las"),
        "fr" to setOf("les"),
        "it" to setOf("i", "gli", "le"),
    )

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
