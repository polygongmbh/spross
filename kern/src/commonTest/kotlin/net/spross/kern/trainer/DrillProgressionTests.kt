package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The ladder and the direction axis. What matters here is that a rung is earned
 * exactly at its requirement — one level short must still read as locked, because
 * the caption the learner sees is derived from this and nothing else.
 */
class DrillProgressionTests {

    private fun progress(vararg pairs: Pair<DrillVariant, Int>) = mapOf(*pairs)

    @Test
    fun numbersAndReverseAreAlwaysAvailable() {
        assertTrue(DrillUnlocks.requirements(DrillVariant.Numbers).isEmpty())
        assertTrue(DrillUnlocks.requirements(DrillModifier.Reverse).isEmpty())
        assertTrue(DrillUnlocks.unlocked(DrillVariant.Numbers, emptyMap()))
        assertTrue(DrillUnlocks.unlocked(DrillModifier.Reverse, emptyMap()))
    }

    @Test
    fun everyRungOpensExactlyAtItsRequirement() {
        val variants = listOf(
            Triple(DrillVariant.Clock, DrillVariant.Numbers, 4),
            Triple(DrillVariant.Phrases, DrillVariant.Clock, 3),
            Triple(DrillVariant.Forms, DrillVariant.Numbers, 7),
        )
        for ((locked, on, level) in variants) {
            assertEquals(mapOf(on to level), DrillUnlocks.requirements(locked))
            assertFalse(DrillUnlocks.unlocked(locked, emptyMap()), "$locked with no progress")
            assertFalse(DrillUnlocks.unlocked(locked, progress(on to level - 1)), "$locked at ${level - 1}")
            assertTrue(DrillUnlocks.unlocked(locked, progress(on to level)), "$locked at $level")
        }
        assertEquals(mapOf(DrillVariant.Numbers to 10), DrillUnlocks.requirements(DrillModifier.Fast))
        assertFalse(DrillUnlocks.unlocked(DrillModifier.Fast, progress(DrillVariant.Numbers to 9)))
        assertTrue(DrillUnlocks.unlocked(DrillModifier.Fast, progress(DrillVariant.Numbers to 10)))
    }

    /** Mix takes two rungs at once, and neither alone opens it. */
    @Test
    fun mixNeedsBothTheNumbersCeilingAndTheFormsMidpoint() {
        assertEquals(
            mapOf(DrillVariant.Numbers to 10, DrillVariant.Forms to 5),
            DrillUnlocks.requirements(DrillModifier.Mix),
        )
        assertFalse(DrillUnlocks.unlocked(DrillModifier.Mix, progress(DrillVariant.Numbers to 10)))
        assertFalse(DrillUnlocks.unlocked(DrillModifier.Mix, progress(DrillVariant.Forms to 5)))
        assertFalse(
            DrillUnlocks.unlocked(DrillModifier.Mix, progress(DrillVariant.Numbers to 10, DrillVariant.Forms to 4)),
        )
        assertTrue(
            DrillUnlocks.unlocked(DrillModifier.Mix, progress(DrillVariant.Numbers to 10, DrillVariant.Forms to 5)),
        )
    }

    /** Clock and Phrases stay out of the Mix gate — a pair's phrase ceiling is catalog-dependent. */
    @Test
    fun mixIgnoresClockAndPhraseProgress() {
        val requirement = DrillUnlocks.requirements(DrillModifier.Mix)
        assertFalse(DrillVariant.Clock in requirement)
        assertFalse(DrillVariant.Phrases in requirement)
    }

    @Test
    fun fastModeHalvesTheRung() {
        assertEquals(2, Trainer.winsToAdvance(fast = false))
        assertEquals(1, Trainer.winsToAdvance(fast = true))
    }

    // Direction

    @Test
    fun reversingSwapsThePromptAndAsksForTheValue() {
        val rng = Random(20260807)
        for (language in Trainer.languages) {
            for (kind in TrainerKind.entries) {
                for (level in 1..Trainer.maxLevel(kind)) {
                    repeat(5) {
                        val forward = Trainer.sample(kind, language, level, rng)
                        val back = Trainer.reversed(forward)
                        val where = "$language $kind level=$level ${forward.prompt}"
                        assertEquals(forward.display, back.prompt, where)
                        assertEquals(back.prompt, back.promptDisplay, where)
                        assertTrue(forward.prompt in back.accepted, "$where: ${back.accepted}")
                        assertTrue(back.display in back.accepted, "$where: ${back.display}")
                        assertEquals(forward.kind, back.kind, where)
                        assertEquals(forward.language, back.language, where)
                    }
                }
            }
        }
    }

    @Test
    fun reversedNumbersTakeEitherSpellingAndRevealTheGroupedOne() {
        val forward = Trainer.number(12345, "de")
        val back = Trainer.reversed(forward)
        assertEquals(forward.display, back.prompt)
        assertEquals(listOf("12345", "12\u202F345"), back.accepted)
        assertEquals("12\u202F345", back.display)
        assertEquals(listOf("347"), Trainer.reversed(Trainer.number(347, "de")).accepted)
    }

    @Test
    fun reversedClockTakesBothDigitalForms() {
        val back = Trainer.reversed(Trainer.clock(8, 5, "de"))
        assertEquals(listOf("08:05", "8:05"), back.accepted)
        assertEquals("08:05", back.display)
        assertEquals(listOf("14:35"), Trainer.reversed(Trainer.clock(14, 35, "de")).accepted)
    }

    @Test
    fun reversedYearsAreNeverGrouped() {
        val back = Trainer.reversed(Trainer.year(1978, "de"))
        assertEquals(listOf("1978"), back.accepted)
        assertEquals("1978", back.display)
    }
}
