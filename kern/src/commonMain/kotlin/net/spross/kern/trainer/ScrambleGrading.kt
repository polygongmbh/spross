package net.spross.kern.trainer

/**
 * What an arrangement is worth.
 *
 * There is no text to grade here: the atoms the learner moved are the very ones kern handed
 * out, so the only honest check is whether they stand in the order the phrase was authored in —
 * position by position, never as a bag of words.
 *
 * Atoms carry an identity ([ScrambleAtom.id]) because a phrase can print the same word twice
 * ("der Mann und der Hund") and the run has to know WHICH of the two the learner just moved.
 * For the verdict the two are interchangeable, and deliberately so: a learner who put the other
 * one there wrote the very sentence that was asked for, and failing them for it would be the
 * drill grading its own bookkeeping.
 *
 * v1 grades the single authored order. A phrase with more than one valid word order is a
 * catalog fact nothing in the current content carries, and the day one does it is a new
 * accepted-orders field, not a looser comparison here.
 */
internal object ScrambleGrading {

    /** Whether [placed] reads as [canonical], position for position. */
    fun isSolved(placed: List<ScrambleAtom>, canonical: List<ScrambleAtom>): Boolean =
        placed.size == canonical.size &&
            placed.indices.all {
                placed[it].id == canonical[it].id || placed[it].text == canonical[it].text
            }
}
