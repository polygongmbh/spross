package net.spross.kern.session

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.nfcNormalized

/**
 * Produce grading with the whole join in view (`kern/docs/grading.md`).
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
 *
 * What the learner WROTE is not always the whole string: where the normalizer reached
 * its verdict by peeling a mistyped article, the remainder is the form that matched,
 * so it is probed too ([AnswerNormalizer.articlePeeledRemainder]) — otherwise a slip
 * behind a fumbled article bridges one concept's word to another unchallenged.
 *
 * The join answers a second question from the other side — which concepts a target form is
 * LISTED by, for a card asked what it means ([conceptsSharing]) — and that one is literal
 * rather than lenient, so the two indexes are built separately.
 */
class CatalogAnswerGrader(
    private val normalizer: AnswerNormalizer,
    cards: List<Card>,
) {

    /** Comparison form → the concepts that accept it exactly, seed order. */
    private val owners: Map<String, List<Card>> = buildOwners(cards)

    /** Printed form → the concepts that LIST it, seed order. */
    private val sharedForms: Map<String, List<Card>> = buildSharedForms(cards)

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

    /**
     * Every OTHER concept that LISTS [form] on its target side, seed order — what a
     * target-language merge means besides what [card] teaches (`catalog/README.md`:
     * sw `kuacha` is verlassen AND aufhören).
     *
     * Literal, where [grade] is lenient, and that is the whole difference between the two
     * indexes: [owners] answers "could this typing be that word" and forgives an article, a
     * citation prefix, a case; this answers "does another concept print this very form", and
     * a form that has to be bent to match is a different word. de `Arm` is not `arm` — nor by
     * ear, where the target is spoken with its article.
     *
     * [card]'s own id and its feminine base are skipped, as in [otherWord] and for the same
     * reason: neither is somebody else's meaning.
     */
    fun conceptsSharing(form: String, card: Card): List<Card> {
        val skipped = setOfNotNull(card.id, card.feminineOf)
        return sharedForms[key(form)].orEmpty().filter { it.id !in skipped }
    }

    private fun otherWord(input: String, card: Card): Match.OtherWord? {
        // why: the base concept's word is deliberately lenient on a feminine card
        // (§3 demotes it to the feminine correction) — it must not be re-labeled
        // as somebody else's word.
        val skipped = setOfNotNull(card.id, card.feminineOf)
        // why: a form the prompted card accepts belongs to it first (see [grade]), and a
        // fumbled article in front of it changes nothing about that — otherwise a word
        // two concepts share would be withdrawn from the one that was asked for.
        val peeled = normalizer.articlePeeledRemainder(input)
            ?.takeIf { normalizer.evaluate(it, card) != Match.Exact }
        // The whole string first, so a form owned outright names itself before the
        // remainder a mistyped article leaves behind does.
        val hits = (ownersOf(input) + ownersOf(peeled))
            .filter { it.id !in skipped }
            .distinctBy { it.id }
        if (hits.isEmpty()) return null
        return Match.OtherWord(
            word = hits.first().target.text,
            meanings = hits.map { it.source.text }.distinct(),
        )
    }

    /**
     * Every concept that accepts [text] exactly. Null (nothing was peeled) owns nothing.
     *
     * why: dropping a citation prefix off the INPUT is the verb rule, so it may only
     * reach verb owners — a noun that happens to start like a stem does not own "kupika".
     */
    private fun ownersOf(text: String?): List<Card> {
        if (text == null) return emptyList()
        val typed = normalizer.comparisonForms(text, verbLeniency = false)
        val stemmed = normalizer.comparisonForms(text, verbLeniency = true).drop(1)
        return typed.flatMap { owners[it].orEmpty() } +
            stemmed.flatMap { owners[it].orEmpty() }.filter { it.kind == CardKind.Verb }
    }

    private fun buildSharedForms(cards: List<Card>): Map<String, List<Card>> {
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
