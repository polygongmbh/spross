package net.spross.kern.catalog

import net.spross.kern.model.Language

/**
 * Reading a [CatalogSource] into a [Catalog]: which file each part comes from, and which
 * of them the fingerprint counts.
 *
 * TRACKED reads restamp a running box, raw reads never do — the difference is stated per
 * file below, and it is the whole reason this walk is written out rather than looped.
 */
internal fun loadCatalog(source: CatalogSource): Catalog {
    val tracked = FingerprintingSource(source)
    val groups = CatalogParser.parseAreasManifest("areas.json", tracked.require("areas.json"))
    val languages = CatalogParser.parseLanguages("languages.json", tracked.require("languages.json"))
    var seedIndex = 0
    val areas = groups.flatMap { it.areas }.map { name ->
        val conceptsPath = "areas/$name/concepts.json"
        val concepts = CatalogParser.parseConcepts(
            area = name,
            path = conceptsPath,
            text = tracked.require(conceptsPath),
            firstSeedIndex = seedIndex,
        )
        seedIndex += concepts.size
        val slugs = concepts.map { it.slug }.toSet()
        val titles = mutableMapOf<Language, String>()
        val subtitles = mutableMapOf<Language, String>()
        val realizations = mutableMapOf<Language, Map<String, RawRealization>>()
        for (lang in languages.keys) {
            val path = "areas/$name/$lang.json"
            val text = tracked.read(path) ?: continue
            val raw = CatalogParser.parseAreaLanguageFile(path, text, slugs)
            titles[lang] = raw.title
            raw.subtitle?.let { subtitles[lang] = it }
            realizations[lang] = raw.words
        }
        CatalogArea(name, concepts, titles, subtitles, realizations)
    }
    // why: read through the RAW source, like audio — the atlas drills, it never
    // joins a card, so editing it must not restamp a running box.
    val atlas = source.read("countries/atlas.json")
        ?.let { CountryAtlasParser.parseAtlas("countries/atlas.json", it) }
    val countrySlugs = atlas?.countries?.map { it.slug }.orEmpty().toSet()
    val countryNames = languages.keys.mapNotNull { lang ->
        val path = "countries/$lang.json"
        source.read(path)?.let {
            lang to CountryAtlasParser.parseNames(path, it, countrySlugs, languages.keys)
        }
    }.toMap()
    // why: read through the RAW source, like the atlas — a calendar name drills, it
    // never joins a card, so editing one must not restamp a running box.
    val dateCalendars = languages.keys.mapNotNull { lang ->
        val path = "dates/$lang.json"
        source.read(path)?.let {
            lang to DateCalendarParser.parse(path, it, lang, languages.keys)
        }
    }.toMap()
    // why: TRACKED — a language name lands inside joined card texts, so editing one
    // changes the join and must restamp a running box exactly as a realization does.
    // The table names every ATLAS language too, far beyond the app's own five.
    val nameable = languages.keys + atlas?.languages?.map { it.code }.orEmpty()
    val languageNames = languages.keys.mapNotNull { lang ->
        val path = "language-names/$lang.json"
        tracked.read(path)?.let { lang to CatalogParser.parseLanguageNames(path, it, nameable) }
    }.toMap()
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
    // why: read through the RAW source, like audio — a frame is never part of the
    // card join, so editing one must not restamp and recompose every running box.
    val conceptSlugs = areas.flatMap { area -> area.concepts.map { it.slug } }.toSet()
    val frames = source.read("phrases/frames.json")
        ?.let { CatalogParser.parseFrames("phrases/frames.json", it, conceptSlugs) }.orEmpty()
    val slots = frames.associate { it.slug to it.slot }
    val drills = languages.keys.mapNotNull { lang ->
        val path = "phrases/$lang.json"
        source.read(path)?.let { lang to CatalogParser.parseFrameLanguageFile(path, it, slots) }
    }.toMap()
    return Catalog(
        groups, languages, areas, tracked.fingerprint(), audio, alphabets, frames,
        frameRealizations = drills.mapValues { (_, it) -> it.frames },
        drillNotes = drills.mapValues { (_, it) -> it.numberNotes },
        languageNames = languageNames,
        countryAtlas = atlas,
        countryNames = countryNames,
        dateCalendars = dateCalendars,
    )
}
