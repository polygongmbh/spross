package net.spross.kern.catalog

import net.spross.kern.model.CardKind
import net.spross.kern.model.Language

/** One ordered group from `areas.json`. */
data class AreaGroup(
    val id: String,
    val titles: Map<Language, String>,
    /** Area names in manifest order — the default progression within this group. */
    val areas: List<String>,
    /**
     * area → illustrative emoji, language-neutral display metadata owned by the manifest.
     * Total over [areas]: the parser requires an emoji on every entry.
     */
    val areaEmojis: Map<String, String>,
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
    /** Global catalog position across groups → areas → concepts. */
    val seedIndex: Int,
) {
    /**
     * Card identity — the bare slug, globally unique across areas (lint-enforced).
     * Area and [kind] stay free to change: moving a concept to another area keeps its
     * FSRS schedule, which is keyed by this id.
     */
    val id: String get() = slug
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

/**
 * One concept as the alphabet's example resolver sees it — the two halves of §2.2 kept
 * apart: [realizations] answers "does THIS language have a word for it" per language,
 * so the target-side example and the reader-side meaning never depend on each other.
 */
internal class CatalogSlug(
    val emoji: String?,
    val realizations: Map<Language, RawRealization>,
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
