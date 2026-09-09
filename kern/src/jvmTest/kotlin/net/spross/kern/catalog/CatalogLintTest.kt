package net.spross.kern.catalog

import net.spross.kern.model.CardKind
import net.spross.kern.model.nfcNormalized
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

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

    /**
     * Areas that exist on disk on purpose and are deliberately NOT in `areas.json` —
     * written, sourced and formatted, waiting on something outside the catalog before a
     * learner may meet them. The manifest is what the loader reads, so a parked area takes
     * no seed index, joins no card and reaches no box; this set is what tells the folder
     * rule that the absence is a decision rather than a hole
     * ([everyAreaFolderIsRegisteredInTheManifest]), and [parkedAreasStayActivatable] is
     * what keeps the content honest while it waits.
     *
     * Parking is for content whose guard is missing, never for content that is unfinished:
     * a half-written area belongs on a branch, where nothing has to explain it.
     *
     * - `reproduction` (parked 2026-09-02): Penis, Vagina and Hoden, ready in all eight
     *   languages. Two things have to ship before they may: a kern-owned quiet flag, so the
     *   listening drill never reads them aloud unattended, and a default-off setting that
     *   hides the shelf. `catalog/areas/reproduction/README.md` carries the rest, including
     *   why the crude Swahili the corpus holds is absent rather than merely unlisted.
     */
    private val parkedAreas = setOf("reproduction")

    /** A shelf a learner can hold in their head — `catalog/areas/README.md`. */
    private val areaCardLine = 40

    /**
     * Areas that stood past the line when it became a check, each waiting on the cut a
     * learner would name: `food` splits at raw ingredients against meals and drinks,
     * `qualities` at a `comparison` shelf, while `desk` and `admin` have no named seam yet.
     * Held in both directions ([anAreaHoldsAFewDozenCards]), so a split clears its own
     * waiver instead of leaving one behind for the next reader to trust.
     */
    private val oversizedAreas = setOf("admin", "desk", "food", "qualities")

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
        assertEquals(parkedAreas, parkedAreas intersect onDisk, "parked area with no folder")
        assertEquals(onDisk - parkedAreas, catalog.areaNames.toSortedSet())
    }

    /**
     * An area is a shelf a learner can hold in their head and choose to pull forward, so it
     * holds a few dozen cards and never a drawer's worth — the rule and its seam are
     * `catalog/areas/README.md` § which area a concept lives in, and [oversizedAreas] is
     * the standing exception.
     *
     * A parked area meets the line on activation, which is what [parkedAreasStayActivatable]
     * reviews; nothing counts it while it waits.
     */
    @Test
    fun anAreaHoldsAFewDozenCards() {
        for (area in catalog.areas) {
            val size = area.concepts.size
            if (area.name in oversizedAreas) {
                assertTrue(
                    size > areaCardLine,
                    "${area.name}: $size concepts is back inside the line — drop it from oversizedAreas",
                )
            } else {
                assertTrue(
                    size <= areaCardLine,
                    "${area.name}: $size concepts, past the ~$areaCardLine line — " +
                        "cut it along a seam a learner would name, or waive it in oversizedAreas",
                )
            }
        }
    }

    /**
     * A parked area is held to everything that decides whether it can still be activated,
     * because the catalog moves underneath it while it waits: its slugs are the card ids
     * the box would key by, so one minted live in the meantime would fuse two concepts on
     * the day it lands, and a language file gone missing would ship a hole.
     *
     * What it is NOT held to is the content rules the live lints apply — a parked area is
     * not in `catalog.areas`, so wording, notes and grammar go unchecked until it joins.
     * Activation is therefore a review, never a rename: move it into `areas.json`, drop it
     * from [parkedAreas], and let the full gate say what it thinks.
     */
    @Test
    fun parkedAreasStayActivatable() {
        val live = catalog.areas.flatMap { it.concepts }.map { it.slug }.toSet()
        for (area in parkedAreas) {
            val folder = File(areasRoot, area)
            assertTrue(slugPattern.matches(area), "bad parked area name: $area")
            val concepts = CatalogParser.parseConcepts(
                area = area,
                path = "areas/$area/concepts.json",
                text = File(folder, "concepts.json").readText(),
                firstSeedIndex = 0,
            )
            assertTrue(concepts.isNotEmpty(), "$area: parked with no concepts")
            val slugs = concepts.map { it.slug }
            assertEquals(emptyList(), slugs.filter { it in live }, "$area: slug already live")
            for (lang in catalog.languages.keys) {
                val file = File(folder, "$lang.json")
                assertTrue(file.isFile, "$area: parked without $lang.json")
                CatalogParser.parseAreaLanguageFile("areas/$area/$lang.json", file.readText(), slugs.toSet())
            }
        }
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

    /** `to-` is the verb's mark and nothing else's, so a noun and its verb never contend for one slug. */
    @Test
    fun verbSlugsCarryTheToPrefix() {
        for (area in catalog.areas) {
            for (concept in area.concepts) {
                assertEquals(
                    concept.kind == CardKind.Verb, concept.slug.startsWith("to-"),
                    "${area.name}/${concept.slug}: ${concept.kind} — a verb slug starts with to-, no other does",
                )
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
     * The modifier letter apostrophe (U+02BC) is the alphabet's alone: `catalog/alphabet/`
     * stores letter names with it and the uk audio manifest keys one by its glyph, while a
     * realization, a country name or a note writes the typewriter U+0027. Grading folds the
     * class, so nothing breaks either way — what breaks is an author meeting the letter in
     * an alphabet file and carrying it into content, where it then reads as a second
     * spelling of a word that already exists.
     */
    @Test
    fun contentWritesTheTypewriterApostrophe() {
        val files = RealCatalog.root.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .filterNot { it.path.contains("/alphabet/") || it.path.contains("/audio/") }
            .toList()
        assertTrue(files.isNotEmpty(), "no catalog json found under ${RealCatalog.root}")
        for (file in files) {
            assertTrue(
                MODIFIER_APOSTROPHE !in file.readText(),
                "${file.relativeTo(RealCatalog.root)}: U+02BC belongs to catalog/alphabet/ — write U+0027 here",
            )
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
                val where = "phrases/$lang.json $slug"
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
     * Writing a note FOR one reader is a promise to write it for every reader who needs it,
     * so a German card that addresses a reader at all addresses both the readers German is
     * taught to. A self-keyed note is not addressing a reader — it is the shared wording —
     * so a card carrying only `de` is untouched.
     */
    @Test
    fun readerNotesOnGermanTargetsCoverEnglishAndSpanish() {
        forEachRealization { area, lang, slug, raw ->
            if (lang != "de") return@forEachRealization
            val readers = raw.notes.keys.filter { it != "de" }
            if (readers.isEmpty()) return@forEachRealization
            assertTrue(
                "en" in readers && "es" in readers,
                "$area/de.json $slug: a reader note needs both en and es, has ${readers.sorted()}",
            )
        }
    }

    /**
     * The rules the catalog teaches by note, and every language that draws the
     * distinction and must therefore say so. A pin, not an inference: no gate can tell
     * whether a language HAS a rule to state, so the languages that draw no line are
     * named here with their reason rather than being silently absent.
     *
     * This is the class of gap that goes unnoticed — `at-school`/`go-to-school` carried
     * the location/destination rule in six languages and not in German, the one that
     * draws it most sharply, for as long as the pair existed.
     */
    @Test
    fun anchoredRulesAreStatedByEveryLanguageThatDrawsThem() {
        // anchor slug → the languages whose realization must carry a note.
        // fr/it/sw say location and destination one way and state that on `at-school`;
        // en draws it lexically and annotates the article instead.
        val anchors = mapOf(
            "go-to-school" to setOf("de", "eo", "es", "fr", "it", "uk"),
            "its-cold-in-the-mountains" to setOf("de", "eo", "uk"),
            "home" to setOf("de", "eo", "uk"),
            "with" to setOf("de", "uk"),
            "for" to setOf("de", "es", "uk"),
            "because-of" to setOf("de", "eo", "uk"),
            "to-follow" to setOf("de", "uk"),
            // Cases German has no counterpart for: the uk vocative, the uk genitive of
            // negation (German keeps the accusative), the es personal `a`.
            // sw draws the line too, but on the pronoun: the object is an affix inside
            // the verb, so `pronouns` states it per person and the phrase practices it.
            "mom-help-me" to setOf("de", "eo", "uk"),
            "i-dont-eat-meat" to setOf("fr", "uk"),
            "ask-the-teacher" to setOf("es"),
        )
        for ((slug, langs) in anchors) {
            val realized = catalog.areas.firstNotNullOfOrNull { area ->
                area.realizations.takeIf { rs -> rs.values.any { slug in it } }
            } ?: fail("anchor $slug names no concept")
            for (lang in langs) {
                val raw = realized[lang]?.get(slug) ?: fail("anchor $slug is unrealized in $lang")
                assertTrue(raw.notes.isNotEmpty(), "$slug: $lang draws this rule and states nothing")
            }
        }
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

/** The alphabet's apostrophe — see [CatalogLintTest.contentWritesTheTypewriterApostrophe]. */
private const val MODIFIER_APOSTROPHE = '\u02BC'
