package net.spross.kern.trainer

import net.spross.kern.box.BoxState
import net.spross.kern.box.Inventory
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.caseFolded

/**
 * What the sentence scramble can ASK of a box: the phrases whose word order is worth putting
 * back together.
 *
 * The whole join, never a growth bar.
 * Arranging is not recall: the words are GIVEN and only their order is withheld,
 * so what a phrase asks is the same question whether or not the learner holds its words,
 * and one built of words they have never met is exposure to an ORDER.
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
     * What an authored SENTENCE ends on.
     *
     * A phrase carrying none is a fragment — "auf dem Tisch", "zaidi au pungufu",
     * "in die Schule gehen", "zwei Uhr nachmittags" — and a fragment's order is IDIOM
     * rather than grammar, so the learner who arranges it the other way round is not wrong
     * in the way this drill marks them wrong.
     * The catalog writes one on 95% of the phrases long enough to arrange;
     * the rest are prepositional, infinitive and clock phrases to a one.
     * It is the authored convention that is read here, so a sentence left without its stop
     * drops out of the drill rather than being asked ambiguously.
     */
    const val TERMINATORS: String = ".?!"

    /**
     * How many phrases one Sprosse holds at most.
     * The ladder is cut into as few bands as keep each at or under it, split evenly,
     * so a pair's catalog of two hundred sentences climbs eight or nine Sprossen.
     */
    const val BAND_SIZE: Int = 24

    /**
     * The phrases worth arranging, in seed order,
     * each already cut into the atoms an arrangement moves.
     * Cut here rather than per question: tokenizing is a sweep of the whole join.
     *
     * [bandSize] is [BAND_SIZE] wherever a box is read; it is open only so a small fixture can
     * build a ladder more than one Sprosse tall.
     */
    data class Report(val phrases: List<Phrase>, val bandSize: Int) {

        // A second constructor rather than a default: defaults do not cross to Swift.
        constructor(phrases: List<Phrase>) : this(phrases, BAND_SIZE)

        val drillAvailable: Boolean get() = phrases.isNotEmpty()

        /**
         * Every phrase, easiest first: fewer words, then fewer letters —
         * a longer word is a longer read to place, so of two phrases with as many chips
         * the one written in shorter words comes first.
         * Seed order breaks what is left of a tie.
         */
        val byDifficulty: List<Phrase> by lazy {
            phrases.sortedWith(compareBy<Phrase> { it.words }.thenBy { it.letters })
        }

        /** The Sprosse ceiling: one Sprosse per band, never fewer than one. */
        val maxSprosse: Int get() = maxOf(1, (phrases.size + bandSize - 1) / maxOf(1, bandSize))

        /**
         * The phrases [sprosse] asks: its own band of [byDifficulty], and nothing from the bands
         * around it — so a miss drops to phrases genuinely easier than the one missed,
         * and every Sprosse, the top one included, is as many phrases as the next.
         */
        fun phrasesAt(sprosse: Int): List<Phrase> {
            val band = sprosse.coerceIn(1, maxSprosse) - 1
            val size = byDifficulty.size
            return byDifficulty.subList(band * size / maxSprosse, (band + 1) * size / maxSprosse)
        }
    }

    /** One eligible phrase and the chips it was cut into. */
    data class Phrase(
        val card: Card,
        val atoms: List<ScrambleAtom>,
        val alternativeOrders: List<List<ScrambleAtom>> = emptyList(),
    ) {

        /**
         * How long the phrase is as an ORDER. A punctuation chip is placed like any other but
         * carries no word order to get right, so the difficulty and the floor count words alone —
         * otherwise "Vorsicht, heiß!" would pass for a four-atom phrase.
         */
        val words: Int get() = atoms.count { !ScrambleTokenizer.isMark(it.text) }

        /** How much there is to read across those words — marks and spaces left out. */
        val letters: Int get() = atoms.filter { !ScrambleTokenizer.isMark(it.text) }.sumOf { it.text.length }
    }

    /**
     * The full report.
     *
     * Every phrase the join carries stays in, suspended and unscheduled ones among them:
     * suspending says stop REVIEWING a card, which an arrangement is not.
     *
     * A phrase whose text carries `…` is left out: that ellipsis is an authored fill-in-blank
     * pattern, so the words around it are a frame rather than a sentence in an order.
     * One that ends on no [TERMINATORS] is left out too — it was written as a fragment.
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
                .filter { card -> card.target.text.trimEnd().lastOrNull()?.let(TERMINATORS::contains) == true }
                .map { card ->
                    val atoms = ScrambleCapitals.neutralized(ScrambleTokenizer.atoms(card.target.text), inherent)
                    val alts = card.target.orders.mapNotNull { arranged(atoms, ScrambleTokenizer.atoms(it)) } +
                        listOfNotNull(ScrambleCommaSwap.of(atoms))
                    Phrase(card, atoms, alts)
                }
                .filter { it.words >= MIN_ATOMS },
        )
    }

    /**
     * [order] re-spelled in the chips of [atoms] — an authored order is written plain, while the
     * chips carry the stress marks and the dropped positional capital the learner will place —
     * or null when it is not a permutation of them.
     * A comma the phrase does not print has no chip to place, so an order that sets one off
     * ("Yesterday, I hurt myself.") is read without it.
     */
    private fun arranged(atoms: List<ScrambleAtom>, order: List<ScrambleAtom>): List<ScrambleAtom>? {
        val free = atoms.toMutableList()
        val spelled = if (atoms.any { it.text == "," }) order else order.filter { it.text != "," }
        return spelled.map { atom ->
            val at = free.indexOfFirst { caseFolded(it.text) == caseFolded(atom.text) }
            if (at < 0) return null
            free.removeAt(at)
        }.takeIf { free.isEmpty() }
    }

    /** Whether the drill exists at all — the hub-chip predicate. */
    fun drillExists(box: BoxState): Boolean = report(box).drillAvailable
}
