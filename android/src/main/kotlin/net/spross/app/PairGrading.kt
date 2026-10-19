package net.spross.app

import net.spross.kern.box.BoxState
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.CatalogAnswerGrader

/**
 * How a review turn of the active pair is graded: [words] is the TARGET language's grading,
 * [meanings] the SOURCE language's, for the one turn typed in it — a card asked by ear
 * owes what the word MEANS, and the articles and typo budget it is measured under
 * are that language's own (`docs/review.md`).
 */
class PairGrading(val words: AnswerNormalizer, val meanings: AnswerNormalizer) {
    private var cachedProduceGrader: CatalogAnswerGrader? = null

    /**
     * Produce grading with the whole join in view: a form the catalog owns
     * elsewhere is that word, never a typo of this card's answer (`docs/grading.md`).
     * One pass over every accepted form the join carries — thousands of normalized
     * strings — so it is built on the first turn that asks and kept until [retire].
     * A card that arrives after the box moved is still graded against the box standing
     * now: everything that can move the join retires it.
     */
    fun produceGrader(state: BoxState): CatalogAnswerGrader =
        cachedProduceGrader ?: CatalogAnswerGrader(words, state.cards.values.toList())
            .also { cachedProduceGrader = it }

    fun retire() {
        cachedProduceGrader = null
    }
}
