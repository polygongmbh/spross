package net.spross.kern.trainer

import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.Inventory
import net.spross.kern.catalog.OppositePair
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.nfcNormalized

/**
 * What the opposites drill can ASK of a box: every word it holds whose catalog opposite it
 * holds too, keyed by how the word is WRITTEN in the language being learned.
 *
 * The bar is the arrived one ([BoxEngine.arrivedCardIds]), the letter drill's: naming the
 * opposite is a second retrieval of a word already met, not a first sight of it.
 *
 * Two concepts written alike are one prompt — de "ausziehen" is moving out AND taking a
 * garment off, so it is asked once and both "einziehen" and "anziehen" answer it. Every
 * opposite of every concept the form prints counts, held or not: a right answer the
 * learner has not met yet is still right, and the reveal is where they meet it.
 */
object OppositesAvailability {

    /**
     * Below this many prompts the drill is the same handful every evening, and a run that
     * ends after a few questions reads as the app having nothing to give. The chip stays away.
     */
    const val POOL_FLOOR: Int = 12

    /** The Sprosse of a plain adjective or adverb (weiß → schwarz, oben → unten). */
    const val ADJECTIVES: Int = 1

    /** The Sprosse of a verb or noun (kaufen → verkaufen). */
    const val VERBS: Int = 2

    /** The Sprosse of a prompt with more than one opposite, whatever its kind (alt → neu, jung). */
    const val SEVERAL: Int = 3

    /** One of the right answers, with what it means in the learner's own language. */
    data class Opposite(val card: Card, val text: String, val gloss: String)

    /**
     * One question: a [form] as the box writes it, the held [card] it is asked through, and
     * every [opposites] entry that answers it.
     */
    data class Prompt(
        val card: Card,
        val form: String,
        /** What the form means — every concept printing it, so a merge reads as one. */
        val gloss: String,
        val opposites: List<Opposite>,
    ) {
        /** Bands, not floors: a prompt is asked on exactly one Sprosse. */
        val sprosse: Int
            get() = when {
                opposites.size > 1 -> SEVERAL
                card.kind == CardKind.Adjective -> ADJECTIVES
                else -> VERBS
            }
    }

    /** The askable prompts, in seed order. Built ONCE per run: it walks the whole join. */
    data class Report(val prompts: List<Prompt>) {

        val drillAvailable: Boolean get() = prompts.size >= POOL_FLOOR

        /** The highest band anything stands in; an empty band below it is climbed past. */
        val maxSprosse: Int get() = prompts.maxOfOrNull { it.sprosse } ?: 1

        fun promptsAt(sprosse: Int): List<Prompt> = prompts.filter { it.sprosse == sprosse }
    }

    fun report(box: BoxState, pairs: List<OppositePair>): Report {
        val oppositesOf = mutableMapOf<String, MutableSet<String>>()
        for (pair in pairs) {
            oppositesOf.getOrPut(pair.first) { mutableSetOf() } += pair.second
            oppositesOf.getOrPut(pair.second) { mutableSetOf() } += pair.first
        }
        val paired = box.cards.values.filter { it.id in oppositesOf }
        val printing = paired.groupBy { form(it) }
        val held = BoxEngine.arrivedCardIds(box).toSet()
        val prompts = printing.mapNotNull { (form, cards) ->
            val opposites = cards
                .flatMap { oppositesOf.getValue(it.id) }
                .mapNotNull { box.cards[it] }
                .filter { form(it) != form }
                .sortedWith(Inventory.seedOrder)
                .distinctBy { form(it) }
            val asked = cards.sortedWith(Inventory.seedOrder).firstOrNull { card ->
                card.id in held && oppositesOf.getValue(card.id).any { it in held && box.cards[it]?.let(::form) != form }
            } ?: return@mapNotNull null
            Prompt(
                card = asked,
                form = asked.target.text,
                gloss = cards.sortedWith(Inventory.seedOrder).joinToString(" · ") { it.source.text },
                opposites = opposites.map { card ->
                    val gloss = printing.getValue(form(card)).sortedWith(Inventory.seedOrder)
                        .joinToString(" · ") { it.source.text }
                    Opposite(card, card.target.text, gloss)
                },
            )
        }
        return Report(prompts.sortedWith(compareBy(Inventory.seedOrder) { it.card }))
    }

    /** Whether the drill exists at all — the hub-chip predicate. */
    fun drillExists(box: BoxState, pairs: List<OppositePair>): Boolean = report(box, pairs).drillAvailable

    /**
     * How a word reaches the learner, exactly — case kept, as the catalog's collision rule
     * keeps it (`Husten`/`husten` stay apart).
     */
    private fun form(card: Card): String = nfcNormalized(card.target.text).trim()
}
