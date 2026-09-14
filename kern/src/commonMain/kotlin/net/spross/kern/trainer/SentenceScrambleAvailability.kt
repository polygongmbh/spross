package net.spross.kern.trainer

import net.spross.kern.box.BoxState
import net.spross.kern.box.Growth
import net.spross.kern.box.Inventory
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind

/**
 * What the sentence scramble can ASK of a box: the phrases whose word order is worth putting
 * back together.
 *
 * One fact, and it is the box's own — a phrase the learner has not unlocked yet
 * ([Growth.isPhraseUnlocked]) is made of words they do not hold, so arranging it would be
 * guessing rather than syntax. Nothing here is a device fact, so unlike the letter drill this
 * needs no capability port at all.
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
     * Below this many phrases the drill is the same handful every evening, and a run that ends
     * after three questions reads as the app having nothing to give. The chip stays away.
     */
    const val POOL_FLOOR: Int = 3

    /**
     * The unlocked phrases, in seed order, each already cut into the atoms an arrangement
     * moves. Cut here rather than per question: tokenizing is a sweep of the whole join.
     */
    data class Report(val phrases: List<Phrase>) {

        val drillAvailable: Boolean get() = phrases.size >= POOL_FLOOR

        /**
         * The Sprosse ceiling: one Sprosse per atom the longest phrase carries past [MIN_ATOMS],
         * so the top of the ladder is a phrase this box actually holds rather than a number.
         */
        val maxLevel: Int
            get() = maxOf(1, (phrases.maxOfOrNull { it.words } ?: MIN_ATOMS) - MIN_ATOMS + 1)

        /** How many WORDS a phrase must carry to be asked at [level]. */
        fun atomsAt(level: Int): Int = maxOf(1, level) + MIN_ATOMS - 1
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
     * A suspended phrase is left out — suspending says stop asking this — while a phrase with no
     * schedule at all stays in: the unlock gate is about the COMPONENTS, and a phrase the learner
     * has never been shown is exactly what this drill prepares them for.
     *
     * A phrase whose text carries `…` is left out too: that ellipsis is an authored fill-in-blank
     * pattern, so the words around it are a frame rather than a sentence in an order.
     */
    fun report(box: BoxState): Report = Report(
        Inventory.joinedCards(box)
            .filter { it.kind == CardKind.Phrase }
            .filter { box.scheduling[it.id]?.suspended != true }
            .filter { Growth.isPhraseUnlocked(box, it) }
            .filter { '…' !in it.target.text }
            .map { Phrase(it, ScrambleTokenizer.atoms(it.target.text)) }
            .filter { it.words >= MIN_ATOMS },
    )

    /** Whether the drill exists at all — the hub-chip predicate. */
    fun drillExists(box: BoxState): Boolean = report(box).drillAvailable
}
