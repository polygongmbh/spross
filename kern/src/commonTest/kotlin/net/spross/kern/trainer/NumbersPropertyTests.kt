package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumbersPropertyTests {

    @Test
    fun sampledTasksAreWellFormed() {
        val rng = Random(0xBEEF)
        for (kind in TrainerKind.entries) {
            for (language in Numbers.languages) {
                repeat(200) {
                    val task = Numbers.sample(kind, language, rng)
                    assertTrue(task.prompt.isNotEmpty())
                    assertTrue(task.accepted.isNotEmpty())
                    assertTrue(task.accepted.all { it.isNotEmpty() })
                    assertTrue(
                        task.display in task.accepted,
                        "$kind/$language: display must be accepted (${task.display})",
                    )
                    assertTrue(task.kind == kind && task.language == language)
                }
            }
        }
    }

    @Test
    fun directGeneratorsAreWellFormedAcrossRanges() {
        for (language in Numbers.languages) {
            for (n in 0L..1200L) {
                val task = Numbers.number(n, language)
                assertTrue(task.display in task.accepted, "n=$n $language")
                assertEquals(task.accepted.size, task.accepted.toSet().size, "n=$n $language")
            }
            for (y in 1000L..2200L step 7) {
                val task = Numbers.year(y, language)
                assertTrue(task.display in task.accepted, "y=$y $language")
            }
            for (h in 0..23) {
                for (m in 0 until 60 step 5) {
                    val task = Numbers.clock(h, m, language)
                    assertTrue(task.display in task.accepted, "$h:$m $language")
                }
            }
        }
    }

    @Test
    fun samplingIsDeterministicForSeededGenerator() {
        for (kind in TrainerKind.entries) {
            val a = Random(0xD00D)
            val b = Random(0xD00D)
            repeat(100) {
                val ta = Numbers.sample(kind, "de", a)
                val tb = Numbers.sample(kind, "de", b)
                assertEquals(ta, tb)
            }
        }
    }

    @Test
    fun sampledValuesStayInPortedRanges() {
        val rng = Random(42)
        repeat(500) {
            val n = Numbers.sample(TrainerKind.Numbers, "de", rng).prompt.toLong()
            assertTrue(n in 10..9999)
            val y = Numbers.sample(TrainerKind.Years, "de", rng).prompt.toLong()
            assertTrue(y in 1000..2200)
            val clockTask = Numbers.sample(TrainerKind.Clock, "de", rng)
            assertTrue(clockTask.prompt.length == 5 && ':' in clockTask.prompt)
        }
    }

    @Test
    fun clockMinutesArePassedThroughExactly() {
        assertEquals("09:58", Numbers.clock(9, 58, "de").prompt)
        assertEquals("23:58", Numbers.clock(23, 58, "de").prompt)
        assertEquals("09:03", Numbers.clock(9, 3, "de").prompt)
        assertEquals("09:00", Numbers.clock(9, 0, "de").prompt)
        assertEquals("09:12", Numbers.clock(9, 12, "de").prompt)
    }

    @Test
    fun nonRoundMinutesReadOutInEveryLanguage() {
        // 08:17 — a minute the old 5-step rounding never produced.
        assertEquals(
            "Saa mbili na dakika kumi na saba asubuhi",
            Numbers.clock(8, 17, "sw").display,
        )
        for (language in Numbers.languages) {
            val task = Numbers.clock(8, 17, language)
            assertEquals("08:17", task.prompt)
            assertTrue(task.display in task.accepted)
        }
    }
}
