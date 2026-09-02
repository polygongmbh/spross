package net.spross.kern.catalog

import net.spross.kern.model.Language

/**
 * The country atlas: `catalog/countries/`, the drill's own content.
 *
 * Two halves, exactly as the alphabet has: [CountryAtlas] is the language-neutral manifest
 * (`atlas.json` — which countries exist, which languages they carry, how far out each
 * sits), and `countries/<lang>.json` says what one language CALLS them ([CountryName]).
 * Language names are never repeated here; they come from `catalog/language-names/`
 * ([LanguageName]), so a country and the language it speaks are always named the same way.
 *
 * Tiers are authored 2–4 and say how far from home a row sits. Tier 1 is never authored:
 * it is the learner's OWN two languages and the countries that carry them, derived per
 * profile in [Catalog.countryDrillContent].
 */
data class CountryAtlas(
    /** Every language the atlas knows, in manifest order — far beyond the app's own five. */
    val languages: List<AtlasLanguage>,
    val countries: List<AtlasCountry>,
)

/** One `languages` row of the manifest. */
data class AtlasLanguage(val code: Language, val tier: Int)

/** One `countries` row of the manifest; [languages] resolve into [CountryAtlas.languages]. */
data class AtlasCountry(
    val slug: String,
    /** The emoji flag — language-neutral display metadata, like an area's emoji. */
    val flag: String,
    /** Codes of the languages spoken there, in authored order (most widely first). */
    val languages: List<Language>,
    val tier: Int,
)

/** One country as one language names it, from `countries/<lang>.json`. */
data class CountryName(
    val text: String,
    /** Accept-only alternates, never displayed (de bare "Schweiz" beside "die Schweiz"). */
    val variants: List<String> = emptyList(),
    /** Free-form grammatical metadata, as a realization carries it (de article, plural). */
    val grammar: Map<String, String> = emptyMap(),
    /** The person noun — every country carries one; the triple is country/nationality/language. */
    val nationality: NationalityName,
    /** Keyed by explanation language, as a realization's notes are; no fallback. */
    val notes: Map<Language, String> = emptyMap(),
)

/** What someone from a country is CALLED, with its accept-only gender alternates. */
data class NationalityName(
    val text: String,
    val variants: List<String> = emptyList(),
)

/**
 * The atlas joined for one (source, target) profile — everything the drill grades against,
 * and everything the reference table renders, from the same rows.
 *
 * Only what BOTH languages realize survives the join, so a caller never has to ask whether
 * a name exists. [AtlasCountryEntry.tier] and [AtlasLanguageEntry.tier] are the EFFECTIVE
 * tiers: 1 wherever the row is the profile's own, the manifest's 2–4 otherwise.
 */
data class CountryDrillContent(
    val source: Language,
    val target: Language,
    /** Manifest order, tier-independent. */
    val countries: List<AtlasCountryEntry>,
    val languages: List<AtlasLanguageEntry>,
) {
    private val languageByCode: Map<Language, AtlasLanguageEntry> = languages.associateBy { it.code }

    /** The outermost tier this pair actually joins — where a rung's pool stops widening. */
    val widestTier: Int = maxOf(
        countries.maxOfOrNull { it.tier } ?: 1,
        languages.maxOfOrNull { it.tier } ?: 1,
    )

    /** The languages of [country] this pair can actually name — manifest order, possibly empty. */
    fun languagesOf(country: AtlasCountryEntry): List<AtlasLanguageEntry> =
        country.languages.mapNotNull { languageByCode[it] }

    /** Every joined country listing [language] — the [languagesOf] relation read backwards. */
    fun countriesOf(language: AtlasLanguageEntry): List<AtlasCountryEntry> =
        countries.filter { language.code in it.languages }
}

/** One joined country: the manifest row plus both sides' names. */
data class AtlasCountryEntry(
    val slug: String,
    val flag: String,
    /** Effective tier — 1 where the country carries the profile's own language. */
    val tier: Int,
    val languages: List<Language>,
    val source: CountryName,
    val target: CountryName,
)

/** One joined language: the manifest row plus both sides' inflected names. */
data class AtlasLanguageEntry(
    val code: Language,
    /** Effective tier — 1 for the profile's own source and target. */
    val tier: Int,
    val source: LanguageName,
    val target: LanguageName,
)
