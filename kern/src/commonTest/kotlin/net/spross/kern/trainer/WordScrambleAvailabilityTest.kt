package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.model.CardKind

/**
 * Which of the learner's own words the scramble may ask. Every row here is a shape the gate has
 * an opinion about: the three word kinds, a phrase, a word short of the display bar, a word of
 * two tokens, and a word too short to anchor.
 */
class WordScrambleAvailabilityTest {

    private val cards = listOf(
        ScrambleFixture.word("window", "Fenster", seed = 1),
        ScrambleFixture.word("cook", "kochen", CardKind.Verb, seed = 2),
        ScrambleFixture.word("fast", "schnell", CardKind.Adjective, seed = 3),
        ScrambleFixture.word("rainbow", "Regenbogen", seed = 4),
        ScrambleFixture.word("outside", "draußen", CardKind.Adjective, seed = 5),
        ScrambleFixture.word("wash", "sich waschen", CardKind.Verb, seed = 6),
        ScrambleFixture.word("clock", "Uhr", seed = 7),
        ScrambleFixture.word("sun", "Sonne", seed = 8),
        ScrambleFixture.phrase("greeting", "guten Morgen hier", listOf("window"), seed = 9),
    )

    private fun ids(
        standing: Map<String, Double> = emptyMap(),
        suspended: Set<String> = emptySet(),
    ): List<String> =
        WordScrambleAvailability.report(ScrambleFixture.box(cards, standing = standing, suspended = suspended))
            .words.map { it.id }

    /** All three word kinds qualify, and the pool comes back in seed order. */
    @Test
    fun everyConsolidatedSingleWordOfEnoughLettersIsAsked() {
        assertEquals(listOf("window", "cook", "fast", "rainbow", "outside", "sun"), ids())
    }

    /** The DISPLAY bar, not the growing one: a word still on its way in is no cue to itself. */
    @Test
    fun aWordShortOfTheDisplayBarIsNotAsked() {
        assertFalse("window" in ids(standing = mapOf("window" to ScrambleFixture.GROWING)))
    }

    /** A separable or reflexive verb is two spellings and a word order — not this drill's. */
    @Test
    fun aWordTheTargetWritesAsTwoTokensIsNotAsked() {
        assertFalse("wash" in ids())
    }

    /** Below the minimum the anchors give the word away, so it is never asked. */
    @Test
    fun aWordTooShortToAnchorIsNotAsked() {
        assertFalse("clock" in ids())
        assertEquals(WordScrambleAvailability.MIN_LETTERS - 1, "Uhr".length)
    }

    /** A phrase belongs to the other scramble, whatever its length. */
    @Test
    fun aPhraseIsNeverAWordScramble() {
        assertFalse("greeting" in ids())
    }

    /** Suspending a word says stop asking it, and a drill is an asking. */
    @Test
    fun aSuspendedWordIsNotAsked() {
        assertFalse("sun" in ids(suspended = setOf("sun")))
    }

    /** The chip predicate is the pool floor, and an empty box offers nothing. */
    @Test
    fun theChipWaitsForAPoolWorthARun() {
        assertTrue(WordScrambleAvailability.drillExists(ScrambleFixture.box(cards)))
        val thin = cards.take(WordScrambleAvailability.POOL_FLOOR - 1)
        assertFalse(WordScrambleAvailability.drillExists(ScrambleFixture.box(thin)))
        assertFalse(WordScrambleAvailability.drillExists(ScrambleFixture.box(emptyList())))
    }
}
