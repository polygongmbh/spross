package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The masking ladder: what each Sprosse leaves standing, what it mixes, and the two things
 * every Sprosse owes — the same letters, and an arrangement that is not the spelling.
 */
class WordScrambleMaskingTest {

    private val word = "Fenster"

    private fun seeds() = (1..40).map { Random(it) }

    /** Both ends anchored, and they keep the capital the word was authored with. */
    @Test
    fun theFirstSprosseAnchorsBothEnds() {
        for (rng in seeds()) {
            val mixed = WordScrambleMasking.scramble(word, 1, rng)
            assertEquals(1, mixed.fixedLeading)
            assertEquals(1, mixed.fixedTrailing)
            assertEquals("F", mixed.display.take(1))
            assertEquals("r", mixed.display.takeLast(1))
            assertEquals(letters(word), letters(mixed.display))
        }
    }

    /** The second gives the ending back to the learner and keeps only the opening letter. */
    @Test
    fun theSecondSprosseAnchorsTheOpeningLetterAlone() {
        for (rng in seeds()) {
            val mixed = WordScrambleMasking.scramble(word, 2, rng)
            assertEquals(1, mixed.fixedLeading)
            assertEquals(0, mixed.fixedTrailing)
            assertEquals("F", mixed.display.take(1))
            assertEquals(letters(word), letters(mixed.display))
        }
    }

    /**
     * The third anchors nothing — including the capital, which would otherwise point at the
     * very letter the Sprosse withheld.
     */
    @Test
    fun theThirdSprosseAnchorsNothingAndKeepsNoCapital() {
        for (rng in seeds()) {
            val mixed = WordScrambleMasking.scramble(word, 3, rng)
            assertTrue(mixed.fullyScrambled)
            assertEquals(mixed.display.lowercase(), mixed.display)
            assertEquals(letters(word), letters(mixed.display))
        }
    }

    /** Past the named Sprossen the content stands still: nothing harder than mixing everything. */
    @Test
    fun aSprosseAboveTheLadderStillAnchorsNothing() {
        val mixed = WordScrambleMasking.scramble(word, WordScrambleMasking.MAX_LEVEL + 4, Random(7))
        assertTrue(mixed.fullyScrambled)
    }

    /** A mix that reads as the spelling is no question at all, so it is rolled again. */
    @Test
    fun theMixNeverReadsAsTheWordItAsksFor() {
        for (rng in seeds()) {
            for (level in 1..WordScrambleMasking.MAX_LEVEL) {
                val mixed = WordScrambleMasking.scramble(word, level, rng)
                assertNotEquals(word.lowercase(), mixed.display.lowercase())
            }
        }
    }

    /**
     * A word with no other arrangement left to it comes back as authored rather than looping:
     * "Ebbe" anchored at both ends has only `bb` to move.
     */
    @Test
    fun aWordWithNothingLeftToMoveComesBackAsAuthored() {
        assertEquals("Ebbe", WordScrambleMasking.scramble("Ebbe", 1, Random(3)).display)
    }

    private fun letters(text: String): List<Char> = text.lowercase().toList().sorted()
}
