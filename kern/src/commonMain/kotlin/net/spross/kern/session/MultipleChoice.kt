package net.spross.kern.session

import kotlin.math.abs

/**
 * Distractor selection for multiple-choice presentation — OS-independent, so
 * the watch, the phone and Android all offer the same tiles.
 *
 * Callers pass the option-side text of the answer and of every candidate: a
 * question asks for ONE side (recognition asks for the source meaning,
 * production for the target word), and reading a candidate on its own side
 * instead of the prompt's would mix the two languages on screen.
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

    /**
     * Up to [limit] distractors for [answer], closest shape first: unique
     * case-insensitively, distinct from the answer, and ranked by [shapeDistance]
     * so a lone long or multi-part option can't give the answer away. Empty when
     * every candidate repeats the answer.
     */
    fun distractors(answer: String, candidates: List<String>, limit: Int = SHORTLIST): List<String> {
        val seen = mutableSetOf(answer.lowercase())
        val unique = candidates.filter { seen.add(it.lowercase()) }
        return unique.sortedBy { shapeDistance(it, answer) }.take(limit)
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
