package net.spross.kern.model

/**
 * `Adjective` is the catch-all for non-noun, non-verb single words —
 * adjectives, adverbs, and interjections (`draußen`, `immer`, `Vorsicht`).
 * Engine-wise it is a plain word: introducible on its own, never phrase-gated,
 * never verb-prefix-stripped.
 */
enum class CardKind { Noun, Verb, Adjective, Phrase, Idiom }

/**
 * Fixed kind-marker emoji for every idiom card. Deliberately uniform across all
 * idiom concepts — never a per-concept meaning cue like other kinds' emoji — so a
 * learner recognizes "this is figurative" from the glyph alone, before reading
 * either language's text, on the very first exposure (`Catalog.kt` applies it at
 * join time; a per-concept `emoji` is rejected on idiom concepts at parse time).
 */
const val IDIOM_EMOJI = "🎭"

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
    /**
     * The concept's catalog slug — globally unique, never contains `|` or `/`.
     * Area-independent on purpose: reclassifying a concept keeps its schedule.
     */
    val id: String,
    val kind: CardKind,
    val area: String,
    val emoji: String?,
    /** Global catalog position (groups → areas → concepts), join-independent. */
    val seedIndex: Int,
    /**
     * Phrase component card ids (slugs), already filtered to components the
     * TARGET realizes — the unlock gate reads them as-is.
     */
    val components: List<String>,
    /** Base concept's card id when this is a feminine sibling concept. */
    val feminineOf: String?,
    /**
     * TARGET-side accepted texts (`text ∪ synonyms ∪ variants`) of the base concept,
     * resolved at join time — non-empty only on feminine cards whose base the target
     * realizes. Grading demotes a base-word answer to typo, not failure (§3).
     */
    val baseAccepted: List<String> = emptyList(),
    /** Known-language side (the prompt on produce). */
    val source: Realization,
    /** Learning-language side (the answer on produce, the prompt on recognize). */
    val target: Realization,
    /** True when the prompt is the base concept's source realization + ♀ badge. */
    val promptFeminineMarker: Boolean,
    /**
     * True when another emitted card carries an IDENTICAL produce prompt — a
     * target-language merge (sw `kuvaa` = anziehen AND sich anziehen) or a source
     * homonym. The UI adds this card's area label as a non-leaking disambiguating
     * cue, generalizing the ♀-badge pattern. PRODUCE only: on recognize any cue
     * strong enough to identify the concept would reveal the answer, and
     * self-grading absorbs the residue (§3).
     */
    val promptAmbiguous: Boolean = false,
)
