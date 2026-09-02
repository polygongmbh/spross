package net.spross.kern.trainer

import net.spross.kern.model.Language

/** The ladder the drill climbs: two multiple-choice Sprossen, typing, then transcription. */
enum class LetterStage { ChoiceEasy, ChoiceConfusable, Typed, Dictation }

/**
 * Which recording, if any, may speak [LetterDrillTask.promptText] — the provenance
 * discriminator behind the audio policy's "a recording plays only over the form it
 * actually says" rule.
 *
 * [Word] is stamped ONLY when the text came out of a resolved catalog realization (an
 * example word's own concept, or a box card): its slug addresses the very recording that
 * speaks it. An `exampleText` escape hatch is [PlainText] — it carries no slug, so no
 * recording can be looked up for it and the synthesizer reads what stands on screen.
 */
enum class LetterPromptKind { Name, Word, PlainText }

/**
 * One letter-drill question. Pure data: the app plays [promptText], renders [choices] or
 * an input field per [stage], and reveals [display] (plus [gloss]) once the answer is in.
 *
 * The prompt is ALWAYS a speakable surface form — a letter's NAME or a whole word, never a
 * bare glyph, which synthesizers read as anything from a spelling alphabet to a pause.
 */
data class LetterDrillTask(
    val stage: LetterStage,
    /** The alphabet's own language — what the prompt is spoken in. */
    val language: Language,
    /** The answered entry's stable ref (its `id`, else its glyph); a card id in dictation. */
    val answerRef: String,
    /** What is spoken: a letter name, an example word, or the dictated word. */
    val promptText: String,
    val promptKind: LetterPromptKind,
    /** Non-null iff [promptKind] is [LetterPromptKind.Word]: the concept slug or card id. */
    val promptSlug: String?,
    /** Non-null iff [promptKind] is [LetterPromptKind.Name]: the letters-manifest lookup key. */
    val promptGlyph: String?,
    /** Tiles in render order, answer included; null outside the choice stages. */
    val choices: List<String>?,
    /** The example word with the asked grapheme blanked, e.g. `Na＿t`; null for letter names. */
    val gapText: String?,
    /** Everything graded correct, canonical first. */
    val accepted: List<String>,
    /** The canonical answer, for the reveal. */
    val display: String,
    /** Shown on the reveal only — the drill never puts a cue on screen before the answer. */
    val gloss: String?,
)
