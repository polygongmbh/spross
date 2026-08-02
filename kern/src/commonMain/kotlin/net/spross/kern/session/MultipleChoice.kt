package net.spross.kern.session

import kotlin.math.abs
import net.spross.kern.model.CardKind

/**
 * Distractor selection for multiple-choice presentation — OS-independent, so
 * the watch, the phone and Android all offer the same tiles.
 *
 * Callers pass the option-side form of the answer and of every candidate: a
 * question asks for ONE side (recognition asks for the source meaning,
 * production for the target word), and reading a candidate on its own side
 * instead of the prompt's would mix the two languages on screen.
 *
 * Nothing but MEANING may separate the answer from the options standing next to
 * it. Three things otherwise do: a word class the other options don't share —
 * every Swahili verb wears `ku`, every German noun a capital — the sentence a
 * text closes itself as, and, within both, the shape of the string. [Option]
 * carries what the first needs; [optionForm] takes the marker off the writing
 * where the class can't be matched, [sentenceShape] keeps a question among
 * questions, and [shapeDistance] settles the rest.
 */
object MultipleChoice {

    /** Tiles per question: the answer plus three distractors. */
    const val OPTION_COUNT: Int = 4

    /**
     * Ranked distractors kept per question, so the tiles vary between rounds:
     * the caller picks three of these, and a snapshot that ships the shortlist
     * offers the same handful until the next push.
     *
     * Sized for variety, not for the wire — trim it first if the watch snapshot
     * ever crowds its size cap (kern README §7).
     */
    const val SHORTLIST: Int = 10

    /** How much a differing part count outweighs a differing length. */
    private const val PART_PENALTY: Int = 6

    /** A catalog text that is a bound stem — Swahili `-zuri` takes a class prefix. */
    private const val BOUND_STEM: String = "-"

    /**
     * One word as it can be offered: the [text] a learner reads on the asked
     * side, plus the two facts that decide whether it can stand beside the
     * answer without being told apart from it by anything but meaning.
     */
    data class Option(val text: String, val kind: CardKind, val area: String)

    /**
     * How a text closes itself — the one thing besides word class that a tile
     * wears in its WRITING rather than in its meaning. A lone question mark
     * among full stops answers the question before anyone has read it.
     */
    enum class SentenceShape { Question, Exclamation, Statement, Bare }

    /**
     * [text]'s shape, read off its last non-blank character.
     *
     * The closing mark is enough in every catalog language: Spanish writes an
     * opening `¿`/`¡` too, but never without the closing one, so a tile that
     * ends like the answer also begins like it. Every single word is [Bare],
     * which is why this rule reaches phrases only.
     */
    fun sentenceShape(text: String): SentenceShape =
        when (text.trimEnd().lastOrNull()) {
            '?' -> SentenceShape.Question
            '!' -> SentenceShape.Exclamation
            '.', '…' -> SentenceShape.Statement
            else -> SentenceShape.Bare
        }

    /**
     * The form [text] is OFFERED in, which is not always the form it is taught
     * in: a bound stem loses its dash, and a verb its citation prefix (sw `ku`,
     * en `to `). Both mark a word class in the writing itself, so a lone `-zuri`
     * among plain words — or a lone `kupika` among nouns — is pickable by anyone,
     * in any language. Applied to every option in a question, so it can never
     * single one out; the taught form is what the reveal shows.
     *
     * [citationPrefixes] are the option-side language's `optionalVerbPrefixes`
     * and reach verbs only, exactly as grading leniency does: a noun that merely
     * starts like a stem keeps its first syllable.
     */
    fun optionForm(text: String, kind: CardKind, citationPrefixes: List<String>): String {
        val free = text.removePrefix(BOUND_STEM)
        if (kind != CardKind.Verb) return free
        val prefix = citationPrefixes.firstOrNull { free.length > it.length && free.startsWith(it) }
        return prefix?.let { free.substring(it.length) } ?: free
    }

    /**
     * Up to [limit] distractors for [answer], best company first: unique
     * case-insensitively, distinct from the answer, and ranked by word class,
     * then [sentenceShape], then area, then [shapeDistance]. Same class first is
     * what keeps the question about meaning; closing the same way keeps a
     * statement from being ruled out beside a question without being read; same
     * area makes it a question worth asking, since telling four kitchen words
     * apart tests the kitchen. Empty when every candidate repeats the answer.
     *
     * Every key RANKS, none filters — a box with nothing well-matched left still
     * fills four tiles rather than offering three.
     */
    fun distractors(answer: Option, candidates: List<Option>, limit: Int = SHORTLIST): List<String> {
        val seen = mutableSetOf(answer.text.lowercase())
        val unique = candidates.filter { seen.add(it.text.lowercase()) }
        val shape = sentenceShape(answer.text)
        // Shapes are decided once per candidate rather than inside the
        // comparator: `offer` already ranks the whole pool once per entry.
        return unique
            .map { it to sentenceShape(it.text) }
            .sortedWith(
                compareBy(
                    { (option, _) -> option.kind != answer.kind },
                    { (_, candidateShape) -> candidateShape != shape },
                    { (option, _) -> option.area != answer.area },
                    { (option, _) -> shapeDistance(option.text, answer.text) },
                ),
            )
            .take(limit)
            .map { (option, _) -> option.text }
    }

    /**
     * Character-length gap plus a heavy penalty when the number of
     * space/hyphen-separated parts differs, keeping compounds and phrases
     * together rather than standing out among single words.
     */
    fun shapeDistance(a: String, b: String): Int =
        abs(a.length - b.length) + abs(partCount(a) - partCount(b)) * PART_PENALTY

    private fun partCount(s: String): Int =
        s.split(' ', '-').count { it.isNotEmpty() }
}
