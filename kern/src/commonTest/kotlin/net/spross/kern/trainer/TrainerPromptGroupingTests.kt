package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `prompt` is the machine contract and `promptDisplay` is what a learner reads.
 * What is asserted here is the seam between them: the grouped form must always
 * be the plain one plus separators, never a different number.
 */
class TrainerPromptGroupingTests {

    private val separator = '\u202F'

    @Test
    fun groupingStartsAtFiveDigits() {
        for (plain in listOf("0", "7", "42", "347", "1978", "9999")) {
            assertEquals(plain, groupDigits(plain), plain)
        }
        assertEquals("10${separator}000", groupDigits("10000"))
        assertEquals("12${separator}345", groupDigits("12345"))
        assertEquals("100${separator}000", groupDigits("100000"))
        assertEquals("1${separator}000${separator}000", groupDigits("1000000"))
        assertEquals("4${separator}072${separator}918${separator}300", groupDigits("4072918300"))
    }

    @Test
    fun theSeparatorIsTheNarrowNoBreakSpace() {
        assertEquals(separator, GROUP_SEPARATOR)
        // Neither convention's thousands mark, and not an ordinary space either.
        val grouped = Trainer.number(12345, "de").promptDisplay
        assertEquals("12${separator}345", grouped)
        assertTrue(grouped.none { it == '.' || it == ',' || it == ' ' }, grouped)
    }

    @Test
    fun sampledNumberPromptsStayParseableAndDisplayOnlyAddsSeparators() {
        val rng = Random(20260806)
        for (language in Trainer.languages) {
            for (level in 1..Trainer.maxLevel(TrainerKind.Numbers)) {
                repeat(20) {
                    val task = Trainer.sample(TrainerKind.Numbers, language, level, rng)
                    val where = "$language level=$level ${task.prompt}"
                    assertEquals(task.prompt, task.promptDisplay.filter { it.isDigit() }, where)
                    assertEquals(task.prompt.toLong().toString(), task.prompt, where)
                    assertEquals(task.prompt.length, level, where)
                    val expectedSeparators = if (level < 5) 0 else (level - 1) / 3
                    assertEquals(expectedSeparators, task.promptDisplay.count { it == separator }, where)
                }
            }
        }
    }

    @Test
    fun yearsAndClockAreNeverGrouped() {
        for (language in Trainer.languages) {
            for (y in listOf(1100L, 1978L, 2026L)) {
                val task = Trainer.year(y, language)
                assertEquals(task.prompt, task.promptDisplay, "$language y=$y")
            }
            for ((h, m) in listOf(0 to 0, 8 to 5, 14 to 35, 23 to 59)) {
                val task = Trainer.clock(h, m, language)
                assertEquals(task.prompt, task.promptDisplay, "$language $h:$m")
            }
        }
    }
}
