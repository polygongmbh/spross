package net.spross.kern.catalog

import net.spross.kern.box.DayPart
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.IDIOM_EMOJI
import net.spross.kern.model.Language
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.Realization
import net.spross.kern.model.nfcNormalized
import net.spross.kern.trainer.PhraseTemplate
import net.spross.kern.trainer.Trainer

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
    /** Sentence frames from `phrases/frames.json`, in manifest order; empty without the folder. */
    internal val frames: List<CatalogFrame>,
    /** lang → frame slug → realization; only languages whose `phrases/<lang>.json` exists. */
    internal val frameRealizations: Map<Language, Map<String, RawFrame>>,
    /** lang → reader → the prose [numberNotes] serves; same file, same registry rule. */
    internal val drillNotes: Map<Language, Map<Language, List<String>>>,
    /** reader → named language → its inflected names, from `language-names/<reader>.json`. */
    internal val languageNames: Map<Language, Map<Language, LanguageName>>,
    /** `countries/atlas.json`, or null without the folder — the atlas drill's registry. */
    internal val countryAtlas: CountryAtlas?,
    /** lang → slug → country name; only languages whose `countries/<lang>.json` exists. */
    internal val countryNames: Map<Language, Map<String, CountryName>>,
    /** Keyed by language, only where `dates/<lang>.json` exists — the dates drill's registry. */
    internal val dateCalendars: Map<Language, DateCalendar>,
) {
    /** Flattened default area order (groups top-to-bottom, areas as listed). */
    val areaNames: List<String> = areas.map { it.name }

    /** Backs [coveredSources]; lazy because deriving it joins every ordered pair. */
    private val covered: List<Language> by lazy {
        languages.keys.sorted().filter { availableTargets(it).isNotEmpty() }
    }

    /** slug → the one concept that owns it, built once (slugs are globally unique). */
    internal val slugIndex: Map<String, CatalogSlug> = buildMap {
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
     * The area's flavor clause, the same shape as [areaTitle]. Optional content: null
     * says this area authors none (in any language), never that the reader's is missing —
     * a subtitle is authored in every declared language or in none.
     */
    fun areaSubtitle(area: String, lang: Language): String? =
        areas.firstOrNull { it.name == area }?.subtitles?.get(lang)

    /**
     * The area's illustrative emoji from `areas.json` — language-neutral, so unlike
     * [areaTitle] it takes no language. Null only for an area the manifest never lists.
     */
    fun areaEmoji(area: String): String? =
        groups.firstNotNullOfOrNull { it.areaEmojis[area] }

    /**
     * What [lang] says at this stretch of the day ([Greetings.slug]), addressed to [name]
     * where one is known. Source-independent like [alphabetExample]: greeting somebody is
     * not a join, so the reader's language never enters it.
     *
     * A morning no language of its own realizes falls to the all-day greeting, because
     * that IS the morning greeting there — Spanish, French and Italian leave `good-morning`
     * unauthored rather than duplicate `¡Buenos días!` / `Bonjour !` / `Buongiorno!`.
     * Null anywhere else the language realizes nothing, which is the surface's cue to say
     * something of its own, never to fall back to another language's line.
     */
    fun greeting(lang: Language, part: DayPart, name: String? = null): String? =
        (text(Greetings.slug(part), lang)
            ?: if (part == DayPart.Morning) text(Greetings.slug(DayPart.Day), lang) else null)
            ?.let { Greetings.addressed(it, name) }

    /**
     * Everything [lang] can say to a learner right now, its greeting for [part] first and the
     * hour-independent invitations after it, each addressed to [name] where one is given.
     *
     * Only what the language actually realizes: the list may be short, or empty where a
     * language authors none of them, which is the surface's cue to say something of its own.
     */
    fun spokenLines(lang: Language, part: DayPart, name: String? = null): List<String> =
        (listOfNotNull(greeting(lang, part)) + Greetings.INVITATIONS.mapNotNull { text(it, lang) })
            .map { Greetings.addressed(it, name) }

    private fun text(slug: String, lang: Language): String? =
        slugIndex[slug]?.realizations?.get(lang)?.text

    /**
     * Emits one [Card] per joinable concept, in catalog order. A concept joins iff the
     * TARGET realizes it AND a source prompt exists: its source realization, else
     * (feminineOf only) the base concept's source realization with `promptFeminineMarker`;
     * skipped when neither exists — or when either side names the target language
     * ([LanguageMarker]) and its own table cannot.
     */
    fun join(source: Language, target: Language): List<Card> {
        require(source != target) { "source == target ($source)" }
        require(source in languages) { "unknown source language \"$source\"" }
        require(target in languages) { "unknown target language \"$target\"" }
        // why: a marker always names the TARGET, and each side resolves it against its OWN
        // table — the prompt reads "Ich lerne Suaheli", the answer "Ninajifunza Kiswahili".
        val sourceName = languageName(source, target)
        val targetName = languageName(target, target)
        val cards = mutableListOf<Card>()
        for (area in areas) {
            val sourceWords = area.realizations[source].orEmpty()
            val targetWords = area.realizations[target].orEmpty()

            fun targetRealization(slug: String): RawRealization? =
                targetWords[slug]?.resolved(targetName)

            for (concept in area.concepts) {
                val targetRaw = targetRealization(concept.slug) ?: continue
                val ownSource = sourceWords[concept.slug]
                val promptRaw = (ownSource ?: concept.feminineOf?.let { sourceWords[it] })
                    ?.resolved(sourceName) ?: continue
                cards += Card(
                    id = concept.id,
                    kind = concept.kind,
                    area = area.name,
                    // why: idiom emoji is a kind marker, not a per-concept picture —
                    // see IDIOM_EMOJI — applied here so every consumer sees it, never null.
                    emoji = if (concept.kind == CardKind.Idiom) IDIOM_EMOJI else concept.emoji,
                    seedIndex = concept.seedIndex,
                    // why: components without a target realization can never be studied —
                    // filtering here keeps the phrase-unlock gate a plain all-components check.
                    components = concept.components.filter { targetRealization(it) != null },
                    feminineOf = concept.feminineOf,
                    // why: grading needs the base concept's TARGET texts (answer side)
                    // to demote a base-word answer — absent when the target never
                    // realizes the base, and the demotion simply has nothing to match.
                    baseAccepted = concept.feminineOf?.let { base ->
                        targetRealization(base)?.let { listOf(it.text) + it.synonyms + it.variants }
                    }.orEmpty(),
                    source = realize(source, promptRaw, reader = null),
                    target = realize(target, targetRaw, reader = source),
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

    /**
     * The frames' half of [join]: one [PhraseTemplate] per frame realized in BOTH languages,
     * directional like a [Card]. `masculineNumeral`/`swahiliNounClass`/`notes` ride along
     * from the ANSWER realization;
     * `count` rides from BOTH, because agreement belongs to whichever language
     * authored it and the prompt renders its own frame. Empty unless [Trainer.supports] the
     * target — sampling generates the answer side's number words, so a target without a pack
     * only ever supplies prompts.
     */
    fun phraseTemplates(source: Language, target: Language): List<PhraseTemplate> {
        require(source != target) { "source == target ($source)" }
        require(source in languages) { "unknown source language \"$source\"" }
        require(target in languages) { "unknown target language \"$target\"" }
        if (!Trainer.supports(target)) return emptyList()
        val sourceFrames = frameRealizations[source].orEmpty()
        val targetFrames = frameRealizations[target].orEmpty()
        // why: markers resolve before the template is built, so {slot}/{count} filling
        // never sees one — same rule as [join], and a side that cannot name the target
        // loses the frame for this pair rather than rendering the marker.
        val sourceName = languageName(source, target)
        val targetName = languageName(target, target)
        return frames.mapNotNull { frame ->
            val prompt = sourceFrames[frame.slug]?.resolved(sourceName) ?: return@mapNotNull null
            val answer = targetFrames[frame.slug]?.resolved(targetName) ?: return@mapNotNull null
            // why: a fraction slot needs the pack to READ one — a frame the target cannot
            // fill is dropped here rather than throwing on the first draw, in a live run.
            if (!Trainer.supportsSlot(frame.slot, target)) return@mapNotNull null
            PhraseTemplate(
                id = frame.slug,
                source = source,
                target = target,
                sourceTemplate = prompt.text,
                targetTemplate = answer.text,
                slotKind = frame.slot,
                acceptedFrames = answer.variants,
                note = answer.notes[source] ?: answer.notes[target], // why: as a card's note
                countForms = answer.count,
                sourceCountForms = prompt.count,
                masculineNumeral = answer.masculineNumeral,
                swahiliNounClass = answer.swahiliNounClass,
            )
        }
    }

    /**
     * How [reader] says the name of [named], in every form a sentence can ask for. Null
     * where the reader's table is missing or has no entry — the caller's cue to leave the
     * material out rather than to invent a name for it.
     */
    fun languageName(reader: Language, named: Language): LanguageName? =
        languageNames[reader]?.get(named)

    /**
     * Targets learnable from [source]: every other language with ≥ 50 joinable concepts.
     * Like [join], this answers only for a language the catalog declares — an undeclared
     * one is a caller that skipped [coveredSources], not a profile with nothing to learn.
     */
    fun availableTargets(source: Language): List<AvailableTarget> {
        require(source in languages) { "unknown source language \"$source\"" }
        return languages.values
            .filter { it.code != source }
            .map { AvailableTarget(it.code, it.name, join(source, it.code).size) }
            .filter { it.conceptCount >= MIN_JOINABLE_CONCEPTS }
    }

    /**
     * Sources worth offering: every language with at least one learnable target, sorted
     * by code. The total query in front of [availableTargets] — asking whether a device
     * locale is offerable must be answerable for locales the catalog has never heard of.
     */
    fun coveredSources(): List<Language> = covered

    /**
     * The source a fresh install opens with on a device reporting [deviceLanguage]
     * (contract §1): that language when the catalog teaches from it, else English —
     * and where English itself teaches nothing, the first source that does, so the
     * answer stays one [availableTargets] accepts.
     */
    fun defaultSource(deviceLanguage: Language): Language = when {
        deviceLanguage in covered -> deviceLanguage
        FALLBACK_SOURCE in covered -> FALLBACK_SOURCE
        else -> covered.firstOrNull() ?: FALLBACK_SOURCE
    }

    /**
     * [this] with its language markers filled in from [name], or null when it carries one
     * and [name] is absent: a side that cannot name the target drops the concept, the same
     * honest-out as a missing realization. Marker-free realizations pass through untouched.
     */
    private fun RawRealization.resolved(name: LanguageName?): RawRealization? {
        if (!carriesLanguageMarker()) return this
        val named = name ?: return null
        return copy(
            text = LanguageNames.resolve(text, named),
            synonyms = synonyms.map { LanguageNames.resolve(it, named) },
            variants = variants.map { LanguageNames.resolve(it, named) },
        )
    }

    private fun RawRealization.carriesLanguageMarker(): Boolean =
        LanguageNames.hasLanguageMarker(text) ||
            synonyms.any { LanguageNames.hasLanguageMarker(it) } ||
            variants.any { LanguageNames.hasLanguageMarker(it) }

    /** The frames' half of [resolved]; agreement forms name a counted noun, never a language. */
    private fun RawFrame.resolved(name: LanguageName?): RawFrame? {
        val marked = LanguageNames.hasLanguageMarker(text) ||
            variants.any { LanguageNames.hasLanguageMarker(it) }
        if (!marked) return this
        val named = name ?: return null
        return copy(
            text = LanguageNames.resolve(text, named),
            variants = variants.map { LanguageNames.resolve(it, named) },
        )
    }

    private fun realize(lang: Language, raw: RawRealization, reader: Language?): Realization =
        Realization(
            lang = lang,
            text = raw.text,
            synonyms = raw.synonyms,
            variants = raw.variants,
            grammar = raw.grammar,
            // why: a note written FOR this reader wins; otherwise the one written in the
            // language being explained, which every reader can read (`kern/docs/catalog.md`).
            // Null reader is the PROMPT side, where neither arm has anything to say.
            note = reader?.let { raw.notes[it] ?: raw.notes[lang] },
        )

    companion object {
        private const val MIN_JOINABLE_CONCEPTS = 50

        /** The source every device falls back to when its own language teaches nothing. */
        const val FALLBACK_SOURCE: Language = "en"

        fun load(source: CatalogSource): Catalog = loadCatalog(source)
    }
}
