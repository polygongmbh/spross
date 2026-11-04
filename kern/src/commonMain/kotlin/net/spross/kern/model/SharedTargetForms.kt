package net.spross.kern.model

/**
 * Which concepts PRINT a given target form — the target language's own merges, read off
 * the join (`catalog/README.md`): sw `ndege` is Vogel AND Flugzeug, and a learner asked
 * what the word means is right with either.
 *
 * LITERAL but for a sentence's casing and punctuation, where grading a typed answer is lenient, and that is the whole difference
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
     * Every OTHER concept that lists [form], seed order — [card]'s own is not somebody else's meaning.
     */
    fun concepts(form: String, card: Card): List<Card> {
        return index[key(form)].orEmpty().filter { it.id != card.id }
    }

    private fun build(cards: Collection<Card>): Map<String, List<Card>> {
        val index = mutableMapOf<String, MutableList<Card>>()
        for (card in cards.sortedBy { it.seedIndex }) {
            for (form in listOf(card.target.text) + card.target.teaches + card.target.accepts + card.target.forms.map { it.written }) {
                val shape = key(form)
                if (shape.isEmpty()) continue
                val holders = index.getOrPut(shape) { mutableListOf() }
                if (holders.none { it.id == card.id }) holders += card
            }
        }
        return index
    }

    /**
     * The printed form itself, made comparable across encodings;
     * a form written as a sentence (`Karibu!`) drops its punctuation and sentence capital,
     * which are how it is written, not which word it is — sw `karibu` is near and welcome.
     */
    private fun key(form: String): String {
        val printed = nfcNormalized(form).trim()
        if (printed.isEmpty() || printed.last() !in SENTENCE_END) return printed
        val bare = printed.trim { it in SENTENCE_END || it in SENTENCE_START || it.isWhitespace() }
        return bare.replaceFirstChar { it.lowercase() }
    }
}

private const val SENTENCE_END = ".!?…"
private const val SENTENCE_START = "¡¿"

