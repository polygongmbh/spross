package net.spross.kern.trainer

import kotlin.random.Random

/**
 * A word with its letters mixed, and how much of it still stands where the spelling puts it.
 *
 * [fixedLeading] counts letters at the front of [display] that are the authored word's own —
 * named as the rule rather than as whatever a surface does to mark them.
 */
data class ScrambledWord(
    val display: String,
    val fixedLeading: Int,
) {
    /**
     * What a screen reader hears for the mixed word: its letters one by one.
     * A mixed word is not a word, and a voice reading it as one says nothing a learner can spell from.
     */
    val spelledOut: String get() = display.letters().joinToString(", ")
}

/**
 * How a word is mixed for the learner to write back — a pure function of the spelling and the
 * run's own [Random].
 *
 * The opening letter always stands: it is the cue a word is found by, and without it the
 * question turns from recalling a word into solving a puzzle. The ending is mixed with the
 * rest — held too, it would leave a four-letter word two letters to move, one possible mix
 * and therefore the same question every time.
 *
 * Only the opening letter keeps the authored capitalization; the mixed letters are lowered, so
 * no capital marks where one of them belongs.
 */
object WordScrambleMasking {

    /**
     * How many arrangements a word is offered before the guards are relaxed. A word whose
     * letters admit few of them ("Beeren") would otherwise re-roll on and on.
     */
    private const val MIX_ATTEMPTS = 12

    /**
     * [text] with everything but its opening letter mixed up.
     *
     * A word with no more than one letter to move comes back as it was authored: there is no
     * other arrangement of it, and re-rolling for one would loop.
     */
    fun scramble(text: String, rng: Random): ScrambledWord {
        // why: a letter is its base plus the marks riding on it (uk `і́`), so a stress mark
        // moves with its vowel instead of landing on whatever letter the mix puts before it.
        val letters = text.trim().letters()
        val lead = minOf(1, letters.size)
        val head = letters.take(lead).joinToString("")
        val interior = letters.drop(lead).map { it.lowercase() }
        return ScrambledWord(head + mixed(interior, rng).joinToString(""), lead)
    }

    /**
     * The letters in an order that is not the one they were written in, and preferably not one
     * neighboring pair off it either: a mix the learner reads as the word itself asks nothing,
     * and a single adjacent swap is the same cue wearing a typo.
     *
     * Both guards yield in turn rather than loop, because a short word may admit nothing better:
     * [MIX_ATTEMPTS] arrangements are tried for one that clears both, then the best swap that
     * came up stands, and only letters with no other arrangement at all come back as written.
     */
    private fun mixed(letters: List<String>, rng: Random): List<String> {
        if (letters.size < 2) return letters
        var swap: List<String>? = null
        repeat(MIX_ATTEMPTS) {
            val mix = letters.shuffled(rng)
            if (mix == letters) return@repeat
            if (!isAdjacentSwap(mix, letters)) return mix
            if (swap == null) swap = mix
        }
        return swap ?: letters
    }

    /** Whether [mix] is [letters] with one neighboring pair traded and nothing else moved. */
    private fun isAdjacentSwap(mix: List<String>, letters: List<String>): Boolean {
        val moved = letters.indices.filter { mix[it] != letters[it] }
        if (moved.size != 2) return false
        val (first, second) = moved
        return second == first + 1
    }
}
