package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The masking ladder: what each Sprosse leaves standing, what it mixes, and the three things
 * every Sprosse owes — the same letters, an arrangement that is not the spelling, and one that
 * is not a single adjacent swap off it either.
 */
class WordScrambleMaskingTest {

    private val word = "Fenster"

    private fun seeds() = (1..40).map { Random(it) }

    /**
     * The opening letter stands, and it keeps the capital the word was authored with. Nothing
     * else does: an ending held in place too would leave a four-letter word two letters to
     * move, which is one possible mix and therefore the same question every time.
     */
    @Test
    fun theFirstTwoSprossenAnchorTheOpeningLetterAlone() {
        for (rng in seeds()) {
            for (level in 1..2) {
                val mixed = WordScrambleMasking.scramble(word, level, rng)
                assertEquals(1, mixed.fixedLeading)
                assertEquals("F", mixed.display.take(1))
                assertEquals(letters(word), letters(mixed.display))
            }
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
     * The mix is not the spelling with one neighboring pair traded either — that is the same
     * cue wearing a typo, and it reads as the word itself.
     */
    @Test
    fun theMixIsNeverOneAdjacentSwapOffTheWord() {
        for (rng in seeds()) {
            for (level in 1..WordScrambleMasking.MAX_LEVEL) {
                val mixed = WordScrambleMasking.scramble(word, level, rng).display.lowercase()
                val moved = word.lowercase().indices.filter { mixed[it] != word.lowercase()[it] }
                assertTrue(moved.size != 2 || moved[1] != moved[0] + 1, "$level: $mixed")
            }
        }
    }

    /**
     * Where a swap is all the letters admit, it stands: the guard yields rather than hand back
     * the word itself. "Tal" anchored at the front has `al` to move, and `la` is a swap.
     */
    @Test
    fun aWordThatAdmitsNothingBetterGetsTheSwap() {
        for (rng in seeds()) {
            assertEquals("Tla", WordScrambleMasking.scramble("Tal", 1, rng).display)
        }
    }

    /**
     * A word with no other arrangement left to it comes back as authored rather than looping:
     * "Ei" anchored at the front has one letter to move.
     */
    @Test
    fun aWordWithNothingLeftToMoveComesBackAsAuthored() {
        assertEquals("Ei", WordScrambleMasking.scramble("Ei", 1, Random(3)).display)
    }

    private fun letters(text: String): List<Char> = text.lowercase().toList().sorted()
}
