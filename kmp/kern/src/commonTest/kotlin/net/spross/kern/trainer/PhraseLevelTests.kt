package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level-aware phrase-slot sampling: sentence drills ramp with the same level
 * semantics as the plain drills, intersected with template constraints.
 */
class PhraseLevelTests {

    private val clockTime = Regex("""(\d{1,2}):(\d{2})""")

    /** Slot minute from the source-sentence digital time ("… um 14:35 Uhr …"). */
    private fun minuteOf(sentence: String): Int =
        clockTime.find(sentence)!!.groupValues[2].toInt()

    private fun templates(kind: TrainerKind): List<PhraseTemplate> =
        PhraseTemplates.all.filter { it.slotKind == kind }

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

    // Template constraint ∩ level set (Swahili clock embeds only minutes 0..30)

    @Test
    fun quarterLevelIntersectsSwahiliConstraint() {
        val rng = Random(4)
        for (template in templates(TrainerKind.Clock)) {
            val allowed = if (template.target == "sw") setOf(0, 15, 30) else setOf(0, 15, 30, 45)
            val seen = mutableSetOf<Int>()
            repeat(120) {
                seen += minuteOf(PhraseSlots.sample(template, level = 2, rng).prompt)
            }
            assertEquals(allowed, seen, template.id)
        }
    }

    @Test
    fun maxLevelKeepsSwahiliMinutesUnderConstraint() {
        val rng = Random(5)
        for (template in templates(TrainerKind.Clock).filter { it.target == "sw" }) {
            var sawOverQuarter = false
            repeat(120) {
                val minute = minuteOf(PhraseSlots.sample(template, level = 4, rng).prompt)
                assertTrue(minute <= 30, "${template.id}: $minute")
                if (minute > 15) sawOverQuarter = true
            }
            assertTrue(sawOverQuarter, "${template.id}: expected non-trivial minutes at level 4")
        }
    }

    // Determinism: seeded RNG reproduces, and leveled sampling == instantiate

    @Test
    fun leveledSamplingIsDeterministicAndMatchesInstantiate() {
        for (template in PhraseTemplates.all) {
            for (level in 1..Trainer.maxLevel(template.slotKind)) {
                val a = Random(0xBEEF + level)
                val b = Random(0xBEEF + level)
                repeat(30) {
                    val sampled = PhraseSlots.sample(template, level, a)
                    // Independent re-statement of the leveled draw spec.
                    val expected = if (template.slotKind == TrainerKind.Clock) {
                        val hour = b.nextInt(24)
                        val cap = if (template.target == "sw") 30 else 59
                        val minute = when (level) {
                            1 -> 0
                            2 -> intArrayOf(0, 15, 30, 45).filter { it <= cap }
                                .let { it[b.nextInt(it.size)] }
                            3 -> b.nextInt(31)
                            else -> b.nextInt(cap + 1)
                        }
                        PhraseSlots.instantiate(template, hour = hour, minute = minute)
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

    // Reverse drills ramp identically

    @Test
    fun leveledReverseMatchesLeveledForward() {
        for (template in PhraseTemplates.all) {
            for (level in 1..Trainer.maxLevel(template.slotKind)) {
                val a = Random(11L * level)
                val b = Random(11L * level)
                val reversed = PhraseSlots.reverseSample(template, level, a)
                val forward = PhraseSlots.sample(template, level, b)
                assertEquals(forward.display, reversed.prompt, "${template.id} L$level")
                assertEquals(template.source, reversed.language, template.id)
            }
        }
    }

    @Test
    fun levelOneReverseClockAnswersFullHours() {
        val rng = Random(8)
        for (template in templates(TrainerKind.Clock)) {
            repeat(40) {
                val task = PhraseSlots.reverseSample(template, level = 1, rng)
                assertEquals(0, minuteOf(task.display), "${template.id}: ${task.display}")
            }
        }
    }
}
