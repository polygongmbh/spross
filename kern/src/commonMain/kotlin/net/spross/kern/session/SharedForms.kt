package net.spross.kern.session

import net.spross.kern.model.Card
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.Realization
import net.spross.kern.model.article
import net.spross.kern.model.articledForm
import net.spross.kern.model.splitArticle

/**
 * Which concepts show a word alike on one side of the join — that side's language merging
 * what the other splits: target sw `ndege` is Vogel AND Flugzeug ([printed]),
 * source de `gehen` is to go AND to walk ([prompts]).
 *
 * A form is keyed as the learner sees and hears it: with its article in front where it carries one
 * (the realization's for its text and `accepts`, a tagged form's own), in [language]'s [ComparisonShape].
 * The shape is the grader's own character pass, so case and punctuation are how a word is written,
 * not which word it is — sw `Karibu!` is `karibu`.
 * The article is part of which word it is — de `der Arm` is not `arm`, nor `der Husten` `husten` —
 * and none of the grader's leniency applies: an article is never dropped, a citation prefix never.
 * A `teaches` entry is another word, whose article the catalog does not carry.
 */
internal class SharedForms private constructor(
    private val language: LanguageInfo,
    cards: Collection<Card>,
    private val side: (Card) -> Realization,
    private val listed: (Realization) -> List<Pair<String, String?>>,
) {

    private val shape = ComparisonShape(language)

    private val index: Map<String, List<Card>> = build(cards)

    /**
     * Every OTHER concept that shows [form], seed order — [card]'s own is not somebody else's meaning.
     * [form] is keyed as [card] lists it, article and all; a form it does not list stands bare.
     */
    fun concepts(form: String, card: Card): List<Card> {
        val article = listed(side(card)).firstOrNull { it.first == form }?.second
        return index[key(form, article)].orEmpty().filter { it.id != card.id }
    }

    private fun build(cards: Collection<Card>): Map<String, List<Card>> {
        val index = mutableMapOf<String, MutableList<Card>>()
        for (card in cards.sortedBy { it.seedIndex }) {
            for ((form, article) in listed(side(card))) {
                val key = key(form, article)
                if (key.isEmpty()) continue
                val holders = index.getOrPut(key) { mutableListOf() }
                if (holders.none { it.id == card.id }) holders += card
            }
        }
        return index
    }

    /** An `accepts` entry authored with an article of its own keeps that one. */
    private fun key(form: String, article: String?): String {
        val shown = if (article == null || splitArticle(form, language.articles).first != null) form else articledForm(article, form)
        return shape.tokenize(shown).joinToString(" ")
    }

    companion object {
        /** Every form a target realization prints: what a target form MEANS besides this card. */
        fun printed(language: LanguageInfo, cards: Collection<Card>): SharedForms =
            SharedForms(language, cards, Card::target) { side ->
                val article = side.article
                listOf(side.text to article) +
                    side.teaches.map { it to null } +
                    side.accepts.map { it to article } +
                    side.forms.map { it.text to it.article }
            }

        /** The source text a produce prompt shows: which concepts a prompt stands for. */
        fun prompts(language: LanguageInfo, cards: Collection<Card>): SharedForms =
            SharedForms(language, cards, Card::source) { side -> listOf(side.text to side.article) }
    }
}
