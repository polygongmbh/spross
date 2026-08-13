package net.spross.kern.session

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind

/**
 * Produce grading with the whole join in view (contract §6).
 *
 * [AnswerNormalizer] sees one card, so a word that is really ANOTHER concept's
 * answer can land inside this card's typo budget — sw `kufunga` (schließen) is
 * one edit from `kufungua` (öffnen) and graded as a forgiven slip. A form the
 * catalog already accepts elsewhere is that word, never a slip of this one:
 * it grades [Match.OtherWord], which names what the learner actually wrote.
 *
 * Exactness is the whole test: only forms the owning concept would itself accept
 * exactly count, so the check never widens what counts as wrong — it re-labels a
 * miss, and withdraws typo credit where the catalog can prove the word is taken.
 */
class CatalogAnswerGrader(
    private val normalizer: AnswerNormalizer,
    cards: List<Card>,
) {

    /** Comparison form → the concepts that accept it exactly, seed order. */
    private val owners: Map<String, List<Card>> = buildOwners(cards)

    /**
     * [Match.Exact] whenever this card accepts the input — a form two concepts
     * share belongs to the prompted one first. Otherwise the other concept's
     * word if the catalog owns the input, else the plain one-card verdict.
     */
    fun grade(input: String, card: Card): Match {
        val direct = normalizer.evaluate(input, card)
        if (direct == Match.Exact) return direct
        return otherWord(input, card) ?: direct
    }

    private fun otherWord(input: String, card: Card): Match.OtherWord? {
        // why: the base concept's word is deliberately lenient on a feminine card
        // (§3 demotes it to the feminine correction) — it must not be re-labeled
        // as somebody else's word.
        val skipped = setOfNotNull(card.id, card.feminineOf)
        val typed = normalizer.comparisonForms(input, verbLeniency = false)
        // why: dropping a citation prefix off the INPUT is the verb rule, so it
        // may only reach verb owners — a noun that happens to start like a stem
        // does not own "kupika".
        val stemmed = normalizer.comparisonForms(input, verbLeniency = true).drop(1)
        val hits = (
            typed.flatMap { owners[it].orEmpty() } +
                stemmed.flatMap { owners[it].orEmpty() }.filter { it.kind == CardKind.Verb }
            )
            .filter { it.id !in skipped }
            .distinctBy { it.id }
        if (hits.isEmpty()) return null
        return Match.OtherWord(
            word = hits.first().target.text,
            meanings = hits.map { it.source.text }.distinct(),
        )
    }

    private fun buildOwners(cards: List<Card>): Map<String, List<Card>> {
        val index = mutableMapOf<String, MutableList<Card>>()
        for (card in cards.sortedBy { it.seedIndex }) {
            val verb = card.kind == CardKind.Verb
            val accepted = listOf(card.target.text) + card.target.synonyms + card.target.variants
            for (form in accepted) {
                for (shape in normalizer.comparisonForms(form, verbLeniency = verb)) {
                    val holders = index.getOrPut(shape) { mutableListOf() }
                    if (holders.none { it.id == card.id }) holders += card
                }
            }
        }
        return index
    }
}
