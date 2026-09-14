package net.spross.kern.trainer

import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.Inventory
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind

/**
 * What the word scramble can ASK of a box: the words whose spelling is worth writing back out
 * of its own letters.
 *
 * The bar is the DISPLAY one ([BoxEngine.isConsolidated]), not the growing one the other drill
 * pools read, and deliberately so: a scrambled word is no cue at all for a word the learner
 * cannot already produce, so this drill is for spelling a word they have rather than meeting
 * one they have not.
 *
 * Nothing here is a device fact, so unlike the letter drill this needs no capability port.
 */
object WordScrambleAvailability {

    /**
     * Below four letters the first-and-last Sprosse leaves nothing worth mixing, and the word
     * is guessable from its ends alone.
     */
    const val MIN_LETTERS: Int = 4

    /**
     * Below this many words the drill is the same handful every evening, and a run that ends
     * after four questions reads as the app having nothing to give. The chip stays away.
     */
    const val POOL_FLOOR: Int = 5

    /** The kinds that are one word to spell; a phrase is the other drill's. */
    private val wordKinds = setOf(CardKind.Noun, CardKind.Verb, CardKind.Adjective)

    /** The eligible words, in seed order. Built ONCE per run: it walks the whole join. */
    data class Report(val words: List<Card>) {

        val drillAvailable: Boolean get() = words.size >= POOL_FLOOR

        /** The Sprosse ceiling is the masking ladder's — the pool decides nothing about it. */
        val maxLevel: Int get() = WordScrambleMasking.MAX_LEVEL
    }

    /**
     * The full report.
     *
     * A word the target writes as more than one token is out: a separable or reflexive verb
     * ("sich waschen") is two spellings and a word order, which is neither what this asks nor
     * what its ladder anchors.
     */
    fun report(box: BoxState): Report = Report(
        Inventory.active(box)
            .map { box.cards.getValue(it.cardId) }
            .filter { it.kind in wordKinds }
            .filter { BoxEngine.isConsolidated(box, it.id) }
            .filter { eligible(it.target.text) }
            .sortedWith(Inventory.seedOrder),
    )

    /** Whether the drill exists at all — the hub-chip predicate. */
    fun drillExists(box: BoxState): Boolean = report(box).drillAvailable

    private fun eligible(text: String): Boolean =
        ScrambleTokenizer.tokens(text).size == 1 && text.trim().length >= MIN_LETTERS
}
