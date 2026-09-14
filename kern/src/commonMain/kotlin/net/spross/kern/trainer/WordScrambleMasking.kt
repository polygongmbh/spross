package net.spross.kern.trainer

import kotlin.random.Random

/**
 * A word with its letters mixed, and how much of it still stands where the spelling puts it.
 *
 * [fixedLeading] and [fixedTrailing] count letters at each end of [display] that are the
 * authored word's own — the help the Sprosse grants, named as the rule rather than as whatever
 * a surface does to mark them.
 */
data class ScrambledWord(
    val display: String,
    val fixedLeading: Int,
    val fixedTrailing: Int,
) {
    /** Nothing stands: the whole word has to be read out of its letters. */
    val fullyScrambled: Boolean get() = fixedLeading == 0 && fixedTrailing == 0
}

/**
 * How a word is mixed for the learner to write back — a pure function of the spelling, the
 * Sprosse and the run's own [Random].
 *
 * The ladder takes help AWAY rather than making the word longer: anchors first and last, then
 * first alone, then nothing. That is what a Sprosse is here — it changes what the question IS.
 *
 * Only the letters left standing keep the authored capitalization. A mixed letter is lowered,
 * because a capital riding somewhere in the middle of a German noun would name the word's first
 * letter at exactly the Sprosse that withheld it.
 */
object WordScrambleMasking {

    /** Anchored both ends, anchored at the front, anchored nowhere. */
    const val MAX_LEVEL: Int = 3

    /** How many mixes a word gets before one that reads as the spelling is allowed to stand. */
    private const val MIX_ATTEMPTS = 8

    /** How many letters stand at the front of the word at [level]. */
    fun fixedLeading(level: Int): Int = if (level.coerceAtLeast(1) <= 2) 1 else 0

    /** How many stand at its end. */
    fun fixedTrailing(level: Int): Int = if (level.coerceAtLeast(1) <= 1) 1 else 0

    /**
     * [text] with everything the Sprosse does not anchor mixed up.
     *
     * A word with no more than one letter to move comes back as it was authored: there is no
     * other arrangement of it, and re-rolling for one would loop.
     */
    fun scramble(text: String, level: Int, rng: Random): ScrambledWord {
        val word = text.trim()
        val lead = minOf(fixedLeading(level), word.length)
        val trail = minOf(fixedTrailing(level), word.length - lead)
        val head = word.take(lead)
        val tail = if (trail > 0) word.takeLast(trail) else ""
        val interior = word.substring(lead, word.length - trail).lowercase()
        return ScrambledWord(head + mixed(interior, rng) + tail, lead, trail)
    }

    /**
     * The letters, in some order that is not the one they were written in. Bounded: a word whose
     * letters admit few arrangements ("Beeren") would otherwise re-roll on and on, so after
     * [MIX_ATTEMPTS] whatever came up stands.
     */
    private fun mixed(letters: String, rng: Random): String {
        if (letters.length < 2) return letters
        var mix = letters.toList().shuffled(rng).joinToString("")
        var attempts = 0
        while (mix == letters && attempts < MIX_ATTEMPTS) {
            mix = letters.toList().shuffled(rng).joinToString("")
            attempts++
        }
        return mix
    }
}
