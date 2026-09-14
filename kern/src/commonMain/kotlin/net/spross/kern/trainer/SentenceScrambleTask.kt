package net.spross.kern.trainer

import net.spross.kern.model.Language

/**
 * One sentence-scramble question. Pure data: the app offers [shuffled] to be arranged, checks
 * nothing itself, and reveals [display] (plus [gloss]) once the arrangement is in.
 *
 * [shuffled] and [canonical] hold the SAME atoms — the shuffle is a reordering kern drew from
 * the run's one `Random`, so both platforms deal the same question from the same seed and
 * neither is left to shuffle for itself.
 */
data class SentenceScrambleTask(
    /** The phrase's card id — what the drill has answered, never a schedule it writes. */
    val cardId: String,
    /** The language the phrase is in: the one being learned. */
    val language: Language,
    /** The atoms in the order they were dealt out. */
    val shuffled: List<ScrambleAtom>,
    /** The atoms in the order the phrase was authored in — what an arrangement is measured by. */
    val canonical: List<ScrambleAtom>,
    /** The phrase as authored, for the reveal. */
    val display: String,
    /** Shown on the reveal only — the drill never puts the meaning on screen before the answer. */
    val gloss: String,
) {
    /** How many atoms the arrangement takes — the Sprosse this question was drawn at. */
    val size: Int get() = canonical.size
}
