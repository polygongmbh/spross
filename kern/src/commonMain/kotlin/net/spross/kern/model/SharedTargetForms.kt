package net.spross.kern.model

/**
 * Which concepts PRINT a given target form — the target language's own merges, read off
 * the join (`catalog/README.md`): sw `ndege` is Vogel AND Flugzeug, and a learner asked
 * what the word means is right with either.
 *
 * LITERAL, where grading a typed answer is lenient, and that is the whole difference
 * between this and `CatalogAnswerGrader`'s owner index: that one asks "could this typing be
 * that word" and forgives an article, a citation prefix, a case; this asks "does another
 * concept print this very form", and a form that has to be bent to match is a different
 * word. de `Arm` is not `arm` — nor by ear, where the target is spoken with its article.
 *
 * Every surface that asks what a target form MEANS reads it: what a typed meaning is
 * credited against, what a reveal names, and which cards the watch must not offer as one
 * another's wrong answer.
 */
class SharedTargetForms(cards: Collection<Card>) {

    private val index: Map<String, List<Card>> = build(cards)

    /**
     * Every OTHER concept that lists [form], seed order. [card]'s own id and its feminine
     * base are skipped: neither is somebody else's meaning.
     */
    fun concepts(form: String, card: Card): List<Card> {
        val skipped = setOfNotNull(card.id, card.feminineOf)
        return index[key(form)].orEmpty().filter { it.id !in skipped }
    }

    private fun build(cards: Collection<Card>): Map<String, List<Card>> {
        val index = mutableMapOf<String, MutableList<Card>>()
        for (card in cards.sortedBy { it.seedIndex }) {
            for (form in listOf(card.target.text) + card.target.synonyms + card.target.variants) {
                val shape = key(form)
                if (shape.isEmpty()) continue
                val holders = index.getOrPut(shape) { mutableListOf() }
                if (holders.none { it.id == card.id }) holders += card
            }
        }
        return index
    }

    /** The printed form itself, only made comparable across encodings. */
    private fun key(form: String): String = nfcNormalized(form).trim()
}
