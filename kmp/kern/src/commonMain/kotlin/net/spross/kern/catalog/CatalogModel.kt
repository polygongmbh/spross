package net.spross.kern.catalog

import net.spross.kern.model.CardKind
import net.spross.kern.model.Language

/** One ordered group from `areas.json`. */
data class AreaGroup(
    val id: String,
    val titles: Map<Language, String>,
    val areas: List<String>,
)

/** A target language a source can learn, with its joinable concept count. */
data class AvailableTarget(
    val code: Language,
    val name: String,
    val conceptCount: Int,
)

/** Language-neutral concept as authored in `<area>/concepts.json`. */
internal data class CatalogConcept(
    val area: String,
    val slug: String,
    val kind: CardKind,
    val emoji: String?,
    /** Same-area word slugs (phrases only). */
    val components: List<String>,
    val feminineOf: String?,
    val variantOf: String?,
    /** Global catalog position across groups → areas → concepts. */
    val seedIndex: Int,
) {
    val id: String get() = "$area/$slug"
}

/** One realization as authored, before profile selection. */
internal data class RawRealization(
    val text: String,
    val synonyms: List<String>,
    val variants: List<String>,
    val grammar: Map<String, String>,
    /** Keyed by explanation language; selected by the profile's source at join time. */
    val notes: Map<Language, String>,
)

internal class CatalogArea(
    val name: String,
    val concepts: List<CatalogConcept>,
    val titles: Map<Language, String>,
    /** lang → slug → realization; only languages whose file exists. */
    val realizations: Map<Language, Map<String, RawRealization>>,
) {
    val conceptsBySlug: Map<String, CatalogConcept> = concepts.associateBy { it.slug }
}
