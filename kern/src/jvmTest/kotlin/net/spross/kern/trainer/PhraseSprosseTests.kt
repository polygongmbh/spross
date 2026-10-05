package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sprosse-aware phrase-slot sampling: sentence drills ramp with exactly the same
 * Sprosse semantics as the plain drills — a frame never constrains the value.
 */
class PhraseSprosseTests {

    private val clockTime = Regex("""(\d{1,2}):(\d{2})""")

    /** Slot minute from the source-sentence digital time ("… um 14:35 Uhr …"). */
    private fun minuteOf(sentence: String): Int =
        clockTime.find(sentence)!!.groupValues[2].toInt()

    private fun templates(kind: NumbersReading): List<PhraseTemplate> =
        RealFrames.all.filter { it.slotKind == kind }

    // Gentle start: Sprosse 1 per kind

    @Test
    fun sprosseOneClockOnlyYieldsFullHours() {
        val rng = Random(1)
        for (template in templates(NumbersReading.Clock)) {
            repeat(40) {
                val task = PhraseSlots.sample(template, sprosse = 1, rng)
                assertEquals(0, minuteOf(task.prompt), "${template.id}: ${task.prompt}")
            }
        }
    }

    @Test
    fun sprosseOneNumberYieldsSingleDigitValues() {
        val rng = Random(2)
        for (template in templates(NumbersReading.Cardinal)) {
            repeat(40) {
                val task = PhraseSlots.sample(template, sprosse = 1, rng)
                val digits = task.prompt.filter { it.isDigit() }
                assertEquals(1, digits.length, "${template.id}: ${task.prompt}")
            }
        }
    }

    @Test
    fun sprosseOneYearStaysInRecentDecades() {
        val rng = Random(3)
        for (template in templates(NumbersReading.Year)) {
            repeat(40) {
                val task = PhraseSlots.sample(template, sprosse = 1, rng)
                val year = task.prompt.filter { it.isDigit() }.toInt()
                assertTrue(year in 1990..2029, "${template.id}: $year")
            }
        }
    }

    // No frame constrains the minute set (the Swahili ≤ 30 rule is deleted)

    @Test
    fun quarterSprosseYieldsAllFourQuarters() {
        val rng = Random(4)
        for (template in templates(NumbersReading.Clock)) {
            val seen = mutableSetOf<Int>()
            repeat(120) {
                seen += minuteOf(PhraseSlots.sample(template, sprosse = 2, rng).prompt)
            }
            assertEquals(setOf(0, 15, 30, 45), seen, template.id)
        }
    }

    @Test
    fun countdownSprosseAddsTheLateFivesAndNothingOffGrid() {
        val rng = Random(8)
        for (template in templates(NumbersReading.Clock)) {
            val seen = mutableSetOf<Int>()
            repeat(200) {
                seen += minuteOf(PhraseSlots.sample(template, sprosse = 4, rng).prompt)
            }
            assertEquals((0..55 step 5).toSet(), seen, template.id)
        }
    }

    @Test
    fun maxSprosseReachesMinutesPastHalfPast() {
        val rng = Random(5)
        val top = Numbers.maxSprosse(NumbersReading.Clock)
        for (template in templates(NumbersReading.Clock)) {
            var sawPastHalf = false
            repeat(120) {
                val minute = minuteOf(PhraseSlots.sample(template, sprosse = top, rng).prompt)
                assertTrue(minute in 0..59, "${template.id}: $minute")
                if (minute > 30) sawPastHalf = true
            }
            assertTrue(sawPastHalf, "${template.id}: expected countdown-form minutes at Sprosse $top")
        }
    }

    // Determinism: seeded RNG reproduces, and sampling at a Sprosse == instantiate

    @Test
    fun sprosseSamplingIsDeterministicAndMatchesInstantiate() {
        for (template in RealFrames.all) {
            for (sprosse in 1..Numbers.maxSprosse(template.slotKind)) {
                val a = Random(0xBEEF + sprosse)
                val b = Random(0xBEEF + sprosse)
                repeat(30) {
                    val sampled = PhraseSlots.sample(template, sprosse, a)
                    // Cross-check against the shared numbers-drill draw machinery.
                    val expected = when (template.slotKind) {
                        NumbersReading.Clock -> {
                            val hour = b.nextInt(24)
                            PhraseSlots.instantiate(template, hour, drawClockMinute(sprosse, b))
                        }
                        NumbersReading.Fraction -> {
                            val slot = Numbers.sample(template.slotKind, template.target, sprosse, b)
                            val parts = slot.prompt.split("/").map { it.toLong() }
                            PhraseSlots.instantiate(template, parts[0], parts[1])
                        }
                        NumbersReading.Phone -> PhraseSlots.instantiate(
                            template, Numbers.sample(template.slotKind, template.target, sprosse, b).prompt,
                        )
                        else -> {
                            val slot = Numbers.sample(template.slotKind, template.target, sprosse, b)
                            PhraseSlots.instantiate(template, value = slot.prompt.toLong())
                        }
                    }
                    assertEquals(expected, sampled, "${template.id} L$sprosse")
                }
            }
        }
    }

    @Test
    fun sprosseClampsToValidBounds() {
        val clock = templates(NumbersReading.Clock).first()
        assertEquals(0, minuteOf(PhraseSlots.sample(clock, sprosse = -3, Random(6)).prompt))
        val number = templates(NumbersReading.Cardinal).first()
        val digits = PhraseSlots.sample(number, sprosse = 99, Random(7)).prompt.filter { it.isDigit() }
        assertEquals(10, digits.length)
    }
}
