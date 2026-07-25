package net.spross.kern.model

/**
 * `Adjective` is the catch-all for non-noun, non-verb single words —
 * adjectives, adverbs, and interjections (`draußen`, `immer`, `Vorsicht`).
 * Engine-wise it is a plain word: introducible on its own, never phrase-gated,
 * never verb-prefix-stripped.
 */
enum class CardKind { Noun, Verb, Adjective, Phrase }

/** One concept rendered in one language, as joined for a concrete (source, target) profile. */
data class Realization(
    val lang: Language,
    /** Canonical answer/display form. */
    val text: String,
    /** Distinct-knowledge alternates — rotate through recognition prompts, all accepted. */
    val synonyms: List<String> = emptyList(),
    /** Accepted surface forms of the same knowledge — grading/display only, never prompted. */
    val variants: List<String> = emptyList(),
    /** Language-specific bare facts (de `gender`/`plural`, …). */
    val grammar: Map<String, String> = emptyMap(),
    /** Already selected by the profile's SOURCE language at join time — the UI cannot leak. */
    val note: String? = null,
)

/**
 * Derived, language-symmetric card for one (source, target) profile.
 * Derived at load from the catalog join — never persisted.
 */
data class Card(
    /** `"area/slug"` — concept identity; never contains `|`. */
    val id: String,
    val kind: CardKind,
    val area: String,
    val emoji: String?,
    /** Global catalog position (groups → areas → concepts), join-independent. */
    val seedIndex: Int,
    /**
     * Phrase component card ids (`"area/slug"`), already filtered to components the
     * TARGET realizes — the unlock gate reads them as-is.
     */
    val components: List<String>,
    /** Base concept's card id when this is a feminine sibling concept. */
    val feminineOf: String?,
    /** Known-language side (the prompt on produce). */
    val source: Realization,
    /** Learning-language side (the answer on produce, the prompt on recognize). */
    val target: Realization,
    /** True when the prompt is the base concept's source realization + ♀ badge. */
    val promptFeminineMarker: Boolean,
)
