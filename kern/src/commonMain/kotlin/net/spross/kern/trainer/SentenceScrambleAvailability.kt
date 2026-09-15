package net.spross.kern.trainer

import net.spross.kern.box.BoxState
import net.spross.kern.box.Inventory
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind

/**
 * What the sentence scramble can ASK of a box: the phrases whose word order is worth putting
 * back together.
 *
 * The whole join, never a growth bar.
 * Arranging is not recall — the atoms are handed over and the gloss stands beside them,
 * so a phrase built of words the learner has not met is exposure to an ORDER
 * rather than a question they cannot answer.
 * Nothing here reads scheduling at all, and nothing here is a device fact,
 * so unlike the letter drill this needs no capability port either.
 *
 * Built ONCE per run: it walks the whole join and tokenizes every phrase in it.
 */
object SentenceScrambleAvailability {

    /**
     * Below three atoms there is no word ORDER to speak of — a two-word phrase has one wrong
     * arrangement, which the learner would fall into or out of by chance.
     */
    const val MIN_ATOMS: Int = 3

    /**
     * The phrases worth arranging, in seed order,
     * each already cut into the atoms an arrangement moves.
     * Cut here rather than per question: tokenizing is a sweep of the whole join.
     */
    data class Report(val phrases: List<Phrase>) {

        val drillAvailable: Boolean get() = phrases.isNotEmpty()

        /**
         * The Sprosse ceiling: one Sprosse per atom the longest phrase carries past [MIN_ATOMS],
         * so the top of the ladder is a phrase this box actually holds rather than a number.
         */
        val maxLevel: Int
            get() = maxOf(1, (phrases.maxOfOrNull { it.words } ?: MIN_ATOMS) - MIN_ATOMS + 1)

        /**
         * The longest phrase [level] may ask, in WORDS — a ceiling, not a floor.
         *
         * Each Sprosse ADDS a length and keeps every one below it, the atlas' "Dazu:" model: a
         * learner who has just reached five-word phrases is not done with four-word ones, and
         * dropping the short phrases as the ladder rose was what made a box of three-, three-,
         * four- and seven-word phrases answer Sprosse 2 with the seven-word one.
         */
        fun atomsAt(level: Int): Int = maxOf(1, level) + MIN_ATOMS - 1

        /** The phrases [level] admits: everything from [MIN_ATOMS] words up to [atomsAt]. */
        fun phrasesAt(level: Int): List<Phrase> = phrases.filter { it.words <= atomsAt(level) }
    }

    /** One eligible phrase and the chips it was cut into. */
    data class Phrase(val card: Card, val atoms: List<ScrambleAtom>) {

        /**
         * How long the phrase is as an ORDER. A punctuation chip is placed like any other but
         * carries no word order to get right, so the ladder and the floor count words alone —
         * otherwise "Vorsicht, heiß!" would pass for a four-atom phrase.
         */
        val words: Int get() = atoms.count { !ScrambleTokenizer.isMark(it.text) }
    }

    /**
     * The full report.
     *
     * Every phrase the join carries stays in, suspended and unscheduled ones among them:
     * suspending says stop REVIEWING a card, which an arrangement is not.
     *
     * A phrase whose text carries `…` is left out: that ellipsis is an authored fill-in-blank
     * pattern, so the words around it are a frame rather than a sentence in an order.
     *
     * The chips come out of the join rather than the phrase alone, because whether the leading
     * capital is the word's own is a question only the rest of the language can answer
     * ([ScrambleCapitals]).
     */
    fun report(box: BoxState): Report {
        val cards = Inventory.joinedCards(box)
        val inherent = ScrambleCapitals.inherent(cards)
        return Report(
            cards
                .filter { it.kind == CardKind.Phrase }
                .filter { '…' !in it.target.text }
                .map {
                    Phrase(it, ScrambleCapitals.neutralized(ScrambleTokenizer.atoms(it.target.text), inherent))
                }
                .filter { it.words >= MIN_ATOMS },
        )
    }

    /** Whether the drill exists at all — the hub-chip predicate. */
    fun drillExists(box: BoxState): Boolean = report(box).drillAvailable
}
