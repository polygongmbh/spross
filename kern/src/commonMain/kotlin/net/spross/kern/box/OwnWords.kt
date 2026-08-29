package net.spross.kern.box

import kotlin.time.Instant
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Language
import net.spross.kern.model.Realization
import net.spross.kern.model.kindEmoji
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
 *
 * Written in only ONE language it is a SUGGESTION: the learner noticed a gap and
 * wrote down the half they had. It joins nothing and is never scheduled ([cards]
 * skips it), it simply waits — either for the other half, or to be read off a
 * report and answered in the catalog itself.
 */
data class OwnWord(
    /** Card id, always [OwnWords.ID_PREFIX]ed, so a catalog slug can never mint one. */
    val id: String,
    val kind: CardKind,
    val emoji: String?,
    /** language → the word in it; a language absent here simply does not join. */
    val texts: Map<Language, String>,
    /**
     * When it was written — stamped by [BoxEngine.addOwnWord], never by the caller.
     * What "only what is new" filters on when the learner copies or mails their words
     * out ([Feedback]); a suggestion never earns a schedule, so its schedule's
     * `addedAt` cannot answer this. Defaults to the beginning of time: a word from
     * before the box recorded this reads as old, never as brand new.
     */
    val addedAt: Instant = Instant.DISTANT_PAST,
) {
    /** Whether this word still waits for one of the profile's two languages. */
    fun isSuggestion(source: Language, target: Language): Boolean =
        texts[source] == null || texts[target] == null
}

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
     * The area's icon. Language-neutral display metadata, owned here for the same
     * reason a catalog area's emoji is owned by the catalog: two apps carrying their
     * own map is how the icon ends up existing on one platform only.
     */
    const val EMOJI: String = "✍️"

    /**
     * Own words sort behind every catalog concept: automatic growth walks seed
     * order, and a word the learner asked for by name is packed on the spot
     * anyway — it never needs growth to reach it.
     */
    const val SEED_BASE: Int = 1_000_000

    /**
     * The pictures offered by a tap when writing a word: the KIND glyphs the box
     * already draws (`kindEmoji`), never a grab-bag of things.
     *
     * A learner writing a word has no picture in mind for it — what they do know is
     * what kind of word it is, and those five glyphs are the vocabulary the rest of
     * the app has already taught them to read. Anything else would be decoration
     * chosen by whoever wrote the list.
     */
    val QUICK_EMOJI: List<String> = CardKind.entries.map(::kindEmoji)

    /**
     * The most pictures a word may carry. Two, not one: a flag pairs with a thing,
     * and a skin-toned or gendered glyph is already several code points that must
     * not read as two pictures' worth of room.
     */
    const val MAX_EMOJI: Int = 2

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

    /**
     * A word as the learner just wrote it, with no age of its own yet —
     * [BoxEngine.addOwnWord] stamps that. Exists because a Kotlin default parameter
     * does not survive the ObjC export, so Swift would otherwise have to mint an
     * [Instant] it has no business knowing about.
     */
    fun write(id: String, kind: CardKind, emoji: String?, texts: Map<Language, String>): OwnWord =
        OwnWord(id = id, kind = kind, emoji = emoji, texts = texts)

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
