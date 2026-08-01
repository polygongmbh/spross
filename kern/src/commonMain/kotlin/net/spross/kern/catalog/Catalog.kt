package net.spross.kern.catalog

import net.spross.kern.model.Card
import net.spross.kern.model.Language
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.Realization
import net.spross.kern.model.nfcNormalized

/**
 * The parsed content catalog. Cards are a runtime [join] per (source, target) profile —
 * never persisted.
 */
class Catalog internal constructor(
    val groups: List<AreaGroup>,
    /** Keyed by code, in `languages.json` order. */
    val languages: Map<Language, LanguageInfo>,
    internal val areas: List<CatalogArea>,
    /** Stable content hash over every catalog file read (for [net.spross.kern.model.JoinStamp]). */
    val fingerprint: String,
    /** Keyed by language, only where `audio/<lang>/manifest.json` exists. */
    internal val audio: Map<Language, AudioManifest>,
    /** Keyed by language, only where `alphabet/<lang>.json` exists — the drill's registry. */
    internal val alphabets: Map<Language, Alphabet>,
) {
    /** Flattened default area order (groups top-to-bottom, areas as listed). */
    val areaNames: List<String> = areas.map { it.name }

    /** slug → the one concept that owns it, built once (slugs are globally unique). */
    private val slugIndex: Map<String, CatalogSlug> = buildMap {
        for (area in areas) {
            for (concept in area.concepts) {
                put(
                    concept.slug,
                    CatalogSlug(
                        emoji = concept.emoji,
                        realizations = area.realizations.mapNotNull { (lang, words) ->
                            words[concept.slug]?.let { lang to it }
                        }.toMap(),
                    ),
                )
            }
        }
    }

    fun areaTitle(area: String, lang: Language): String? =
        areas.firstOrNull { it.name == area }?.titles?.get(lang)

    /**
     * The area's illustrative emoji from `areas.json` — language-neutral, so unlike
     * [areaTitle] it takes no language. Null only for an area the manifest never lists.
     */
    fun areaEmoji(area: String): String? =
        groups.firstNotNullOfOrNull { it.areaEmojis[area] }

    /**
     * Emits one [Card] per joinable concept, in catalog order. A concept joins iff the
     * TARGET realizes it AND a source prompt exists: its source realization, else
     * (feminineOf only) the base concept's source realization with `promptFeminineMarker`;
     * skipped when neither exists.
     */
    fun join(source: Language, target: Language): List<Card> {
        require(source != target) { "source == target ($source)" }
        require(source in languages) { "unknown source language \"$source\"" }
        require(target in languages) { "unknown target language \"$target\"" }
        val cards = mutableListOf<Card>()
        for (area in areas) {
            val sourceWords = area.realizations[source].orEmpty()
            val targetWords = area.realizations[target].orEmpty()

            fun joins(concept: CatalogConcept): Boolean {
                if (concept.slug !in targetWords) return false
                if (concept.slug in sourceWords) return true
                return concept.feminineOf?.let { it in sourceWords } ?: false
            }

            for (concept in area.concepts) {
                if (!joins(concept)) continue
                val ownSource = sourceWords[concept.slug]
                val promptRaw = ownSource ?: sourceWords.getValue(concept.feminineOf!!)
                cards += Card(
                    id = concept.id,
                    kind = concept.kind,
                    area = area.name,
                    emoji = concept.emoji,
                    seedIndex = concept.seedIndex,
                    // why: components without a target realization can never be studied —
                    // filtering here keeps the phrase-unlock gate a plain all-components check.
                    components = concept.components.filter { it in targetWords },
                    feminineOf = concept.feminineOf,
                    // why: grading needs the base concept's TARGET texts (answer side)
                    // to demote a base-word answer — absent when the target never
                    // realizes the base, and the demotion simply has nothing to match.
                    baseAccepted = concept.feminineOf?.let { base ->
                        targetWords[base]?.let { listOf(it.text) + it.synonyms + it.variants }
                    }.orEmpty(),
                    source = realize(source, promptRaw, source),
                    target = realize(target, targetWords.getValue(concept.slug), source),
                    promptFeminineMarker = ownSource == null,
                )
            }
        }
        // why: a produce prompt two cards share is unanswerable without a cue, so flag
        // it once per join and let the UI add the area label. Keyed on what the learner
        // SEES — citation conventions (de noun capitals, en "to ", sw ku-) keep
        // noun/verb homographs apart, and a ♀ sibling is disambiguated by its badge.
        val promptCounts = cards.groupingBy(::promptKey).eachCount()
        return cards.map { it.copy(promptAmbiguous = promptCounts.getValue(promptKey(it)) > 1) }
    }

    private fun promptKey(card: Card): String =
        nfcNormalized(card.source.text).trim() + if (card.promptFeminineMarker) "♀" else ""

    /** Targets learnable from [source]: every other language with ≥ 50 joinable concepts. */
    fun availableTargets(source: Language): List<AvailableTarget> {
        require(source in languages) { "unknown source language \"$source\"" }
        return languages.values
            .filter { it.code != source }
            .map { AvailableTarget(it.code, it.name, join(source, it.code).size) }
            .filter { it.conceptCount >= MIN_JOINABLE_CONCEPTS }
    }

    /**
     * How [visibleForm] is pronounced in [lang] — keyed by what stands on the card, so a
     * rotated synonym is spoken as itself. A bundled recording is returned only when it
     * speaks that form ([speechKey]); everything else falls to the app's synthesizer,
     * which is handed [Pronunciation.utterance]. Paths only: kern never reads audio bytes.
     */
    fun pronunciation(lang: Language, visibleForm: String): Pronunciation =
        Pronunciation(
            form = visibleForm,
            utterance = utterance(visibleForm),
            lang = lang,
            recordingPath = audio[lang]?.recordingPath(visibleForm),
        )

    /** The letter's recording, catalog-relative; null → the drill speaks its NAME instead. */
    fun letterRecordingPath(lang: Language, glyph: String): String? = audio[lang]?.letterPath(glyph)

    /**
     * The alphabet reference sheet's content for [lang], null where no file is authored.
     * File presence IS the registry: adding a language's alphabet is dropping a file.
     */
    fun alphabet(lang: Language): Alphabet? = alphabets[lang]

    /**
     * The entry's example word in the ALPHABET's own language — what the drill speaks and
     * gaps. Source-independent by design: the example must exist no matter who is reading,
     * so the join is never consulted. Null → the caller falls back to
     * [AlphabetEntry.exampleText], which carries no slug and therefore no recording.
     */
    fun alphabetExample(entry: AlphabetEntry, lang: Language): AlphabetExample? {
        val slug = entry.exampleSlug ?: return null
        val concept = slugIndex[slug] ?: return null
        val text = concept.realizations[lang]?.text ?: return null
        return AlphabetExample(slug, text, concept.emoji)
    }

    /**
     * What the example word MEANS to a reader of [lang] — null whenever that language does
     * not realize the concept. The sheet then omits the meaning line: graceful
     * degradation, never an error (an alphabet is not a join).
     */
    fun exampleMeaning(slug: String, lang: Language): String? =
        slugIndex[slug]?.realizations?.get(lang)?.text

    /**
     * Attribution for every bundled recording, grouped by (language, author, licence) —
     * BY and BY-SA cannot share a notice, so the groups ARE the credit rows. Derived from
     * the shipped manifests, so the surface can never credit what is not bundled. Order is
     * stable: languages as declared, entries as the manifest lists them.
     */
    fun audioCredits(): List<AudioCredit> {
        val files = LinkedHashMap<CreditKey, MutableList<AudioCreditFile>>()
        val deeds = mutableMapOf<CreditKey, String?>()
        for ((lang, manifest) in audio) {
            for ((label, recording) in manifest.creditRows()) {
                val key = CreditKey(lang, recording.author, recording.licence)
                files.getOrPut(key) { mutableListOf() } += AudioCreditFile(label, recording.source)
                if (key !in deeds) deeds[key] = recording.licenceUrl
            }
        }
        return files.map { (key, rows) ->
            AudioCredit(key.language, key.author, key.licence, deeds[key], rows)
        }
    }

    private fun realize(lang: Language, raw: RawRealization, source: Language): Realization =
        Realization(
            lang = lang,
            text = raw.text,
            synonyms = raw.synonyms,
            variants = raw.variants,
            grammar = raw.grammar,
            note = raw.notes[source], // why: notes never cross-language fall back — §2
        )

    companion object {
        private const val MIN_JOINABLE_CONCEPTS = 50

        fun load(source: CatalogSource): Catalog {
            val tracked = FingerprintingSource(source)
            val groups = CatalogParser.parseAreasManifest("areas.json", tracked.require("areas.json"))
            val languages = CatalogParser.parseLanguages("languages.json", tracked.require("languages.json"))
            var seedIndex = 0
            val areas = groups.flatMap { it.areas }.map { name ->
                val conceptsPath = "$name/concepts.json"
                val concepts = CatalogParser.parseConcepts(
                    area = name,
                    path = conceptsPath,
                    text = tracked.require(conceptsPath),
                    firstSeedIndex = seedIndex,
                )
                seedIndex += concepts.size
                val slugs = concepts.map { it.slug }.toSet()
                val titles = mutableMapOf<Language, String>()
                val realizations = mutableMapOf<Language, Map<String, RawRealization>>()
                for (lang in languages.keys) {
                    val path = "$name/$lang.json"
                    val text = tracked.read(path) ?: continue
                    val (title, words) = CatalogParser.parseAreaLanguageFile(path, text, slugs)
                    titles[lang] = title
                    realizations[lang] = words
                }
                CatalogArea(name, concepts, titles, realizations)
            }
            // why: read through the RAW source, never the fingerprinting wrapper — audio
            // can never change the join, so a refreshed pack must not restamp (and
            // recompose) a session that is already running.
            val audio = languages.keys.mapNotNull { lang ->
                val path = "audio/$lang/manifest.json"
                source.read(path)?.let { lang to AudioManifestParser.parse(path, it, lang) }
            }.toMap()
            // why: TRACKED, unlike audio — an alphabet is content, so editing one recomposes
            // a running session once on upgrade, which is the designed behavior.
            val alphabets = languages.keys.mapNotNull { lang ->
                val path = "alphabet/$lang.json"
                tracked.read(path)?.let { lang to AlphabetParser.parse(path, it, lang, languages.keys) }
            }.toMap()
            return Catalog(groups, languages, areas, tracked.fingerprint(), audio, alphabets)
        }
    }
}

/** Credit identity: one author's work in one language under one licence. */
private data class CreditKey(val language: Language, val author: String, val licence: String)
