package net.spross.kern.trainer

import kotlin.random.Random

/**
 * A word with its letters mixed, and how much of it still stands where the spelling puts it.
 *
 * [fixedLeading] counts letters at the front of [display] that are the authored word's own —
 * the help the Sprosse grants, named as the rule rather than as whatever a surface does to
 * mark them.
 */
data class ScrambledWord(
    val display: String,
    val fixedLeading: Int,
) {
    /** Nothing stands: the whole word has to be read out of its letters. */
    val fullyScrambled: Boolean get() = fixedLeading == 0
}

/**
 * How a word is mixed for the learner to write back — a pure function of the spelling, the
 * Sprosse and the run's own [Random].
 *
 * The ladder takes help AWAY rather than making the word longer: the opening letter stands,
 * then nothing does. That is what a Sprosse is here — it changes what the question IS.
 *
 * Only the opening letter keeps the authored capitalization. A mixed letter is lowered, because
 * a capital riding somewhere in the middle of a German noun would name the word's first letter
 * at exactly the Sprosse that withheld it.
 */
object WordScrambleMasking {

    /** Anchored at the front, then anchored nowhere. */
    const val MAX_LEVEL: Int = 3

    /**
     * How many arrangements a word is offered before the guards are relaxed. A word whose
     * letters admit few of them ("Beeren") would otherwise re-roll on and on.
     */
    private const val MIX_ATTEMPTS = 12

    /** How many letters stand at the front of the word at [level]. */
    fun fixedLeading(level: Int): Int = if (level.coerceAtLeast(1) <= 2) 1 else 0

    /**
     * [text] with everything the Sprosse does not anchor mixed up.
     *
     * A word with no more than one letter to move comes back as it was authored: there is no
     * other arrangement of it, and re-rolling for one would loop.
     */
    fun scramble(text: String, level: Int, rng: Random): ScrambledWord {
        val word = text.trim()
        val lead = minOf(fixedLeading(level), word.length)
        val head = word.take(lead)
        val interior = word.substring(lead).lowercase()
        return ScrambledWord(head + mixed(interior, rng), lead)
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
    private fun mixed(letters: String, rng: Random): String {
        if (letters.length < 2) return letters
        var swap: String? = null
        repeat(MIX_ATTEMPTS) {
            val mix = letters.toList().shuffled(rng).joinToString("")
            if (mix == letters) return@repeat
            if (!isAdjacentSwap(mix, letters)) return mix
            if (swap == null) swap = mix
        }
        return swap ?: letters
    }

    /** Whether [mix] is [letters] with one neighboring pair traded and nothing else moved. */
    private fun isAdjacentSwap(mix: String, letters: String): Boolean {
        val moved = letters.indices.filter { mix[it] != letters[it] }
        if (moved.size != 2) return false
        val (first, second) = moved
        return second == first + 1
    }
}
