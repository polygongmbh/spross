package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The ladder and the direction axis. What matters here is that a Sprosse is earned
 * exactly at its requirement — one level short must still read as locked, because
 * the caption the learner sees is derived from this and nothing else.
 */
class DrillProgressionTests {

    private fun progress(vararg pairs: Pair<DrillVariant, Int>) = mapOf(*pairs)

    /** Counting is the one thing nothing has to be earned for — every other row is bought. */
    @Test
    fun numbersIsTheOnlyThingOpenFromTheStart() {
        assertTrue(DrillUnlocks.requirements(DrillVariant.Numbers).isEmpty())
        assertTrue(DrillUnlocks.unlocked(DrillVariant.Numbers, emptyMap()))
        for (modifier in DrillModifier.entries) {
            assertFalse(DrillUnlocks.unlocked(modifier, emptyMap()), "$modifier with no progress")
        }
    }

    /** Decoding waits for the clock to have been worked, not merely opened. */
    @Test
    fun reverseWaitsForTheClockToBeClimbed() {
        assertEquals(mapOf(DrillVariant.Clock to 3), DrillUnlocks.requirements(DrillModifier.Reverse))
        assertFalse(DrillUnlocks.unlocked(DrillModifier.Reverse, progress(DrillVariant.Clock to 2)))
        assertTrue(DrillUnlocks.unlocked(DrillModifier.Reverse, progress(DrillVariant.Clock to 3)))
        // The numbers climb rides along: the clock does not open before four digits.
        assertFalse(DrillUnlocks.unlocked(DrillVariant.Clock, progress(DrillVariant.Numbers to 3)))
    }

    @Test
    fun everySprosseOpensExactlyAtItsRequirement() {
        val variants = listOf(
            Triple(DrillVariant.Clock, DrillVariant.Numbers, 4),
            // The phrase gate rides the clock ceiling, so growing the ladder raises it.
            Triple(DrillVariant.Phrases, DrillVariant.Clock, Trainer.maxLevel(TrainerKind.Clock)),
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

    /**
     * Mix rides on the forms Sprosse alone. It needs no numbers Sprosse of its own: Forms
     * cannot open below seven digits, so the climb is already paid for by the time
     * this can be reached.
     */
    @Test
    fun mixRidesOnTheFormsSprosseAlone() {
        assertEquals(mapOf(DrillVariant.Forms to 5), DrillUnlocks.requirements(DrillModifier.Mix))
        assertFalse(DrillUnlocks.unlocked(DrillModifier.Mix, progress(DrillVariant.Forms to 4)))
        assertTrue(DrillUnlocks.unlocked(DrillModifier.Mix, progress(DrillVariant.Forms to 5)))
        assertFalse(DrillUnlocks.unlocked(DrillVariant.Forms, progress(DrillVariant.Numbers to 6)))
    }

    /** No modifier prices Phrases — a pair's phrase ceiling is catalog-dependent. */
    @Test
    fun noModifierPricesPhraseProgress() {
        for (modifier in DrillModifier.entries) {
            assertFalse(DrillVariant.Phrases in DrillUnlocks.requirements(modifier), "$modifier")
        }
    }

    @Test
    fun fastModeHalvesTheSprosse() {
        assertEquals(2, Trainer.winsToAdvance(fast = false))
        assertEquals(1, Trainer.winsToAdvance(fast = true))
    }

    // The ramp — one rule for every drill, whatever it asks.

    @Test
    fun twoCleanWinsClimbOneSprosseAndAMissStepsBack() {
        val first = DrillRamp.step(3, 0, correct = true, clean = true, winsRequired = 2)
        assertEquals(DrillRamp.SprosseStep(3, 1), first)
        val second = DrillRamp.step(3, 1, correct = true, clean = true, winsRequired = 2)
        assertEquals(DrillRamp.SprosseStep(4, 0), second)
        val missed = DrillRamp.step(4, 1, correct = false, clean = true, winsRequired = 2)
        assertEquals(DrillRamp.SprosseStep(3, 0), missed)
        // The floor holds however long the run goes wrong.
        assertEquals(
            DrillRamp.SprosseStep(1, 0),
            DrillRamp.step(1, 0, correct = false, clean = true, winsRequired = 2),
        )
    }

    @Test
    fun aShorterSprosseClimbsOnASingleWin() {
        assertEquals(
            DrillRamp.SprosseStep(4, 0),
            DrillRamp.step(3, 0, correct = true, clean = true, winsRequired = 1),
        )
        // The narrower stage does not change what a miss costs.
        assertEquals(
            DrillRamp.SprosseStep(2, 0),
            DrillRamp.step(3, 0, correct = false, clean = true, winsRequired = 1),
        )
    }

    @Test
    fun anAlmostAnswerMovesNeitherWay() {
        for (width in 1..2) {
            assertEquals(
                DrillRamp.SprosseStep(3, 1),
                DrillRamp.step(3, 1, correct = true, clean = false, winsRequired = width),
            )
        }
    }

    /**
     * The Sprosse has no ceiling: a ladder's named Sprossen cap the CONTENT, and each drill
     * clamps its own draw, but the number goes on so a climbed-out ladder still has
     * something to beat.
     */
    @Test
    fun theSprosseKeepsCountingPastTheNamedLadder() {
        assertEquals(8, DrillRamp.step(7, 1, correct = true, clean = true, winsRequired = 2).level)
        assertEquals(10, DrillRamp.step(9, 0, correct = true, clean = true, winsRequired = 1).level)
        // A miss costs one Sprosse up there like anywhere else, and the floor still holds.
        assertEquals(11, DrillRamp.step(12, 1, correct = false, clean = true, winsRequired = 2).level)
    }

    // The ladder — where the next question comes from, shared by all four drills.

    /**
     * The climb: the standing Sprosse first, the ones above it only where it is answered out,
     * and a Sprosse ABOVE the named ladder keeps its own number rather than the clamped one.
     */
    @Test
    fun theClimbFindsTheFirstSprosseWithAQuestionLeft() {
        val spent = setOf(1, 2)
        assertEquals(DrillLadder.Sprosse("q3", 3), DrillLadder.climb(1, 5) { if (it in spent) null else "q$it" })
        assertEquals(DrillLadder.Sprosse("q2", 2), DrillLadder.climb(2, 5) { "q$it" })
        // Past the top the sampler is asked for the top Sprosse and the answer keeps the tail's number.
        assertEquals(DrillLadder.Sprosse("q5", 9), DrillLadder.climb(9, 5) { "q$it" })
        // A ladder answered out draws nothing and leaves the run standing where it was.
        assertEquals(DrillLadder.Sprosse(null, 4), DrillLadder.climb(4, 5) { null })
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
