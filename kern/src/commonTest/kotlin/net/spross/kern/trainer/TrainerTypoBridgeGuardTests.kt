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

    @Test
    fun germanCardinals0To999NeverBridge() {
        assertEquals(emptyList(), sweep("de", (0L..999L).map { GermanNumbers.cardinal(it) }))
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
