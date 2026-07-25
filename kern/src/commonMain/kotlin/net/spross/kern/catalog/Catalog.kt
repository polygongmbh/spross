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
) {
    /** Flattened default area order (groups top-to-bottom, areas as listed). */
    val areaNames: List<String> = areas.map { it.name }

    fun areaTitle(area: String, lang: Language): String? =
        areas.firstOrNull { it.name == area }?.titles?.get(lang)

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
            return Catalog(groups, languages, areas, tracked.fingerprint())
        }
    }
}
