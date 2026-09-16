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
 * The canonical order is the authored `text`; [alternatives] are the word-order permutations
 * the catalog declares via `orders` — grammatically valid rearrangements whose meaning may
 * differ, so the gloss is suppressed when one matches.
 */
internal object ScrambleGrading {

    /** Whether [placed] reads as [canonical] or any of the [alternatives], position for position. */
    fun isSolved(
        placed: List<ScrambleAtom>,
        canonical: List<ScrambleAtom>,
        alternatives: List<List<ScrambleAtom>> = emptyList(),
    ): Boolean = matchesCanonical(placed, canonical) || alternatives.any { matchesByText(placed, it) }

    /** Whether [placed] matches the authored order — by identity or by text for duplicate words. */
    fun matchesCanonical(placed: List<ScrambleAtom>, canonical: List<ScrambleAtom>): Boolean =
        placed.size == canonical.size &&
            placed.indices.all {
                placed[it].id == canonical[it].id || placed[it].text == canonical[it].text
            }

    /** Text-only positional match — for alternative orderings whose atom ids are independent. */
    private fun matchesByText(placed: List<ScrambleAtom>, order: List<ScrambleAtom>): Boolean =
        placed.size == order.size &&
            placed.indices.all { placed[it].text == order[it].text }
}
