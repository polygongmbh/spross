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
    /**
     * The phrase as AUTHORED, for the reveal — capital, full stop and all. The atoms carry
     * neither the sentence's own stop ([ScrambleTokenizer]) nor its positional capital
     * ([ScrambleCapitals]), so rejoining them is what the learner arranged, while this is what
     * the catalog teaches.
     */
    val display: String,
    /** Shown on the reveal only — the drill never puts the meaning on screen before the answer. */
    val gloss: String,
) {
    /** How many chips the arrangement takes — words and marks alike, since both are placed. */
    val size: Int get() = canonical.size

    /** How many of them carry a word ORDER — the Sprosse this question was drawn at. */
    val words: Int get() = canonical.count { !ScrambleTokenizer.isMark(it.text) }
}
