package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The ladder and the direction axis. What matters here is that a Sprosse is earned
 * exactly at its requirement — one Sprosse short must still read as locked, because
 * the caption the learner sees is derived from this and nothing else.
 */
class DrillProgressionTests {

    private fun progress(vararg pairs: Pair<NumbersExercise, Int>) = mapOf(*pairs)

    /** Counting is the one thing nothing has to be earned for — every other row is bought. */
    @Test
    fun numbersIsTheOnlyThingOpenFromTheStart() {
        assertTrue(DrillUnlocks.requirements(NumbersExercise.Counting).isEmpty())
        assertTrue(DrillUnlocks.unlocked(NumbersExercise.Counting, emptyMap()))
        for (modifier in DrillModifier.entries) {
            assertFalse(DrillUnlocks.unlocked(modifier, emptyMap()), "$modifier with no progress")
        }
    }

    /**
     * Every locked row names one requirement and opens exactly on it, read off the table
     * rather than restated. Decoding waits for the clock to have been climbed, and mix rides
     * on the forms Sprosse alone.
     */
    @Test
    fun everySprosseOpensExactlyAtItsRequirement() {
        val locked: List<Any> = listOf(NumbersExercise.Clock, NumbersExercise.Phrases, NumbersExercise.Forms) +
            DrillModifier.entries
        for (row in locked) {
            val requirements = when (row) {
                is NumbersExercise -> DrillUnlocks.requirements(row)
                else -> DrillUnlocks.requirements(row as DrillModifier)
            }
            val (on, sprosse) = requirements.entries.single().toPair()
            fun unlocked(progress: Map<NumbersExercise, Int>) = when (row) {
                is NumbersExercise -> DrillUnlocks.unlocked(row, progress)
                else -> DrillUnlocks.unlocked(row as DrillModifier, progress)
            }
            assertFalse(unlocked(progress(on to sprosse - 1)), "$row at ${sprosse - 1}")
            assertTrue(unlocked(progress(on to sprosse)), "$row at $sprosse")
        }
        assertEquals(NumbersExercise.Clock, DrillUnlocks.requirements(DrillModifier.Reverse).keys.single())
        assertEquals(NumbersExercise.Forms, DrillUnlocks.requirements(DrillModifier.Mix).keys.single())
        // The phrase gate rides the clock ceiling, so growing the ladder raises it.
        assertEquals(
            mapOf(NumbersExercise.Clock to Numbers.maxSprosse(NumbersReading.Clock)),
            DrillUnlocks.requirements(NumbersExercise.Phrases),
        )
    }

    /** No modifier prices Phrases — a pair's phrase ceiling is catalog-dependent. */
    @Test
    fun noModifierPricesPhraseProgress() {
        for (modifier in DrillModifier.entries) {
            assertFalse(NumbersExercise.Phrases in DrillUnlocks.requirements(modifier), "$modifier")
        }
    }

    // The ramp — one rule for every drill, whatever it asks.

    /**
     * WHEN a draw leads with what the Sprosse added, which is the half of the rule every
     * nesting ladder shares; WHAT each of them added is its own, and its own test.
     */
    @Test
    fun arrivingAlwaysLeadsWithWhatTheSprosseAddedAndStandingHalfTheTime() {
        for (seed in 1..40) {
            assertTrue(
                DrillLadder.leadsWithAdded(arriving = true, rng = Random(seed)),
                "arriving is never left to a coin",
            )
        }
        val led = (1..200).count { DrillLadder.leadsWithAdded(arriving = false, rng = Random(it)) }
        // Half, give or take the coin — the rest of the Sprosse keeps its turn either way.
        assertTrue(led in 70..130, "standing on a Sprosse led with what it added $led times in 200")
    }

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
        assertEquals(8, DrillRamp.step(7, 1, correct = true, clean = true, winsRequired = 2).sprosse)
        assertEquals(10, DrillRamp.step(9, 0, correct = true, clean = true, winsRequired = 1).sprosse)
        // A miss costs one Sprosse up there like anywhere else, and the floor still holds.
        assertEquals(11, DrillRamp.step(12, 1, correct = false, clean = true, winsRequired = 2).sprosse)
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

    /** The question just asked is drawn again ONCE, never until it misses: a repeat needs two unlucky draws. */
    @Test
    fun theQuestionJustAskedIsResampledOnce() {
        val pool = listOf("asked", "other")
        val repeats = (1..400).count { seed ->
            DrillLadder.pickAvoiding(pool, Random(seed.toLong())) { it == "asked" } == "asked"
        }
        assertTrue(repeats > 0, "a resample loop: the question just asked can never come back")
        assertTrue(repeats < 200, "avoiding the question just asked bought nothing")
    }

    // Direction

    @Test
    fun reversingSwapsThePromptAndAsksForTheValue() {
        val rng = Random(20260807)
        for (language in Numbers.languages) {
            for (kind in NumbersReading.entries) {
                for (sprosse in 1..Numbers.maxSprosse(kind)) {
                    repeat(5) {
                        val forward = Numbers.sample(kind, language, sprosse, rng)
                        val back = Numbers.reversed(forward)
                        val where = "$language $kind sprosse=$sprosse ${forward.prompt}"
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
        val forward = Numbers.number(12345, "de")
        val back = Numbers.reversed(forward)
        assertEquals(forward.display, back.prompt)
        assertEquals(listOf("12345", "12\u202F345"), back.accepted)
        assertEquals("12\u202F345", back.display)
        assertEquals(listOf("347"), Numbers.reversed(Numbers.number(347, "de")).accepted)
    }

    @Test
    fun reversedClockTakesBothDigitalForms() {
        val back = Numbers.reversed(Numbers.clock(8, 5, "de"))
        assertEquals(listOf("08:05", "08.05", "8:05", "8.05"), back.accepted)
        assertEquals("08:05", back.display)
        assertEquals(listOf("14:35", "14.35"), Numbers.reversed(Numbers.clock(14, 35, "de")).accepted)
    }

    @Test
    fun reversedYearsAreNeverGrouped() {
        val back = Numbers.reversed(Numbers.year(1978, "de"))
        assertEquals(listOf("1978"), back.accepted)
        assertEquals("1978", back.display)
    }
}
