package net.spross.kern.box

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Language
import net.spross.kern.model.Realization
import net.spross.kern.model.nfcNormalized

/**
 * A word the learner wrote themselves, because the catalog had none.
 *
 * It is NOT catalog content and never becomes any: it is persisted with the box,
 * in an area of its own, so a catalog that grows can neither collide with it nor
 * quietly reclaim it. [texts] is keyed by language exactly as the catalog keys a
 * concept's realizations, which buys the same coverage rule for free — a word
 * written in two languages joins the profiles that pair them, and is inert in
 * every other, rather than being lost.
 */
data class OwnWord(
    /** Card id, always [OwnWords.ID_PREFIX]ed, so a catalog slug can never mint one. */
    val id: String,
    val kind: CardKind,
    val emoji: String?,
    /** language → the word in it; a language absent here simply does not join. */
    val texts: Map<Language, String>,
)

/** The rules that turn the learner's own words into cards the box can hold. */
object OwnWords {
    /**
     * The one area own words live in. Not a catalog folder name — the catalog must
     * stay free to add any area it likes without landing on top of them.
     */
    const val AREA: String = "own"

    /** Every own-word id starts with it; nothing in the catalog may. */
    const val ID_PREFIX: String = "own:"

    /**
     * Own words sort behind every catalog concept: automatic growth walks seed
     * order, and a word the learner asked for by name is packed on the spot
     * anyway — it never needs growth to reach it.
     */
    const val SEED_BASE: Int = 1_000_000

    /**
     * Plain-word by default. The catch-all kind is never phrase-gated and never
     * verb-prefix-stripped, so a word written by hand is studiable whatever it
     * turns out to be.
     */
    val DEFAULT_KIND: CardKind = CardKind.Adjective

    /**
     * The cards [words] contribute to a (source, target) join: one per word written
     * in BOTH languages, in the order they were added.
     */
    fun cards(words: List<OwnWord>, source: Language, target: Language): List<Card> =
        words.mapIndexedNotNull { position, word ->
            val known = word.texts[source] ?: return@mapIndexedNotNull null
            val learning = word.texts[target] ?: return@mapIndexedNotNull null
            Card(
                id = word.id,
                kind = word.kind,
                area = AREA,
                emoji = word.emoji,
                seedIndex = SEED_BASE + position,
                components = emptyList(),
                feminineOf = null,
                source = Realization(lang = source, text = known),
                target = Realization(lang = target, text = learning),
                promptFeminineMarker = false,
            )
        }

    /** Whether this card id belongs to a word the learner wrote. */
    fun owns(cardId: String): Boolean = cardId.startsWith(ID_PREFIX)

    /**
     * A free id for a new word, readable rather than opaque so a box document can
     * still be read by a human. [taken] are the ids already in use; a collision
     * (the same word written twice, or two words that fold alike) counts up.
     */
    fun mint(text: String, taken: Set<String>): String {
        val stem = nfcNormalized(text).lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .ifEmpty { "word" }
        val first = ID_PREFIX + stem
        if (first !in taken) return first
        var counter = 2
        while ("$first-$counter" in taken) counter++
        return "$first-$counter"
    }
}
