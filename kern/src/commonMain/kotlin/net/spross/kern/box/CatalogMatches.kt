package net.spross.kern.box

import net.spross.kern.model.Card
import net.spross.kern.model.articledForm
import net.spross.kern.model.caseFolded

/** How much of an own word the catalog card it was matched against agrees with. */
enum class MatchSide {
    /** Both halves. The kind that arrives ticked: there is nothing left to judge. */
    Both,

    /** The known half only — the catalog says the learning side differently. */
    KnownOnly,

    /** The learning half only — the catalog says the known side differently. */
    LearningOnly,
}

/** One word the learner wrote, beside the catalog word that has since caught up with it. */
data class CatalogMatch(
    val word: OwnWord,
    /** The catalog card it leans on — [BoxState.cards] holds it under this id. */
    val cardId: String,
    val side: MatchSide,
)

/**
 * The learner's own words against the catalog that grew past them.
 *
 * An own word is written because the catalog had none; catalogs grow, and the word lands in
 * one eventually. Nothing compared the two before, so the learner kept a private card for a
 * word the box also teaches — and moving to the catalog's meant losing the progress made on
 * theirs ([BoxEngine.mergeOwnWord] is what does not).
 *
 * What counts as the same word, and why it is not simply [BoxForms]'s question: a match
 * needs one side spelled EXACTLY as the catalog spells it, or both sides leaning
 * ([FormLikeness]). The exact side is what makes a one-sided match readable — the learner
 * wrote `taulo` where the catalog says `taula`, and the German half they share is what says
 * it is the same word rather than a neighbor. A lone leaning side would say nothing of the
 * kind, which is the noise `BoxForms` was written to reject.
 *
 * Suggestions take part on the single side they carry: the catalog answering a suggestion is
 * the whole of what it was written for. A remark names no word and never does. A pair this
 * profile cannot study has no side to compare under it and is absent from the list.
 *
 * Built once per open — a walk of every card, as [Briefing] and [BoxForms] are.
 */
object CatalogMatches {

    /**
     * Every own word the catalog has caught up with, written order, the unambiguous ones
     * first: a surface walks one list and starts a heading where the side turns.
     */
    fun of(state: BoxState): List<CatalogMatch> {
        val source = state.joinStamp.source
        val target = state.joinStamp.target
        val forms = CatalogForms(state)
        return state.ownWords
            .mapNotNull { word ->
                forms.matchOf(word.texts[source], word.texts[target])
                    ?.let { (cardId, side) -> CatalogMatch(word, cardId, side) }
            }
            .sortedBy { it.side.ordinal }
    }
}

/**
 * The catalog side of the box, indexed for the two questions a match asks: is this form one
 * the catalog writes, and does the other half of the card agree.
 *
 * Own-word cards are left out — a word cannot catch up with itself, and two own words
 * that say the same thing are the learner's to merge by hand.
 */
private class CatalogForms(state: BoxState) {

    private val cards: List<Card> =
        state.cards.values.filterNot { OwnWords.owns(it.id) }

    /** Every written form of a side, folded, against the cards that write it. */
    private val known = LinkedHashMap<String, MutableList<Card>>()
    private val learning = LinkedHashMap<String, MutableList<Card>>()

    init {
        for (card in cards) {
            put(known, card.source.text, card)
            card.source.teaches.forEach { put(known, it, card) }
            card.source.accepts.forEach { put(known, it, card) }
            put(learning, card.target.text, card)
            put(learning, articledForm(card.target.grammar["gender"], card.target.text), card)
            card.target.teaches.forEach { put(learning, it, card) }
            card.target.accepts.forEach { put(learning, it, card) }
        }
    }

    /** The best card for a word written [source] / [target], or null where none agrees. */
    fun matchOf(source: String?, target: String?): Pair<String, MatchSide>? {
        val onKnown = source?.let { known[caseFolded(it)] }.orEmpty()
        val onLearning = target?.let { learning[caseFolded(it)] }.orEmpty()
        // Both sides exact, or one exact and the other close enough to read as a correction.
        onKnown.firstOrNull { agrees(learning, it.target.text, target) }
            ?.let { return it.id to MatchSide.Both }
        onLearning.firstOrNull { agrees(known, it.source.text, source) }
            ?.let { return it.id to MatchSide.Both }
        // Neither side exact: both have to lean, which is what keeps a typed-over word
        // findable without every near-spelling in the catalog answering for it.
        leaningBoth(source, target)?.let { return it to MatchSide.Both }
        onKnown.firstOrNull()?.let { return it.id to MatchSide.KnownOnly }
        onLearning.firstOrNull()?.let { return it.id to MatchSide.LearningOnly }
        return null
    }

    private fun put(index: MutableMap<String, MutableList<Card>>, form: String, card: Card) {
        index.getOrPut(caseFolded(form)) { mutableListOf() }.add(card)
    }

    /**
     * Whether the card's other half agrees with [written]: the same form, one the card also
     * accepts, or one a slip or two off. A half the learner never wrote agrees with nothing —
     * a suggestion is matched on the side it HAS, and the missing one cannot vouch for it.
     */
    private fun agrees(
        index: Map<String, List<Card>>,
        cardText: String,
        written: String?,
    ): Boolean {
        val folded = caseFolded(written ?: return false)
        if (index[folded].orEmpty().any { it.target.text == cardText || it.source.text == cardText }) {
            return true
        }
        return leans(folded, caseFolded(cardText))
    }

    private fun leaningBoth(source: String?, target: String?): String? {
        val knownForm = caseFolded(source ?: return null)
        val learningForm = caseFolded(target ?: return null)
        return cards.firstOrNull {
            leans(knownForm, caseFolded(it.source.text)) &&
                leans(learningForm, caseFolded(it.target.text))
        }?.id
    }

    /** Two forms long enough to mean anything by it, spelled close enough to be one word. */
    private fun leans(one: String, other: String): Boolean =
        one.length >= FormLikeness.MIN_STEM && other.length >= FormLikeness.MIN_STEM &&
            FormLikeness.leans(one, other)
}
