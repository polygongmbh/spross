package net.spross.kern.catalog

import net.spross.kern.model.CardKind
import net.spross.kern.model.Language
import net.spross.kern.trainer.PhraseTemplate
import net.spross.kern.trainer.TrainerKind

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

/** One `<area>/<lang>.json` as authored: the area's headings plus its realizations. */
internal data class RawArea(
    val title: String,
    /** The optional flavor clause under [title]; null where this area authors none. */
    val subtitle: String?,
    val words: Map<String, RawRealization>,
)

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
 * A sentence frame as authored in `drills/frames.json` — language-neutral, exactly like
 * [CatalogConcept]: a slug plus the [slot] kind its single `{slot}` takes. Frames are not
 * scheduled cards, so they carry no area, no kind and no seedIndex.
 */
internal data class CatalogFrame(
    val slug: String,
    val slot: TrainerKind,
)

/**
 * One `drills/<lang>.json` as authored: what this language does with numbers, and the
 * frames it renders. The notes describe the LANGUAGE, not any one frame, which is why
 * they sit beside [frames] rather than inside one.
 */
internal data class RawDrills(
    /** Keyed by explanation language, like a realization's notes; a few lines each. */
    val numberNotes: Map<Language, List<String>>,
    val frames: Map<String, RawFrame>,
)

/** One frame rendered in one language, as authored in `drills/<lang>.json`. */
internal data class RawFrame(
    /** Carries exactly one `{slot}`, and `{count}` iff [count] is authored. */
    val text: String,
    /** Accept-only alternate renderings of [text] (the du/Sie register split). */
    val variants: List<String>,
    /** Counted-noun agreement for the `{count}` marker; `numbers` frames only. */
    val count: PhraseTemplate.CountForms?,
    /** This realization counts a masculine/indeclinable noun: одна/дві are wrong. */
    val masculineNumeral: Boolean,
    /** Keyed by explanation language; selected by the profile's source at join time. */
    val notes: Map<Language, String>,
)

/**
 * One concept as the alphabet's example resolver sees it — the two halves of example
 * resolution (`kern/docs/catalog.md`) kept apart: [realizations] answers "does THIS
 * language have a word for it" per language, so the target-side example and the
 * reader-side meaning never depend on each other.
 */
internal class CatalogSlug(
    val emoji: String?,
    val realizations: Map<Language, RawRealization>,
)

internal class CatalogArea(
    val name: String,
    val concepts: List<CatalogConcept>,
    val titles: Map<Language, String>,
    /** Optional flavor clauses, keyed like [titles] — absent for an area authoring none. */
    val subtitles: Map<Language, String>,
    /** lang → slug → realization; only languages whose file exists. */
    val realizations: Map<Language, Map<String, RawRealization>>,
) {
    val conceptsBySlug: Map<String, CatalogConcept> = concepts.associateBy { it.slug }
}
