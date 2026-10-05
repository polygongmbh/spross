package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.Alphabet
import net.spross.kern.model.Card
import net.spross.kern.session.spokenOnly

/**
 * The letter drill's top Sprossen: words the learner already holds, played and written down
 * ([LetterFormat.Dictation]). Behind [LetterDrill]'s dictation verbs.
 */
internal object LetterDictation {

    /** Dictation at Sprosse 8 asks for short words; the count ignores spaces. */
    private const val SHORT_WORD_LETTERS = 6

    /** Below this many short candidates the Sprosse-8 filter is dropped — never draw from one. */
    private const val MIN_SHORT_CANDIDATES = 3

    /** Ceilings on the two things that make a word worth dictating twice (see [weight]). */
    private const val TRICKY_CAP = 3
    private const val DIFFICULTY_CAP = 3

    /** FSRS difficulty runs 1–10; below its middle a word is not what the Sprosse is for. */
    private const val DIFFICULTY_MIDPOINT = 5.0
    private const val DIFFICULTY_PER_STEP = 1.0

    /**
     * One dictation question. [candidates] arrive filtered to arrived, speakable box
     * cards; kern drops anything with a space of its own — a transcription task is
     * one word, whatever the caller believes.
     *
     * Sprosse 8 asks for short words. If fewer than [MIN_SHORT_CANDIDATES] survive that
     * filter the whole list is used instead: a drill that always dictates the same two
     * words is worse than one that occasionally dictates a long one.
     *
     * Inside whatever pool survives, the draw is WEIGHTED by [weight] — a Sprosse
     * spent on words already spelled right is a Sprosse spent on nothing. [alphabet] is only
     * consulted for the language's own hard graphemes; a language without one dictates
     * fine, it just weighs the spelling half at zero.
     *
     * [solved] is what this run has already transcribed right; null comes back once every
     * candidate is in it, and the run climbs past the Sprosse rather than dictating twice.
     */
    fun sample(
        candidates: List<LetterDrill.DictationCandidate>,
        alphabet: Alphabet?,
        sprosse: Int,
        avoidCardId: String?,
        solved: Set<String>,
        rng: Random,
    ): LetterDrillTask? {
        val words = candidates.filter {
            ' ' !in it.card.target.text &&
                DrillSolved.letterKey(LetterFormat.Dictation, it.card.id, it.card.target.text) !in solved
        }
        if (words.isEmpty()) return null
        val short = words.filter { it.card.target.text.count { ch -> ch != ' ' } <= SHORT_WORD_LETTERS }
        val pool = if (sprosse <= 8 && short.size >= MIN_SHORT_CANDIDATES) short else words
        val tricky = alphabet?.trickyGlyphs.orEmpty()
        val weights = pool.map { weight(it, tricky) }
        var card = weighted(pool, weights, rng).card
        if (card.id == avoidCardId) card = weighted(pool, weights, rng).card
        return LetterDrillTask(
            format = LetterFormat.Dictation,
            language = card.target.lang,
            answerRef = card.id,
            promptText = card.target.text,
            promptKind = LetterPromptKind.Word,
            promptSlug = card.id,
            promptGlyph = null,
            choices = null,
            gapText = null,
            // why: transcription accepts what was SPOKEN and nothing else — a synonym
            // would credit a word the learner never heard.
            accepted = listOf(card.target.text),
            display = card.target.text,
            gloss = card.source.text,
        )
    }

    /**
     * How much of the dictation draw a candidate is worth. One is the floor every word
     * keeps — nothing is ever excluded, only out-drawn — and two things add to it:
     *
     * the SPELLING (how many of the language's own hard graphemes the word carries, which
     * is what a transcription actually tests), and FSRS's DIFFICULTY above the midpoint,
     * which every Again raises — the words this learner has forgotten before. Each is capped,
     * so a single leech cannot take the Sprosse over, and both are zero on a short clean word
     * — which is exactly when the draw stays uniform.
     */
    fun weight(candidate: LetterDrill.DictationCandidate, trickyGlyphs: List<String>): Int {
        val word = candidate.card.target.text.lowercase()
        val spelling = minOf(TRICKY_CAP, trickyGlyphs.count { it in word })
        val forgotten = minOf(
            DIFFICULTY_CAP,
            ((candidate.difficulty - DIFFICULTY_MIDPOINT) / DIFFICULTY_PER_STEP).toInt().coerceAtLeast(0),
        )
        return 1 + spelling + forgotten
    }

    /** Cumulative draw over [weights]; identical to a uniform pick where they all match. */
    private fun <T> weighted(pool: List<T>, weights: List<Int>, rng: Random): T {
        val total = weights.sum()
        var roll = rng.nextInt(total)
        for ((index, weight) in weights.withIndex()) {
            roll -= weight
            if (roll < 0) return pool[index]
        }
        return pool.last()
    }

    /**
     * The card a dictation answer is graded against — [spokenOnly] over what the task
     * actually played, so no word the learner never heard is credited.
     */
    fun gradingCard(card: Card, task: LetterDrillTask): Card =
        spokenOnly(card, task.accepted.firstOrNull() ?: card.target.text)
}
