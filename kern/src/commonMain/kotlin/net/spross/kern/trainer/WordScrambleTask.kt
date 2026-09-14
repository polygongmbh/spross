package net.spross.kern.trainer

import net.spross.kern.model.Language

/**
 * One word-scramble question. Pure data: the app shows [scrambled], takes a typed answer, and
 * reveals [display] (plus [gloss]) once the answer is in.
 *
 * The answer is TYPED, so the hard part stays where it belongs — the letters are a cue, not the
 * answer handed over in pieces.
 */
data class WordScrambleTask(
    /** The word's card id — what the drill has answered, never a schedule it writes. */
    val cardId: String,
    /** The language the word is written back in: the one being learned. */
    val language: Language,
    /** The Sprosse the mixing was cut at — what makes this a different question from the same word. */
    val level: Int,
    /** The letters as they stand on the card, and how much of the spelling they keep. */
    val scrambled: ScrambledWord,
    /**
     * Everything graded correct, canonical first: the word plus every synonym and variant the
     * catalog authored for it, so a real alternate spelling is never refused.
     */
    val accepted: List<String>,
    /** The canonical spelling, for the reveal. */
    val display: String,
    /** Shown on the reveal only — the drill never puts the meaning on screen before the answer. */
    val gloss: String,
)
