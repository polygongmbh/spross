package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cardinal half of the "a drill must never accept one number for another" guard:
 * [TypoBridgeSweep] over each language's generated cardinals, which is where its audited
 * allowlist was found. The forms half is [TrainerFormsTypoBridgeGuardTests].
 *
 * Any pair NOT on the allowlist fails the sweep; a vanished known pair fails too.
 */
class TrainerTypoBridgeGuardTests {

    /** Both German spellings per value — the bare-stem twin is graded too, so it is swept too. */
    @Test
    fun germanCardinals0To999NeverBridge() {
        val prompts = (0L..999L).map { TypoBridgeSweep.Prompt(GermanNumbers.variants(it)) }
        assertEquals(emptyList(), TypoBridgeSweep.run("de", prompts))
    }

    @Test
    fun ukrainianCardinals0To99BridgeOnlyTheKnownNineTenPair() {
        val known = sweep("uk", (0L..99L).map { UkrainianNumbers.cardinal(it) })
        assertEquals(listOf("\"дев'ять\" ↔ \"десять\""), known)
    }

    @Test
    fun englishCardinals0To999BridgeOnlyTheKnownEightEightyPairs() {
        val known = sweep("en", (0L..999L).map { EnglishNumbers.cardinal(it) })
        assertEquals(10, known.size, "expected the ten eight ↔ eighty pairs, got $known")
        assertTrue(known.all { "eight\"" in it && "eighty\"" in it }, "unexpected pair in $known")
    }

    @Test
    fun spanishCardinals0To999BridgeOnlyTheKnownSixtySeventyPairs() {
        val known = sweep("es", (0L..999L).map { SpanishNumbers.cardinal(it) })
        assertEquals(100, known.size, "expected the hundred sesenta ↔ setenta pairs, got $known")
        assertTrue(known.all { "sesenta" in it && "setenta" in it }, "unexpected pair in $known")
    }

    /**
     * The six/dix pair, alone and inside the two compounds that carry it — and only those:
     * the rectified all-hyphen spelling welds a whole numeral, so it reproduces the same
     * pair as one word per hundred it sits under and teaches the sweep nothing the
     * traditional spelling it is derived from has not already shown.
     */
    @Test
    fun frenchCardinals0To999BridgeOnlyTheKnownSixTenPairs() {
        val known = sweep("fr", (0L..999L).map { FrenchNumbers.cardinal(it) })
        assertEquals(30, known.size, "expected the thirty six ↔ dix pairs, got $known")
        assertTrue(known.all { "six\"" in it && "dix\"" in it }, "unexpected pair in $known")
    }

    /**
     * Both Italian spellings per value, the hiatus twin of the cento seam included — it is
     * graded, so it is swept. The one pair that bridges is the elision itself: `ventotto`
     * and `centotto` are `otto` welded onto two words that differ in one letter.
     */
    @Test
    fun italianCardinals0To999BridgeOnlyTheKnownTwentyEightHundredEightPair() {
        val prompts = (0L..999L).map { TypoBridgeSweep.Prompt(ItalianNumbers.variants(it)) }
        assertEquals(
            listOf("\"ventotto\" ↔ \"centotto\""),
            TypoBridgeSweep.run("it", prompts),
        )
    }

    @Test
    fun swahiliCardinals0To99BridgeOnlyTheKnownFourEightPairs() {
        val known = sweep("sw", (0L..99L).map { SwahiliNumbers.cardinal(it) })
        assertEquals(10, known.size, "expected the ten nne ↔ nane pairs, got $known")
        assertTrue(known.all { "nne" in it && "nane" in it }, "unexpected pair in $known")
    }

    /** One canonical spelling per number: the cardinal drill's own answer space. */
    private fun sweep(language: String, words: List<String>): List<String> =
        TypoBridgeSweep.run(language, TypoBridgeSweep.single(words))
}
