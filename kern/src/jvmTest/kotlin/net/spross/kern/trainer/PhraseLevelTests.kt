package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level-aware phrase-slot sampling: sentence drills ramp with exactly the same
 * level semantics as the plain drills — a frame never constrains the value.
 */
class PhraseLevelTests {

    private val clockTime = Regex("""(\d{1,2}):(\d{2})""")

    /** Slot minute from the source-sentence digital time ("… um 14:35 Uhr …"). */
    private fun minuteOf(sentence: String): Int =
        clockTime.find(sentence)!!.groupValues[2].toInt()

    private fun templates(kind: TrainerKind): List<PhraseTemplate> =
        RealFrames.all.filter { it.slotKind == kind }

    // Gentle start: level 1 per kind

    @Test
    fun levelOneClockOnlyYieldsFullHours() {
        val rng = Random(1)
        for (template in templates(TrainerKind.Clock)) {
            repeat(40) {
                val task = PhraseSlots.sample(template, level = 1, rng)
                assertEquals(0, minuteOf(task.prompt), "${template.id}: ${task.prompt}")
            }
        }
    }

    @Test
    fun levelOneNumberYieldsSingleDigitValues() {
        val rng = Random(2)
        for (template in templates(TrainerKind.Numbers)) {
            repeat(40) {
                val task = PhraseSlots.sample(template, level = 1, rng)
                val digits = task.prompt.filter { it.isDigit() }
                assertEquals(1, digits.length, "${template.id}: ${task.prompt}")
            }
        }
    }

    @Test
    fun levelOneYearStaysInRecentDecades() {
        val rng = Random(3)
        for (template in templates(TrainerKind.Years)) {
            repeat(40) {
                val task = PhraseSlots.sample(template, level = 1, rng)
                val year = task.prompt.filter { it.isDigit() }.toInt()
                assertTrue(year in 1990..2029, "${template.id}: $year")
            }
        }
    }

    // No frame constrains the minute set (the Swahili ≤ 30 rule is deleted)

    @Test
    fun quarterLevelYieldsAllFourQuarters() {
        val rng = Random(4)
        for (template in templates(TrainerKind.Clock)) {
            val seen = mutableSetOf<Int>()
            repeat(120) {
                seen += minuteOf(PhraseSlots.sample(template, level = 2, rng).prompt)
            }
            assertEquals(setOf(0, 15, 30, 45), seen, template.id)
        }
    }

    @Test
    fun maxLevelReachesMinutesPastHalfPast() {
        val rng = Random(5)
        for (template in templates(TrainerKind.Clock)) {
            var sawPastHalf = false
            repeat(120) {
                val minute = minuteOf(PhraseSlots.sample(template, level = 4, rng).prompt)
                assertTrue(minute in 0..59, "${template.id}: $minute")
                if (minute > 30) sawPastHalf = true
            }
            assertTrue(sawPastHalf, "${template.id}: expected countdown-form minutes at level 4")
        }
    }

    // Determinism: seeded RNG reproduces, and leveled sampling == instantiate

    @Test
    fun leveledSamplingIsDeterministicAndMatchesInstantiate() {
        for (template in RealFrames.all) {
            for (level in 1..Trainer.maxLevel(template.slotKind)) {
                val a = Random(0xBEEF + level)
                val b = Random(0xBEEF + level)
                repeat(30) {
                    val sampled = PhraseSlots.sample(template, level, a)
                    // Cross-check against the shared Trainer draw machinery.
                    val expected = if (template.slotKind == TrainerKind.Clock) {
                        val hour = b.nextInt(24)
                        PhraseSlots.instantiate(template, hour = hour, minute = Trainer.clockMinute(level, b))
                    } else {
                        val slot = Trainer.sample(template.slotKind, template.target, level, b)
                        PhraseSlots.instantiate(template, value = slot.prompt.toLong())
                    }
                    assertEquals(expected, sampled, "${template.id} L$level")
                }
            }
        }
    }

    @Test
    fun levelClampsToValidBounds() {
        val clock = templates(TrainerKind.Clock).first()
        assertEquals(0, minuteOf(PhraseSlots.sample(clock, level = -3, Random(6)).prompt))
        val number = templates(TrainerKind.Numbers).first()
        val digits = PhraseSlots.sample(number, level = 99, Random(7)).prompt.filter { it.isDigit() }
        assertEquals(10, digits.length)
    }
}
