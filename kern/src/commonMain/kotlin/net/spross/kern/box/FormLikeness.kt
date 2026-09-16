package net.spross.kern.box

import net.spross.kern.session.AnswerNormalizer

/**
 * Whether two written forms are the same word said twice.
 *
 * The question a MATCHER asks, and deliberately not the one grading asks: grading decides
 * whether the learner typed the word in front of them and forgives a slip per six letters
 * without limit ([AnswerNormalizer] and `docs/grading.md`), where this decides whether two
 * forms name the same thing at all and stops at two. They share the distance and nothing
 * else — a budget that grew with length would fold whole families of long words together.
 *
 * Both callers fold their forms first ([net.spross.kern.model.caseFolded]).
 */
internal object FormLikeness {

    /**
     * One form standing inside the other with the shared letters outweighing the extra ones,
     * or a spelling a slip or two off.
     *
     * Containment is what agglutinating languages need: sw `ninapenda` is `penda` with a
     * subject and a tense on it, while `hapa` inside `tunamaliza hapa` is a different word
     * standing next to it, which is what the half-length floor rules out.
     */
    fun leans(one: String, other: String): Boolean {
        val longest = maxOf(one.length, other.length)
        val shortest = minOf(one.length, other.length)
        if (one.contains(other) || other.contains(one)) return shortest * 2 >= longest
        val slips = if (shortest >= TWO_SLIP_LENGTH) 2 else 1
        if (longest - shortest > slips) return false
        return AnswerNormalizer.damerauLevenshtein(one, other) <= slips
    }

    /** Under this many letters a shared spelling is a coincidence rather than a stem. */
    const val MIN_STEM: Int = 4

    /** From this length on, a word survives two slips and is still the same word. */
    private const val TWO_SLIP_LENGTH: Int = 8
}
