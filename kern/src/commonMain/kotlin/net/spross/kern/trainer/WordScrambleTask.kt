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
     * What grades correct: the ONE form [scrambled] holds the letters of, and nothing else.
     * A synonym or a variant is a different spelling of the same knowledge, which is exactly
     * what these letters do not spell ([WordScrambleRun.grade]).
     */
    val accepted: List<String>,
    /**
     * The form whose letters were handed over, for the reveal — the word's own text, or, where
     * that cannot be written standing alone, the variant it was asked through
     * ([WordScrambleAvailability.spellings]).
     */
    val display: String,
    /** Shown on the reveal only — the drill never puts the meaning on screen before the answer. */
    val gloss: String,
)
