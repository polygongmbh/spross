package net.spross.kern.trainer

import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.Inventory
import net.spross.kern.model.APOSTROPHES
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind

/**
 * What the word scramble can ASK of a box: the words whose spelling is worth writing back out
 * of its own letters.
 *
 * The bar is the DISPLAY one ([BoxEngine.isConsolidated]), not the growing one the letter drill
 * reads, and deliberately so: a scrambled word is no cue at all for a word the learner cannot
 * already produce, so this drill is for spelling a word they have rather than meeting one they
 * have not. The sentence scramble reads no bar at all — an ORDER is not a word.
 *
 * Nothing here is a device fact, so unlike the letter drill this needs no capability port.
 */
object WordScrambleAvailability {

    /**
     * Below four LETTERS an anchored Sprosse leaves two to move, and two letters have exactly
     * one arrangement that is not the spelling — the same mix every time the word comes round.
     * Letters, never characters: sw "-pya" measures four only by counting a hyphen that is no
     * letter to hand over.
     */
    const val MIN_LETTERS: Int = 4

    /**
     * Below this many words the drill is the same handful every evening,
     * and a run that ends after a few questions reads as the app having nothing to give.
     * The chip stays away.
     */
    const val POOL_FLOOR: Int = 15

    /** The kinds that are one word to spell; a phrase is the other drill's. */
    private val wordKinds = setOf(CardKind.Noun, CardKind.Verb, CardKind.Adjective)

    /**
     * One word of the pool and every [spellable] form it may be handed over as, in authored
     * order. A word carries more than one only where its own text cannot be spelled standing
     * alone — see [spellings].
     */
    data class Spelling(val card: Card, val forms: List<String>) {

        /** The gentlest form on offer; the draw reaches for the shortest words first. */
        val shortest: Int get() = forms.minOf { it.letters }

        /** The longest form on offer — how high up the ladder this word can still be asked. */
        val reach: Int get() = forms.maxOf { it.letters }

        /** Every form long enough for a Sprosse whose floor is [letters]; empty ⇒ the word is short in all of them. */
        fun formsFrom(letters: Int): List<String> = forms.filter { it.letters >= letters }
    }

    /** The eligible words, in seed order. Built ONCE per run: it walks the whole join. */
    data class Report(val words: List<Spelling>) {

        val drillAvailable: Boolean get() = words.size >= POOL_FLOOR

        /**
         * The Sprosse ceiling, read off the POOL rather than off the masking ladder: the
         * highest Sprosse [POOL_FLOOR] words still clear the floor of, so no Sprosse exists that
         * the learner's own words cannot fill.
         *
         * [WordScrambleMasking] tops out at three — the opening letter anchored, then
         * nothing — and the Sprossen above it go on lengthening the word with nothing anchored,
         * which is where a well-grown box spends most of its climb.
         */
        val maxLevel: Int by lazy {
            val nth = words.map { it.reach }.sortedDescending().getOrNull(POOL_FLOOR - 1)
            maxOf(1, (nth ?: MIN_LETTERS) - MIN_LETTERS + 1)
        }

        /**
         * How many LETTERS a word must carry to be asked at [level] — one more per Sprosse,
         * from [MIN_LETTERS] at the foot.
         *
         * One letter a Sprosse rather than a wider band: the catalog's single words crowd into
         * four to eight letters and thin out from there, so a band of two would spend the
         * whole ladder inside that crowd and then leave its top Sprosse empty for anyone but a
         * learner who has grown the long tail.
         */
        fun lettersAt(level: Int): Int = MIN_LETTERS + maxOf(1, level) - 1
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
            .sortedWith(Inventory.seedOrder)
            .map { Spelling(it, spellings(it)) }
            .filter { it.forms.isNotEmpty() },
    )

    /** Whether the drill exists at all — the hub-chip predicate. */
    fun drillExists(box: BoxState): Boolean = report(box).drillAvailable

    /**
     * What [card] may be asked to spell out of loose letters: its own text where that stands as
     * a word, else — and ONLY where that text is not [writtenInLetters] — the concrete forms
     * its variants carry.
     *
     * A Swahili bound stem ("-baya") is no citation form a learner could write down: it is a
     * dash and an agreement slot. Every form it agrees into ("mbaya", "wabaya", "vibaya") is a
     * real spelling though, and one worth teaching, so the stem is asked through those rather
     * than dropped, and only a word with no spellable form at all leaves the pool.
     *
     * The fallback reaches no further. A word of several tokens ("sich waschen") is a word
     * order, which is not what this drill asks, and a word under the letter floor ("Oma",
     * "nah") is short in every form it has — reaching for a variant there would hand over a
     * DIFFERENT word ("Großmutter"; uk "він" → "вона" is not even the same person), which is
     * not the word spelled out.
     */
    fun spellings(card: Card): List<String> {
        val text = card.target.text.trim()
        if (ScrambleTokenizer.tokens(text).size != 1) return emptyList()
        if (spellable(text)) return listOf(text)
        if (writtenInLetters(text)) return emptyList()
        return card.target.variants.filter(::spellable)
    }

    /** One token [writtenInLetters], carrying at least [MIN_LETTERS] of them. */
    private fun spellable(form: String): Boolean {
        val word = form.trim()
        return ScrambleTokenizer.tokens(word).size == 1 &&
            writtenInLetters(word) &&
            word.letters >= MIN_LETTERS
    }

    /**
     * Nothing but letters — plus the apostrophe, which sw "ng'ombe" and uk "м'який" are SPELLED
     * with rather than merely punctuated by.
     *
     * The guard is the character class rather than the hyphen alone. Whatever a letter scramble
     * mixes has to BE a letter, or the Sprosse that anchors "the first letter" anchors a dash,
     * and the one above it shuffles that dash into the middle of the word.
     */
    private fun writtenInLetters(word: String): Boolean =
        word.all { it.isLetter() || it in APOSTROPHES }
}

/**
 * How long a spelling is to the ladder: LETTERS, never characters. sw "ng'ombe" is six letters
 * held together by an apostrophe, and the floor a Sprosse sets is about how much word there is
 * to read out of the mix rather than how wide it renders.
 */
internal val String.letters: Int get() = count { it.isLetter() }
